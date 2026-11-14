package com.example.nutrimate.data.mcp

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class McpToolSource(val badgeLabel: String) {
    LOCAL("local"),
    REMOTE_MCP("mcp-http")
}

data class McpToolResult(
    val payload: JsonObject,
    val displaySummary: String,
    val isError: Boolean = false
)

/**
 * Unified MCP-compatible tool contract shared by local Android/Firestore domain tools
 * and dynamic remote HTTP MCP server tools.
 */
interface McpTool {
    val name: String
    val description: String
    val parameters: Map<String, Schema>
    val optionalParameters: List<String>
    val source: McpToolSource

    suspend fun execute(args: Map<String, JsonElement>): McpToolResult

    fun toFunctionDeclaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = parameters,
        optionalParameters = optionalParameters
    )
}

data class McpRemoteServerConfig(
    val serverName: String,
    val endpointUrl: String,
    val bearerToken: String? = null,
    val timeoutMs: Int = 15_000
)

data class RemoteMcpToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

data class ToolExecutionRecord(
    val toolName: String,
    val argsSummary: String,
    val resultSummary: String,
    val responsePart: FunctionResponsePart,
    val latencyMs: Long,
    val source: McpToolSource,
    val isError: Boolean = false
)

data class PendingChatSwapProposal(
    val token: String,
    val slotId: String,
    val slotName: String,
    val itemName: String,
    val placeName: String,
    val priceTk: Int,
    val kcal: Int,
    val fundingType: String, // "ALLOWANCE" | "RESERVE" | "FEAST_SACRIFICE" | "MANUAL_LOG"
    val shortfallTk: Double,
    val feastTitle: String?,
    val remainderTk: Double,
    val summary: String
)
