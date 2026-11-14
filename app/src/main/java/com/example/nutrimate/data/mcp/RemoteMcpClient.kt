package com.example.nutrimate.data.mcp

import android.util.Log
import com.google.firebase.ai.type.Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val TAG = "RemoteMcpClient"

/**
 * Streamable HTTP JSON-RPC 2.0 MCP client ready for future remote MCP servers.
 * Discovers remote tools via `tools/list`, bridges JSON Schema into Firebase AI `Schema`,
 * and executes tool invocations via `tools/call`.
 */
class RemoteMcpClient(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    suspend fun initialize(config: McpRemoteServerConfig): Boolean {
        val params = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {})
            put(
                "clientInfo",
                buildJsonObject {
                    put("name", "NutriMateAndroid")
                    put("version", "1.0.0")
                }
            )
        }
        return try {
            val response = postJsonRpc(config, "initialize", params)
            response["error"] == null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MCP server ${config.serverName}", e)
            false
        }
    }

    suspend fun listTools(config: McpRemoteServerConfig): List<McpTool> {
        val response = postJsonRpc(config, "tools/list", buildJsonObject {})
        val descriptors = parseToolsListResponse(response)
        return descriptors.map { descriptor ->
            RemoteMcpToolAdapter(
                config = config,
                descriptor = descriptor,
                client = this
            )
        }
    }

    suspend fun callTool(
        config: McpRemoteServerConfig,
        toolName: String,
        args: Map<String, JsonElement>
    ): McpToolResult {
        return try {
            val params = buildJsonObject {
                put("name", toolName)
                put("arguments", JsonObject(args))
            }
            val response = postJsonRpc(config, "tools/call", params)
            parseToolCallResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Remote MCP tool call failed ($toolName on ${config.serverName})", e)
            val errObj = buildJsonObject {
                put("error", e.localizedMessage ?: "Remote MCP call failed")
            }
            McpToolResult(
                payload = errObj,
                displaySummary = "Remote MCP error: ${e.localizedMessage ?: "connection failed"}",
                isError = true
            )
        }
    }

    private suspend fun postJsonRpc(
        config: McpRemoteServerConfig,
        method: String,
        params: JsonObject
    ): JsonObject = withContext(Dispatchers.IO) {
        val reqBody = buildJsonRpcRequest(
            id = UUID.randomUUID().toString(),
            method = method,
            params = params
        ).toString()

        val conn = (URL(config.endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.timeoutMs
            readTimeout = config.timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/event-stream")
            if (!config.bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${config.bearerToken}")
            }
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(reqBody)
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val rawText = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
            }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("MCP HTTP $code: ${rawText.take(300)}")
            }

            // Support direct JSON or SSE data: payload framing
            val jsonPayload = if (rawText.trimStart().startsWith("data:")) {
                rawText.lineSequence()
                    .filter { it.startsWith("data:") }
                    .map { it.removePrefix("data:").trim() }
                    .lastOrNull { it.startsWith("{") }
                    ?: "{}"
            } else {
                rawText
            }
            json.parseToJsonElement(jsonPayload).jsonObject
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun buildJsonRpcRequest(
            id: String,
            method: String,
            params: JsonObject
        ): JsonObject = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }

        fun parseToolsListResponse(responseJson: JsonObject): List<RemoteMcpToolDescriptor> {
            val resultObj = responseJson["result"] as? JsonObject ?: return emptyList()
            val toolsArray = resultObj["tools"] as? JsonArray ?: return emptyList()
            return toolsArray.mapNotNull { elem ->
                val obj = elem as? JsonObject ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val inputSchema = (obj["inputSchema"] as? JsonObject) ?: buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {})
                }
                RemoteMcpToolDescriptor(
                    name = name,
                    description = description,
                    inputSchema = inputSchema
                )
            }
        }

        fun parseToolCallResponse(responseJson: JsonObject): McpToolResult {
            val rpcError = responseJson["error"] as? JsonObject
            if (rpcError != null) {
                val msg = rpcError["message"]?.jsonPrimitive?.contentOrNull ?: "MCP error"
                return McpToolResult(
                    payload = buildJsonObject { put("error", msg) },
                    displaySummary = msg,
                    isError = true
                )
            }

            val resultObj = responseJson["result"] as? JsonObject ?: buildJsonObject {}
            val isToolError = resultObj["isError"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            val structured = resultObj["structuredContent"] as? JsonObject
            if (structured != null) {
                return McpToolResult(
                    payload = structured,
                    displaySummary = structured.toString().take(120),
                    isError = isToolError
                )
            }

            val contentArr = resultObj["content"] as? JsonArray
            val textContent = contentArr?.mapNotNull { item ->
                (item as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }?.joinToString("\n").orEmpty()

            val payload = buildJsonObject {
                put("text", textContent)
                if (isToolError) put("isError", true)
            }
            return McpToolResult(
                payload = payload,
                displaySummary = textContent.ifBlank { "Completed remote tool call" }.take(120),
                isError = isToolError
            )
        }

        /**
         * Bridges an MCP JSON Schema definition into a Firebase AI `Schema` graph.
         */
        fun jsonSchemaToFirebaseSchema(schemaObj: JsonObject): Schema {
            val type = schemaObj["type"]?.jsonPrimitive?.contentOrNull ?: "string"
            val desc = schemaObj["description"]?.jsonPrimitive?.contentOrNull
            val enumValues = (schemaObj["enum"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

            if (!enumValues.isNullOrEmpty()) {
                return Schema.enumeration(values = enumValues, description = desc)
            }

            return when (type.lowercase()) {
                "integer" -> Schema.integer(description = desc)
                "number" -> Schema.double(description = desc)
                "boolean" -> Schema.boolean(description = desc)
                "array" -> {
                    val itemsObj = (schemaObj["items"] as? JsonObject) ?: buildJsonObject { put("type", "string") }
                    Schema.array(items = jsonSchemaToFirebaseSchema(itemsObj), description = desc)
                }
                "object" -> {
                    val propsObj = schemaObj["properties"] as? JsonObject
                    val props = propsObj?.entries?.associate { (k, v) ->
                        k to jsonSchemaToFirebaseSchema(v as? JsonObject ?: buildJsonObject { put("type", "string") })
                    } ?: emptyMap()
                    val required = (schemaObj["required"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        ?.toSet()
                        ?: emptySet()
                    val optional = props.keys.filterNot { it in required }
                    Schema.obj(
                        properties = props,
                        optionalProperties = optional,
                        description = desc
                    )
                }
                else -> Schema.string(description = desc)
            }
        }

        fun extractParametersFromInputSchema(inputSchema: JsonObject): Pair<Map<String, Schema>, List<String>> {
            val propsObj = inputSchema["properties"] as? JsonObject ?: return emptyMap<String, Schema>() to emptyList()
            val params = propsObj.entries.associate { (key, value) ->
                val childObj = value as? JsonObject ?: buildJsonObject { put("type", "string") }
                key to jsonSchemaToFirebaseSchema(childObj)
            }
            val required = (inputSchema["required"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.toSet()
                ?: emptySet()
            val optional = params.keys.filterNot { it in required }
            return params to optional
        }
    }
}

private class RemoteMcpToolAdapter(
    private val config: McpRemoteServerConfig,
    descriptor: RemoteMcpToolDescriptor,
    private val client: RemoteMcpClient
) : McpTool {
    override val name: String = descriptor.name
    override val description: String = descriptor.description.ifBlank { "Remote MCP tool (${config.serverName})" }
    private val extracted = RemoteMcpClient.extractParametersFromInputSchema(descriptor.inputSchema)
    override val parameters: Map<String, Schema> = extracted.first
    override val optionalParameters: List<String> = extracted.second
    override val source: McpToolSource = McpToolSource.REMOTE_MCP

    override suspend fun execute(args: Map<String, JsonElement>): McpToolResult {
        return client.callTool(config, name, args)
    }
}
