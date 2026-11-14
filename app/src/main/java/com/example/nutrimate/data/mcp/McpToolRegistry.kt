package com.example.nutrimate.data.mcp

import android.util.Log
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Tool
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "McpToolRegistry"

/**
 * Central registry uniting local NutriMate tools and dynamic remote HTTP MCP tools
 * behind a single Gemini `Tool` declaration and execution surface.
 */
class McpToolRegistry(
    private val remoteMcpClient: RemoteMcpClient = RemoteMcpClient()
) {
    private val tools = linkedMapOf<String, McpTool>()

    @Synchronized
    fun registerTool(tool: McpTool) {
        tools[tool.name] = tool
    }

    @Synchronized
    fun registerTools(newTools: List<McpTool>) {
        newTools.forEach { tools[it.name] = it }
    }

    @Synchronized
    fun unregisterRemoteTools() {
        val iterator = tools.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.source == McpToolSource.REMOTE_MCP) {
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun getRegisteredToolNames(): List<String> = tools.keys.toList()

    @Synchronized
    fun getTool(name: String): McpTool? = tools[name]

    @Synchronized
    fun getFunctionDeclarations(): List<FunctionDeclaration> =
        tools.values.map { it.toFunctionDeclaration() }

    fun getGeminiTool(): Tool = Tool.functionDeclarations(getFunctionDeclarations())

    suspend fun attachRemoteServer(config: McpRemoteServerConfig): Int {
        val ok = remoteMcpClient.initialize(config)
        if (!ok) {
            Log.w(TAG, "Remote MCP server ${config.serverName} failed initialize handshake")
            return 0
        }
        val discovered = remoteMcpClient.listTools(config)
        registerTools(discovered)
        Log.d(TAG, "Attached ${discovered.size} tools from remote MCP server ${config.serverName}")
        return discovered.size
    }

    suspend fun executeCall(call: FunctionCallPart): ToolExecutionRecord {
        val startMs = System.currentTimeMillis()
        val tool = getTool(call.name)
        val argsSummary = call.args.entries.joinToString(", ") { (k, v) ->
            val clean = if (v is JsonPrimitive && v.isString) "\"${v.content}\"" else v.toString()
            "$k=$clean"
        }

        val result = if (tool == null) {
            McpToolResult(
                payload = buildJsonObject {
                    put("error", "Unknown tool '${call.name}'")
                },
                displaySummary = "Unknown tool '${call.name}'",
                isError = true
            )
        } else {
            try {
                tool.execute(call.args)
            } catch (e: Exception) {
                Log.e(TAG, "Tool execution error in ${call.name}", e)
                McpToolResult(
                    payload = buildJsonObject {
                        put("error", e.localizedMessage ?: "Tool execution failed")
                    },
                    displaySummary = "Error: ${e.localizedMessage ?: "execution failed"}",
                    isError = true
                )
            }
        }

        val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val callId = call.id
        val responsePart = if (!callId.isNullOrBlank()) {
            FunctionResponsePart(
                name = call.name,
                response = result.payload,
                id = callId
            )
        } else {
            FunctionResponsePart(
                name = call.name,
                response = result.payload
            )
        }

        return ToolExecutionRecord(
            toolName = call.name,
            argsSummary = argsSummary,
            resultSummary = result.displaySummary,
            responsePart = responsePart,
            latencyMs = latencyMs,
            source = tool?.source ?: McpToolSource.LOCAL,
            isError = result.isError
        )
    }
}
