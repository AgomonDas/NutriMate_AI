package com.example.nutrimate.data.feast

import android.util.Log
import com.example.nutrimate.BuildConfig
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.ChatMessage
import com.example.nutrimate.model.MealSlot
import com.example.nutrimate.model.MessageSender
import com.example.nutrimate.model.NutriMateUserContext
import com.google.firebase.Firebase
import com.google.firebase.ai.TemplateGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "NutriMateChatEngine"
private const val AI_LOG_TAG = "NUTRIMATE_AI"
private const val MAX_HISTORY_TURNS = 10
private const val STREAM_TIMEOUT_MS = 45_000L

/**
 * Production-grade Two-Tier Hybrid AI Engine:
 * - Tier 1: On-Device Router & Deterministic Gatekeeper (<15ms)
 *   Locally evaluates budget rules, searches the active campus catalog, and validates tokens
 *   without waiting for multi-turn cloud round-trips.
 * - Tier 2: Real-Time Streaming via Firebase AI Logic Server Prompt Template (`nutrimate-copilot-v1`)
 *   Streams tokens immediately as they arrive, injects structured live user context into template inputs,
 *   maintains bounded multi-turn conversation memory, and emits NUTRIMATE_AI latency telemetry.
 */
@OptIn(PublicPreviewAPI::class)
class NutriMateChatEngine(
    val toolsGatekeeper: NutriMateAiToolsGatekeeper
) {
    companion object {
        const val PROMPT_TEMPLATE_ID = "nutrimate-copilot-v1"

        fun buildTemplateInputs(
            userText: String,
            userContext: NutriMateUserContext,
            historyText: String = ""
        ): Map<String, Any> = buildMap {
            val contextJson = userContext.toCompactJson()
            // Common prompt / user message variable names for server Dotprompt templates
            put("user_message", userText)
            put("userMessage", userText)
            put("message", userText)
            put("prompt", userText)
            put("query", userText)
            put("user_input", userText)
            put("userInput", userText)
            // Structured live user & budget context
            put("user_context", contextJson)
            put("userContext", contextJson)
            put("context", contextJson)
            // Granular context variables in case the template binds individual placeholders
            put("user_name", userContext.userName)
            put("userName", userContext.userName)
            put("user_goal", userContext.userGoal)
            put("campus_area", userContext.campusArea)
            put("university", userContext.university)
            put("daily_calorie_target", userContext.dailyCalorieTarget)
            put("planned_calories_today", userContext.plannedCaloriesToday)
            put("remaining_calories_today", userContext.remainingCaloriesToday)
            put("monthly_budget_tk", userContext.monthlyBudgetTk)
            put("remaining_monthly_budget_tk", userContext.remainingMonthlyBudgetTk)
            put("today_allowance_tk", userContext.todaySlotAllowanceTotalTk)
            put("unallocated_reserve_tk", userContext.unallocatedReserveTk)
            put("dietary_preferences", userContext.dietaryPreferences.joinToString(", "))
            put("planned_meals_today", userContext.plannedMealsToday.joinToString("; "))
            put("nearby_partner_deals", userContext.nearbyPartnerDeals.joinToString("; "))
            put("date", userContext.dateString)
            if (historyText.isNotBlank()) {
                put("chat_history", historyText)
                put("conversation_history", historyText)
            }
        }
    }

    val onDeviceRouter = NutriMateOnDeviceRouter()

    private val totalRequests = AtomicInteger(0)
    private val errorCount = AtomicInteger(0)
    private val timeoutCount = AtomicInteger(0)

    @Volatile
    private var templateSupportsChatHistory: Boolean = true

    /** Last cloud failure, so UI/logs can surface exact failure diagnostics. */
    @Volatile
    var lastCloudError: String? = null
        private set

    fun describeError(e: Throwable): String = when {
        e is TimeoutCancellationException ->
            "Request timed out after ${STREAM_TIMEOUT_MS / 1000}s waiting for Gemini."
        e.message?.contains("App Check", ignoreCase = true) == true ||
            e.message?.contains("403") == true ->
            "App Check / permission rejected (ensure Firebase AI Logic is enabled and debug token is registered)."
        e.message?.contains("not found", ignoreCase = true) == true ||
            e.message?.contains("404") == true ->
            "Prompt template \"$PROMPT_TEMPLATE_ID\" or its model was not found in this Firebase project."
        e.message?.contains("INVALID_ARGUMENT", ignoreCase = true) == true ||
            e.message?.contains("schema", ignoreCase = true) == true ->
            "Prompt template \"$PROMPT_TEMPLATE_ID\" rejected inputs: ${e.localizedMessage ?: e.javaClass.simpleName}"
        e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
            e.message?.contains("network", ignoreCase = true) == true ->
            "Network unavailable. Check your internet connection and tap Retry."
        else -> e.localizedMessage ?: e.javaClass.simpleName
    }

    /**
     * Server-managed Firebase AI Logic prompt template model (`nutrimate-copilot-v1`),
     * initialized lazily and reused across requests.
     */
    private val templateModel: TemplateGenerativeModel by lazy {
        val initStart = System.currentTimeMillis()
        val model = Firebase.ai.templateGenerativeModel()
        Log.d(
            AI_LOG_TAG,
            "TemplateGenerativeModel ($PROMPT_TEMPLATE_ID) initialized lazily in ${System.currentTimeMillis() - initStart}ms"
        )
        model
    }

    /**
     * Bounded multi-turn conversation history preserved across turns.
     */
    private val conversationHistory = mutableListOf<Content>()

    /**
     * Hydrates multi-turn memory from capped recent persisted user/assistant turns.
     */
    @Synchronized
    fun hydrateHistory(recentMessages: List<ChatMessage>) {
        val turns = recentMessages
            .filter { !it.isError && !it.isStreaming && it.content.isNotBlank() }
            .filter { it.sender == MessageSender.USER || it.sender == MessageSender.ASSISTANT }
            .takeLast(MAX_HISTORY_TURNS)

        conversationHistory.clear()
        for (msg in turns) {
            val role = if (msg.sender == MessageSender.USER) "user" else "model"
            // Avoid priming empty conversation with only a model greeting
            if (conversationHistory.isEmpty() && role == "model") continue
            conversationHistory.add(content(role) { text(msg.content.take(1500)) })
        }
        Log.d(AI_LOG_TAG, "Hydrated multi-turn session with ${conversationHistory.size} persisted turns")
    }

    /**
     * Resets or clears the active chat history (e.g. on New Chat or account switch).
     */
    @Synchronized
    fun resetChatSession() {
        conversationHistory.clear()
        Log.d(AI_LOG_TAG, "Chat session reset")
    }

    @Synchronized
    private fun snapshotHistory(): List<Content> = conversationHistory.toList()

    @Synchronized
    private fun appendCompletedTurn(userMessage: String, modelResponse: String) {
        conversationHistory.add(content("user") { text(userMessage.take(1500)) })
        conversationHistory.add(content("model") { text(modelResponse.take(1500)) })
        val maxEntries = MAX_HISTORY_TURNS * 2
        while (conversationHistory.size > maxEntries) {
            conversationHistory.removeAt(0)
        }
    }

    @Synchronized
    private fun formatHistoryForTemplateInput(): String {
        if (conversationHistory.isEmpty()) return ""
        return conversationHistory.takeLast(6).joinToString("\n") { entry ->
            val speaker = if (entry.role == "user") "Student" else "NutriMate"
            val text = entry.parts.joinToString(" ") { part ->
                (part as? com.google.firebase.ai.type.TextPart)?.text.orEmpty()
            }.trim()
            "$speaker: $text"
        }
    }

    private fun isHistoryUnsupportedError(e: Throwable): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("history") &&
            (msg.contains("not") || msg.contains("invalid") || msg.contains("unexpected") || msg.contains("400"))
    }

    private fun isStrictInputSchemaError(e: Throwable): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return (msg.contains("additional") || msg.contains("unknown") || msg.contains("unexpected") || msg.contains("schema")) &&
            (msg.contains("input") || msg.contains("property") || msg.contains("variable"))
    }

    /**
     * Two-Tier Hybrid Execution Pipeline:
     * 1. On-device router classifies prompt in ~1-15ms.
     * 2. If explicit plan mutation: executes search_menu, check, find_funding, and update locally.
     * 3. Otherwise: invokes Firebase server prompt template `nutrimate-copilot-v1` with structured user context
     *    and streams tokens live.
     */
    suspend fun processUserMessage(
        userText: String,
        currentSlots: List<MealSlot>,
        userContext: NutriMateUserContext,
        contextBuildMs: Long = 0L,
        userId: String?,
        repository: NutriMateFirestoreRepository?,
        onStepGenerated: (ChatMessage) -> Unit
    ): ChatMessage {
        val totalStart = System.currentTimeMillis()
        val requestNum = totalRequests.incrementAndGet()
        val route = onDeviceRouter.classify(userText)

        // -------------------------------------------------------------------------
        // TIER 1: Plan-changing Request (Deterministic Gatekeeper Execution)
        // -------------------------------------------------------------------------
        if (route.isPlanChange) {
            val slot = route.slot
            val query = route.query
            val maxPrice = route.maxPriceTk

            val tSearchStart = System.currentTimeMillis()
            val searchResults = toolsGatekeeper.searchMenu(query, slot, maxPrice)
            val searchLatency = (System.currentTimeMillis() - tSearchStart).coerceAtLeast(1L)

            val searchBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "search_menu(query=\"$query\", slot=\"$slot\")",
                toolName = "search_menu",
                toolArgs = "query=\"$query\", slot=\"$slot\"" + (if (maxPrice != null) ", max_price_tk=$maxPrice" else ""),
                toolResult = if (searchResults.isNotEmpty()) {
                    "Found ${searchResults.size} items: ${searchResults.first().name} (৳${searchResults.first().priceTk})"
                } else {
                    "No items found matching \"$query\""
                },
                latencyMs = searchLatency,
                isLocalExecution = true
            )
            onStepGenerated(searchBadge)

            if (searchResults.isEmpty()) {
                val tAltStart = System.currentTimeMillis()
                val alts = toolsGatekeeper.getAlternatives(slot, maxPrice)
                val altLatency = (System.currentTimeMillis() - tAltStart).coerceAtLeast(1L)

                val altBadge = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.FUNCTION_CALL,
                    content = "get_alternatives(slot=\"$slot\")",
                    toolName = "get_alternatives",
                    toolArgs = "slot=\"$slot\"" + (if (maxPrice != null) ", max_price_tk=$maxPrice" else ""),
                    toolResult = "${alts.size} alternatives: ${alts.joinToString { "${it.name} (৳${it.priceTk})" }}",
                    latencyMs = altLatency,
                    isLocalExecution = true
                )
                onStepGenerated(altBadge)

                val altNames = alts.take(3).joinToString { "${it.name} (৳${it.priceTk})" }
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = if (query.isNotBlank()) {
                        "I couldn't find \"$query\" on your active campus menu. Tasty alternatives within budget: $altNames."
                    } else {
                        "Here are budget-friendly options for $slot: $altNames."
                    },
                    latencyMs = System.currentTimeMillis() - totalStart,
                    isLocalExecution = true
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }

            val candidate = searchResults.first()
            val tCheckStart = System.currentTimeMillis()
            val checkRes = toolsGatekeeper.check(slot, candidate.itemId)
            val checkLatency = (System.currentTimeMillis() - tCheckStart).coerceAtLeast(1L)

            val checkBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "check(slot=\"$slot\", item_id=\"${candidate.itemId}\")",
                toolName = "check",
                toolArgs = "slot=\"$slot\", item_id=\"${candidate.itemId}\"",
                toolResult = if (checkRes.ok) {
                    "ok: true, price=৳${checkRes.priceTk}, allowance=৳${checkRes.slotBudgetTk}"
                } else {
                    "ok: false, reason=over_budget, short_by=৳${String.format("%.2f", checkRes.shortByTk ?: 0.0)}"
                },
                latencyMs = checkLatency,
                isLocalExecution = true
            )
            onStepGenerated(checkBadge)

            if (checkRes.ok && checkRes.checkToken != null) {
                val tUpdateStart = System.currentTimeMillis()
                val updateRes = toolsGatekeeper.update(checkRes.checkToken, currentSlots, userId, repository)
                val updateLatency = (System.currentTimeMillis() - tUpdateStart).coerceAtLeast(1L)

                val updateBadge = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.FUNCTION_CALL,
                    content = "update(token=\"${checkRes.checkToken.take(8)}...\")",
                    toolName = "update",
                    toolArgs = "token=\"${checkRes.checkToken.take(8)}...\"",
                    toolResult = "status: ${updateRes.status}",
                    latencyMs = updateLatency,
                    isLocalExecution = true
                )
                onStepGenerated(updateBadge)

                val totalElapsed = System.currentTimeMillis() - totalStart
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Updated your $slot to ${candidate.name} (৳${candidate.priceTk}, ${candidate.kcal} kcal). It fits nicely within today's allowance!",
                    latencyMs = totalElapsed,
                    isLocalExecution = true
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }

            val tFundingStart = System.currentTimeMillis()
            val fundingRes = toolsGatekeeper.findFunding(slot, candidate.itemId)
            val fundingLatency = (System.currentTimeMillis() - tFundingStart).coerceAtLeast(1L)

            val fundingBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "find_funding(slot=\"$slot\", item_id=\"${candidate.itemId}\")",
                toolName = "find_funding",
                toolArgs = "slot=\"$slot\", item_id=\"${candidate.itemId}\"",
                toolResult = if (fundingRes.options.isNotEmpty()) {
                    "option: ${fundingRes.options.first().summary}"
                } else {
                    "no funding available"
                },
                latencyMs = fundingLatency,
                isLocalExecution = true
            )
            onStepGenerated(fundingBadge)

            if (fundingRes.options.isEmpty()) {
                val alts = toolsGatekeeper.getAlternatives(slot, maxPrice)
                val altNames = alts.take(3).joinToString { "${it.name} (৳${it.priceTk})" }
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "${candidate.name} exceeds today's $slot allowance by ৳${checkRes.shortByTk ?: 0.0} and no unallocated buffer or feast is available. Try: $altNames.",
                    latencyMs = System.currentTimeMillis() - totalStart,
                    isLocalExecution = true
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }

            val bestOption = fundingRes.options.first()
            val tUpdateStart = System.currentTimeMillis()
            val updateRes = toolsGatekeeper.update(bestOption.token, currentSlots, userId, repository)
            val updateLatency = (System.currentTimeMillis() - tUpdateStart).coerceAtLeast(1L)

            val updateBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "update(token=\"${bestOption.token.take(8)}...\")",
                toolName = "update",
                toolArgs = "token=\"${bestOption.token.take(8)}...\"",
                toolResult = "status: ${updateRes.status}",
                latencyMs = updateLatency,
                isLocalExecution = true
            )
            onStepGenerated(updateBadge)

            if (updateRes.status == "needs_confirmation") {
                val synthesisPrompt = """
                    USER_CONTEXT: ${userContext.toCompactJson()}
                    The student requested: "$userText".
                    Target meal: ${candidate.name} (৳${candidate.priceTk}) for $slot.
                    Shortfall: ৳${bestOption.shortfallTk}.
                    Trade-off option: ${bestOption.summary}.
                    Explain this trade-off politely in 1 to 2 concise sentences and ask them to tap Confirm below if they want to apply it.
                """.trimIndent()

                val tGeminiStart = System.currentTimeMillis()
                val tradeoffInputs = buildTemplateInputs(
                    userText = synthesisPrompt,
                    userContext = userContext,
                    historyText = formatHistoryForTemplateInput()
                )
                val explanationText = try {
                    withTimeout(STREAM_TIMEOUT_MS) {
                        val resp = if (templateSupportsChatHistory) {
                            try {
                                templateModel.startChat(
                                    templateId = PROMPT_TEMPLATE_ID,
                                    inputs = tradeoffInputs,
                                    history = emptyList()
                                ).sendMessage(synthesisPrompt)
                            } catch (e: Exception) {
                                if (isHistoryUnsupportedError(e)) {
                                    templateSupportsChatHistory = false
                                    templateModel.generateContent(PROMPT_TEMPLATE_ID, tradeoffInputs)
                                } else if (isStrictInputSchemaError(e)) {
                                    templateModel.startChat(
                                        templateId = PROMPT_TEMPLATE_ID,
                                        inputs = emptyMap(),
                                        history = emptyList()
                                    ).sendMessage(synthesisPrompt)
                                } else {
                                    throw e
                                }
                            }
                        } else {
                            templateModel.generateContent(PROMPT_TEMPLATE_ID, tradeoffInputs)
                        }
                        resp.text?.takeIf { it.isNotBlank() }
                    }
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException) {
                        timeoutCount.incrementAndGet()
                        errorCount.incrementAndGet()
                        lastCloudError = describeError(e)
                        Log.e(AI_LOG_TAG, "Tier 2 tradeoff synthesis timed out: $lastCloudError", e)
                        null
                    } else throw e
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                    lastCloudError = describeError(e)
                    Log.e(AI_LOG_TAG, "Tier 2 tradeoff synthesis failed: $lastCloudError", e)
                    null
                } ?: "I can cover the ৳${bestOption.shortfallTk} difference by cancelling ${bestOption.feastTitle ?: "your upcoming feast"}. Please tap Confirm below if you want to proceed."

                val geminiLatency = System.currentTimeMillis() - tGeminiStart
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = explanationText,
                    tradeoffToken = bestOption.token,
                    isPendingConfirmation = true,
                    latencyMs = geminiLatency,
                    isLocalExecution = false
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            } else if (updateRes.status == "applied") {
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Applied! Funded ৳${bestOption.shortfallTk} from your unallocated reserve. Your $slot is now ${candidate.name}!",
                    latencyMs = System.currentTimeMillis() - totalStart,
                    isLocalExecution = true
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }
        }

        // -------------------------------------------------------------------------
        // TIER 2: Personalized Real-Time Streaming Gemini Q&A
        // -------------------------------------------------------------------------
        val assistantMsgId = UUID.randomUUID().toString()
        val textAccumulator = StringBuilder()
        var firstTokenMs: Long? = null
        var hasEmittedFirstChunk = false

        val historySnapshot = snapshotHistory()
        val templateInputs = buildTemplateInputs(
            userText = userText,
            userContext = userContext,
            historyText = formatHistoryForTemplateInput()
        )
        val contextualPrompt = """
            USER_CONTEXT: ${userContext.toCompactJson()}
            USER_MESSAGE: $userText
        """.trimIndent()

        val tRequestInitStart = System.currentTimeMillis()
        Log.d(
            AI_LOG_TAG,
            "Req #$requestNum start | template=$PROMPT_TEMPLATE_ID | router=${route.classificationTimeMs}ms | context=${contextBuildMs}ms | historyTurns=${historySnapshot.size}"
        )

        suspend fun collectResponseStream(flow: kotlinx.coroutines.flow.Flow<com.google.firebase.ai.type.GenerateContentResponse>) {
            flow.collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    textAccumulator.append(chunkText)
                    val elapsed = System.currentTimeMillis() - tRequestInitStart
                    if (!hasEmittedFirstChunk) {
                        hasEmittedFirstChunk = true
                        firstTokenMs = elapsed
                        Log.d(AI_LOG_TAG, "Req #$requestNum TTFT=${elapsed}ms")
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
        }

        var streamFailure: Throwable? = null
        try {
            withTimeout(STREAM_TIMEOUT_MS) {
                val initElapsed = System.currentTimeMillis() - tRequestInitStart
                Log.d(AI_LOG_TAG, "Req #$requestNum requestInit=${initElapsed}ms -> opening template stream ($PROMPT_TEMPLATE_ID)")

                if (templateSupportsChatHistory) {
                    try {
                        val chat = templateModel.startChat(
                            templateId = PROMPT_TEMPLATE_ID,
                            inputs = templateInputs,
                            history = historySnapshot
                        )
                        collectResponseStream(chat.sendMessageStream(contextualPrompt))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (!hasEmittedFirstChunk && isHistoryUnsupportedError(e)) {
                            Log.w(AI_LOG_TAG, "Template $PROMPT_TEMPLATE_ID does not define {{history}}; switching to generateContentStream", e)
                            templateSupportsChatHistory = false
                            collectResponseStream(
                                templateModel.generateContentStream(
                                    templateId = PROMPT_TEMPLATE_ID,
                                    inputs = templateInputs
                                )
                            )
                        } else if (!hasEmittedFirstChunk && isStrictInputSchemaError(e)) {
                            Log.w(AI_LOG_TAG, "Template $PROMPT_TEMPLATE_ID rejected extra input keys; retrying with empty inputs + chat history", e)
                            val chat = templateModel.startChat(
                                templateId = PROMPT_TEMPLATE_ID,
                                inputs = emptyMap(),
                                history = historySnapshot
                            )
                            collectResponseStream(chat.sendMessageStream(contextualPrompt))
                        } else {
                            throw e
                        }
                    }
                } else {
                    collectResponseStream(
                        templateModel.generateContentStream(
                            templateId = PROMPT_TEMPLATE_ID,
                            inputs = templateInputs
                        )
                    )
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
            } else {
                // Cooperative cancellation (e.g., user tapped Stop or left session)
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
        }

        val totalGenMs = System.currentTimeMillis() - tRequestInitStart
        val totalRequestMs = System.currentTimeMillis() - totalStart

        // Happy path or graceful preservation if substantial streamed content arrived before interruption
        if (textAccumulator.isNotBlank() && (streamFailure == null || (hasEmittedFirstChunk && textAccumulator.length >= 25))) {
            lastCloudError = null
            appendCompletedTurn(userText, textAccumulator.toString())
            Log.d(
                AI_LOG_TAG,
                "Req #$requestNum COMPLETE | TTFT=${firstTokenMs ?: totalGenMs}ms | gen=${totalGenMs}ms | total=${totalRequestMs}ms | chars=${textAccumulator.length}"
            )
            val completedMsg = ChatMessage(
                id = assistantMsgId,
                sender = MessageSender.ASSISTANT,
                content = textAccumulator.toString(),
                latencyMs = totalGenMs,
                firstTokenMs = firstTokenMs,
                isLocalExecution = false,
                isStreaming = false
            )
            onStepGenerated(completedMsg)
            return completedMsg
        }

        // Empty response or cloud failure -> surface transparent error with Retry + compact offline hint
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
