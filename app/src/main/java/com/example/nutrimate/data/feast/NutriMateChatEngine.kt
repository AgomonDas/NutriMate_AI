package com.example.nutrimate.data.feast

import android.util.Log
import com.example.nutrimate.BuildConfig
import com.example.nutrimate.data.location.CampusArea
import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.mcp.McpRemoteServerConfig
import com.example.nutrimate.data.mcp.McpToolRegistry
import com.example.nutrimate.data.mcp.McpToolSource
import com.example.nutrimate.data.mcp.NutriMateTurnToolContext
import com.example.nutrimate.data.mcp.buildNutriMateLocalMcpTools
import com.example.nutrimate.data.model.RestaurantWithDistance
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.ChatMessage
import com.example.nutrimate.model.MealSlot
import com.example.nutrimate.model.MessageSender
import com.example.nutrimate.model.NutriMateUserContext
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "NutriMateChatEngine"
private const val AI_LOG_TAG = "NUTRIMATE_AI"
private const val MAX_HISTORY_TURNS = 10
private const val STREAM_TIMEOUT_MS = 45_000L
private const val MAX_TOOL_HOPS = 5

/**
 * Agentic Tool-Calling AI Engine powered by Gemini 3.5 Flash-Lite (`gemini-3.5-flash-lite`)
 * via Firebase AI Logic and an MCP-compatible `McpToolRegistry`.
 *
 * - Gemini autonomously decides when to answer directly vs invoke typed domain tools.
 * - All local tools (`get_user_context`, `search_campus_menu`, `get_nearby_restaurants`,
 *   `check_meal_budget`, `find_funding_options`, `propose_meal_swap`, `log_manual_meal`)
 *   share the same `McpTool` contract as future remote HTTP MCP server tools.
 * - Strict Human-in-the-Loop Gate: swaps and logs are staged as proposals requiring explicit
 *   user confirmation in chat before modifying meal slots or budget reserves.
 */
class NutriMateChatEngine(
    val toolsGatekeeper: NutriMateAiToolsGatekeeper
) {
    companion object {
        const val MODEL_NAME = "gemini-3.5-flash-lite"
    }

    private val totalRequests = AtomicInteger(0)
    private val errorCount = AtomicInteger(0)
    private val timeoutCount = AtomicInteger(0)

    @Volatile
    private var activeTurnContext: NutriMateTurnToolContext? = null

    val toolRegistry = McpToolRegistry().apply {
        val localTools = buildNutriMateLocalMcpTools(
            toolsGatekeeper = toolsGatekeeper,
            turnContextProvider = { activeTurnContext }
        )
        registerTools(localTools)
    }

    /** Last cloud failure, so UI/logs can surface exact failure diagnostics. */
    @Volatile
    var lastCloudError: String? = null
        private set

    /**
     * Programmatic hook to attach a remote HTTP MCP server's tools into the active Gemini agent loop.
     */
    suspend fun attachRemoteMcpServer(config: McpRemoteServerConfig): Int {
        val count = toolRegistry.attachRemoteServer(config)
        if (count > 0) {
            // Rebind generative model lazily so newly discovered remote MCP tools are included
            synchronized(this) {
                cachedModel = null
                chatSession = null
            }
        }
        return count
    }

    fun describeError(e: Throwable): String = when {
        e is TimeoutCancellationException ->
            "Request timed out after ${STREAM_TIMEOUT_MS / 1000}s waiting for Gemini."
        e.message?.contains("App Check", ignoreCase = true) == true ||
            e.message?.contains("403") == true ->
            "App Check / permission rejected (ensure Firebase AI Logic is enabled and debug token is registered)."
        e.message?.contains("not found", ignoreCase = true) == true ||
            e.message?.contains("404") == true ->
            "Configured model \"$MODEL_NAME\" was not found for this Firebase project/backend."
        e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
            e.message?.contains("network", ignoreCase = true) == true ->
            "Network unavailable. Check your internet connection and tap Retry."
        else -> e.localizedMessage ?: e.javaClass.simpleName
    }

    @Volatile
    private var cachedModel: GenerativeModel? = null

    private fun getOrCreateModel(): GenerativeModel {
        cachedModel?.let { return it }
        synchronized(this) {
            cachedModel?.let { return it }
            val initStart = System.currentTimeMillis()
            val model = Firebase.ai.generativeModel(
                modelName = MODEL_NAME,
                generationConfig = generationConfig {
                    temperature = 0.35f
                    topP = 0.9f
                    maxOutputTokens = 600
                },
                tools = listOf(toolRegistry.getGeminiTool()),
                systemInstruction = content {
                    text(
                        """
                        You are NutriMate AI, a smart, warm, and practical personalized nutrition & student food-budget copilot in Bangladesh.

                        TOOL USE & REASONING RULES:
                        1. You have live MCP-compatible tools. Never guess menu prices, restaurant distances, or user budget balances—call `search_campus_menu`, `get_nearby_restaurants`, `get_user_context`, `check_meal_budget`, and `find_funding_options` whenever the user asks about campus dishes, nearby deals, budget status, or meal changes.
                        2. Simple nutrition/calorie knowledge questions (e.g., "how much protein in 2 eggs?") can be answered directly without tools using the USER_CONTEXT provided in the turn.
                        3. Meal Swap Workflow (MANDATORY):
                           - First call `search_campus_menu` to find the real `item_id`, price in ৳, and kcal.
                           - Then call `check_meal_budget(slot, item_id)` to verify slot allowance.
                           - If `ok=false` (`over_budget`), call `find_funding_options(slot, item_id)` to check unallocated reserve or feast sacrifice.
                           - Whenever the student wants to swap/change/choose a meal—or asks you to apply an option—call `propose_meal_swap(slot, item_id, token)`.
                           - NEVER claim a meal has already been swapped or saved! Always tell the student clearly what will change (dish, price ৳, kcal, and whether it fits allowance, draws from unallocated reserve, or cancels a feast) and ask them to tap Confirm below.
                        4. Off-catalog manual meals: If the user asks to log a custom/off-menu meal they ate, call `log_manual_meal(...)` and ask them to tap Confirm below.
                        5. Persona & Clarity: Think in Bangladeshi Taka (৳). Keep responses concise, structured, and grounded strictly in tool outputs and USER_CONTEXT. Never diagnose medical conditions or recommend extreme deficits.
                        """.trimIndent()
                    )
                }
            )
            Log.d(
                AI_LOG_TAG,
                "Model $MODEL_NAME initialized lazily in ${System.currentTimeMillis() - initStart}ms with ${toolRegistry.getRegisteredToolNames().size} MCP tools"
            )
            cachedModel = model
            return model
        }
    }

    private var chatSession: Chat? = null
    private var hydratedHistory: List<Content> = emptyList()

    /**
     * Hydrates multi-turn memory from capped recent persisted user/assistant turns.
     */
    @Synchronized
    fun hydrateHistory(recentMessages: List<ChatMessage>) {
        val turns = recentMessages
            .filter { !it.isError && !it.isStreaming && it.content.isNotBlank() }
            .filter { it.sender == MessageSender.USER || it.sender == MessageSender.ASSISTANT }
            .takeLast(MAX_HISTORY_TURNS)

        val contentList = mutableListOf<Content>()
        for (msg in turns) {
            val role = if (msg.sender == MessageSender.USER) "user" else "model"
            if (contentList.isEmpty() && role == "model") continue
            contentList.add(content(role) { text(msg.content.take(1500)) })
        }
        hydratedHistory = contentList
        chatSession = null
        Log.d(AI_LOG_TAG, "Hydrated multi-turn memory with ${contentList.size} persisted turns")
    }

    /**
     * Resets or clears the active chat session (e.g. on New Chat or account switch).
     */
    @Synchronized
    fun resetChatSession() {
        chatSession = null
        hydratedHistory = emptyList()
        Log.d(AI_LOG_TAG, "Chat session reset")
    }

    @Synchronized
    private fun getOrCreateChatSession(): Chat {
        chatSession?.let { return it }
        val model = getOrCreateModel()
        val session = if (hydratedHistory.isNotEmpty()) {
            model.startChat(history = hydratedHistory)
        } else {
            model.startChat()
        }
        chatSession = session
        return session
    }

    /**
     * Agentic streaming execution pipeline:
     * Streams Gemini 3.5 Flash-Lite, executes requested MCP tools in a bounded loop,
     * emits live collapsible tool step cards, and stages confirmable meal swap proposals.
     */
    suspend fun processUserMessage(
        userText: String,
        currentSlots: List<MealSlot>,
        userContext: NutriMateUserContext,
        nearbyRestaurants: List<RestaurantWithDistance> = emptyList(),
        campusArea: CampusArea = CampusLocationManager.getDefaultArea(),
        contextBuildMs: Long = 0L,
        userId: String? = null,
        repository: NutriMateFirestoreRepository? = null,
        onStepGenerated: (ChatMessage) -> Unit
    ): ChatMessage {
        val totalStart = System.currentTimeMillis()
        val requestNum = totalRequests.incrementAndGet()
        val turnContext = NutriMateTurnToolContext(
            userContext = userContext,
            currentSlots = currentSlots,
            nearbyRestaurants = nearbyRestaurants,
            campusArea = campusArea
        )
        activeTurnContext = turnContext

        val assistantMsgId = UUID.randomUUID().toString()
        val textAccumulator = StringBuilder()
        var firstTokenMs: Long? = null
        var hasEmittedFirstChunk = false

        val contextualPrompt = """
            USER_CONTEXT: ${userContext.toCompactJson()}
            USER_MESSAGE: $userText
        """.trimIndent()

        val tRequestInitStart = System.currentTimeMillis()
        Log.d(
            AI_LOG_TAG,
            "Req #$requestNum start | model=$MODEL_NAME | tools=${toolRegistry.getRegisteredToolNames().size} | context=${contextBuildMs}ms"
        )

        var streamFailure: Throwable? = null
        try {
            withTimeout(STREAM_TIMEOUT_MS) {
                val session = getOrCreateChatSession()
                var nextInput: Content = content("user") { text(contextualPrompt) }
                var hop = 0

                while (hop < MAX_TOOL_HOPS) {
                    hop++
                    val pendingCalls = mutableListOf<FunctionCallPart>()

                    session.sendMessageStream(nextInput).collect { chunk ->
                        if (chunk.functionCalls.isNotEmpty()) {
                            pendingCalls.addAll(chunk.functionCalls)
                        }
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            textAccumulator.append(chunkText)
                            val elapsed = System.currentTimeMillis() - tRequestInitStart
                            if (!hasEmittedFirstChunk) {
                                hasEmittedFirstChunk = true
                                firstTokenMs = elapsed
                                Log.d(AI_LOG_TAG, "Req #$requestNum TTFT=${elapsed}ms (hop=$hop)")
                            }
                            onStepGenerated(
                                ChatMessage(
                                    id = assistantMsgId,
                                    sender = MessageSender.ASSISTANT,
                                    content = textAccumulator.toString(),
                                    latencyMs = elapsed,
                                    firstTokenMs = firstTokenMs,
                                    isLocalExecution = false,
                                    isStreaming = true
                                )
                            )
                        }
                    }

                    if (pendingCalls.isEmpty()) {
                        break
                    }

                    val responseParts = mutableListOf<com.google.firebase.ai.type.FunctionResponsePart>()
                    for (call in pendingCalls) {
                        val exec = toolRegistry.executeCall(call)
                        responseParts.add(exec.responsePart)

                        val badgeMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = MessageSender.FUNCTION_CALL,
                            content = "${exec.toolName}(${exec.argsSummary})",
                            toolName = exec.toolName,
                            toolArgs = exec.argsSummary.ifBlank { "none" },
                            toolResult = exec.resultSummary,
                            toolSource = exec.source.badgeLabel,
                            latencyMs = exec.latencyMs,
                            isLocalExecution = exec.source == McpToolSource.LOCAL,
                            isError = exec.isError
                        )
                        onStepGenerated(badgeMsg)
                    }

                    nextInput = Content(role = "user", parts = responseParts)
                }

                // If Gemini ended on a tool call without a final natural-language message, synthesize a concise closing line
                if (textAccumulator.isBlank() && turnContext.pendingSwapProposal != null) {
                    val p = turnContext.pendingSwapProposal!!
                    textAccumulator.append("I've prepared ${p.itemName} (৳${p.priceTk}, ${p.kcal} kcal) for ${p.slotName}. Tap Confirm below to apply it.")
                }
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                timeoutCount.incrementAndGet()
                errorCount.incrementAndGet()
                streamFailure = e
                lastCloudError = describeError(e)
                Log.e(
                    AI_LOG_TAG,
                    "Req #$requestNum TIMEOUT after ${System.currentTimeMillis() - tRequestInitStart}ms (errors=${errorCount.get()}, timeouts=${timeoutCount.get()})",
                    e
                )
                chatSession = null
            } else {
                Log.d(AI_LOG_TAG, "Req #$requestNum cancelled by user/lifecycle after ${System.currentTimeMillis() - tRequestInitStart}ms")
                if (hasEmittedFirstChunk && textAccumulator.isNotBlank()) {
                    onStepGenerated(
                        ChatMessage(
                            id = assistantMsgId,
                            sender = MessageSender.ASSISTANT,
                            content = textAccumulator.toString(),
                            latencyMs = System.currentTimeMillis() - tRequestInitStart,
                            firstTokenMs = firstTokenMs,
                            isLocalExecution = false,
                            isStreaming = false
                        )
                    )
                }
                throw e
            }
        } catch (e: Exception) {
            errorCount.incrementAndGet()
            streamFailure = e
            lastCloudError = describeError(e)
            Log.e(
                AI_LOG_TAG,
                "Req #$requestNum FAILED after ${System.currentTimeMillis() - tRequestInitStart}ms: $lastCloudError (errors=${errorCount.get()})",
                e
            )
            chatSession = null
        } finally {
            activeTurnContext = null
        }

        val totalGenMs = System.currentTimeMillis() - tRequestInitStart
        val totalRequestMs = System.currentTimeMillis() - totalStart
        val pendingProposal = turnContext.pendingSwapProposal

        if (textAccumulator.isNotBlank() && (streamFailure == null || (hasEmittedFirstChunk && textAccumulator.length >= 25))) {
            lastCloudError = null
            Log.d(
                AI_LOG_TAG,
                "Req #$requestNum COMPLETE | TTFT=${firstTokenMs ?: totalGenMs}ms | gen=${totalGenMs}ms | total=${totalRequestMs}ms | chars=${textAccumulator.length} | pendingSwap=${pendingProposal != null}"
            )
            val completedMsg = ChatMessage(
                id = assistantMsgId,
                sender = MessageSender.ASSISTANT,
                content = textAccumulator.toString(),
                tradeoffToken = pendingProposal?.token,
                isPendingConfirmation = pendingProposal != null,
                swapFundingType = pendingProposal?.fundingType,
                swapItemTitle = pendingProposal?.itemName,
                swapPlaceName = pendingProposal?.placeName,
                swapPriceTk = pendingProposal?.priceTk,
                swapKcal = pendingProposal?.kcal,
                swapFeastTitle = pendingProposal?.feastTitle,
                swapShortfallTk = pendingProposal?.shortfallTk,
                swapSummary = pendingProposal?.summary,
                latencyMs = totalGenMs,
                firstTokenMs = firstTokenMs,
                isLocalExecution = false,
                isStreaming = false
            )
            onStepGenerated(completedMsg)
            return completedMsg
        }

        if (streamFailure == null && textAccumulator.isBlank()) {
            errorCount.incrementAndGet()
            lastCloudError = "Gemini returned an empty response."
            Log.e(AI_LOG_TAG, "Req #$requestNum empty response after ${totalGenMs}ms")
        }

        val offlineSuggestion = generateSmartCopilotResponse(
            userText = userText,
            currentSlots = currentSlots,
            isVegetarian = userContext.dietaryPreferences.any { it.equals("Vegetarian", ignoreCase = true) }
        )
        val reason = lastCloudError ?: "Could not reach NutriMate AI."
        val errorText = buildString {
            append("Couldn't reach Gemini right now ($reason)")
            if (BuildConfig.DEBUG) {
                append("\n\nOffline nutrition tip while disconnected:\n$offlineSuggestion")
            } else {
                append("\n\nQuick tip: $offlineSuggestion")
            }
        }

        val errorMsg = ChatMessage(
            id = assistantMsgId,
            sender = MessageSender.ASSISTANT,
            content = errorText,
            latencyMs = totalGenMs,
            firstTokenMs = firstTokenMs,
            isLocalExecution = true,
            isStreaming = false,
            isError = true,
            retryPrompt = userText
        )
        onStepGenerated(errorMsg)
        return errorMsg
    }

    /**
     * Deterministic offline domain helper shown only when Gemini cloud generation is unreachable.
     */
    fun generateSmartCopilotResponse(
        userText: String,
        currentSlots: List<MealSlot>,
        isVegetarian: Boolean
    ): String {
        val lower = userText.trim().lowercase()

        if (lower.matches(Regex("^(hi|hello|hey|salam|assalamu alaikum|hlw|yo|greetings).*")) ||
            lower.contains("who are you") || lower.contains("what can you do") || lower.contains("help")
        ) {
            return "Hey! I track your meal allowances in ৳ and suggest campus-friendly dishes. Ask me about protein, calories, budget meals, or say 'swap lunch to chicken rice bowl'."
        }

        if (lower.contains("protein") || lower.contains("gym") || lower.contains("muscle") ||
            lower.contains("workout") || lower.contains("calories") || lower.contains("diet") ||
            lower.contains("healthy") || lower.contains("weight")
        ) {
            return "Budget high-protein combo (~৳115, ~46g estimated protein): 2 boiled eggs (৳30, ~14g), 1 bowl mushur dal (৳15, ~8g), and quarter chicken roast/bhuna (৳70, ~24g) with salad."
        }

        if (lower.contains("save") || lower.contains("budget") || lower.contains("expensive") ||
            lower.contains("cheap") || lower.contains("cheaper") || lower.contains("money") ||
            lower.contains("allowance") || lower.contains("cost") || lower.contains("taka")
        ) {
            val totalDailyAllowance = currentSlots.sumOf { it.budget }
            return "Your planned meals today total ৳$totalDailyAllowance. Choosing Dal-Bhat with Dim Bhuna (৳45–৳55) can save ৳40–৳60/day compared to heavier platters."
        }

        if (isVegetarian || lower.contains("veg") || lower.contains("niramish") || lower.contains("shobji") ||
            lower.contains("bhorta") || lower.contains("fasting") || lower.contains("roza")
        ) {
            val vegItems = toolsGatekeeper.searchMenu("", "lunch").filter { it.isVegetarian }
            val itemNames = if (vegItems.isNotEmpty()) {
                vegItems.take(2).joinToString { "${it.name} (৳${it.priceTk})" }
            } else {
                "Shobji Khichuri (৳45) and Ruti with mixed shobji (৳28)"
            }
            return "Budget vegetarian picks near campus: $itemNames."
        }

        val targetSlot = when {
            lower.contains("breakfast") -> "breakfast"
            lower.contains("dinner") -> "dinner"
            lower.contains("snack") -> "snacks"
            else -> "lunch"
        }
        val alts = toolsGatekeeper.getAlternatives(targetSlot, 80)
        val altList = alts.take(2).joinToString { "${it.name} (৳${it.priceTk})" }
        return "Affordable $targetSlot picks near your campus: $altList."
    }
}
