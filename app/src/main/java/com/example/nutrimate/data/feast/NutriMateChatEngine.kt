package com.example.nutrimate.data.feast

import android.util.Log
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.ChatMessage
import com.example.nutrimate.model.MealSlot
import com.example.nutrimate.model.MessageSender
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

private const val TAG = "NutriMateChatEngine"

class NutriMateChatEngine(
    val toolsGatekeeper: NutriMateAiToolsGatekeeper
) {
    val onDeviceRouter = NutriMateOnDeviceRouter()

    /**
     * Dedicated single-turn model for Tier 2 synthesis and direct food Q&A.
     * Executes in 1 single turn (no multi-turn tool loops), delivering answers in <1s.
     */
    private val synthesisModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.3f
            },
            systemInstruction = content {
                text(
                    """
                    You are NutriMate AI, a friendly meal planner for university students in Bangladesh on a tight budget.
                    Keep answers clear, realistic, and strictly under 2 sentences.
                    Use Taka (৳). Never invent fake meal plan changes that were not passed to you.
                    """.trimIndent()
                )
            }
        )
    }
    private val model: GenerativeModel by lazy {
        val searchMenuDecl = FunctionDeclaration(
            name = "search_menu",
            description = "Searches the student food catalog for meals matching query, meal slot, or budget.",
            parameters = mapOf(
                "query" to Schema.string("Food item name or keyword (e.g. biriyani, paratha, egg, khichuri)"),
                "slot" to Schema.string("Meal slot: breakfast, lunch, snacks, or dinner"),
                "max_price_tk" to Schema.integer("Maximum price in Taka")
            ),
            optionalParameters = listOf("slot", "max_price_tk")
        )

        val checkDecl = FunctionDeclaration(
            name = "check",
            description = "Read-only dry run checking if a meal fits today's slot budget allowance.",
            parameters = mapOf(
                "slot" to Schema.string("Meal slot: breakfast, lunch, snacks, or dinner"),
                "item_id" to Schema.string("Item ID from search_menu")
            )
        )

        val findFundingDecl = FunctionDeclaration(
            name = "find_funding",
            description = "Finds funding options when an item exceeds budget (unallocated balance or feast cancellation).",
            parameters = mapOf(
                "slot" to Schema.string("Meal slot: breakfast, lunch, snacks, or dinner"),
                "item_id" to Schema.string("Item ID from search_menu")
            )
        )

        val updateDecl = FunctionDeclaration(
            name = "update",
            description = "The only write tool. Mutates plan using signed token from check or find_funding.",
            parameters = mapOf(
                "token" to Schema.string("Signed authorization token from check or find_funding")
            )
        )

        val getAlternativesDecl = FunctionDeclaration(
            name = "get_alternatives",
            description = "Returns 2-3 cheaper alternative meals fitting the slot budget.",
            parameters = mapOf(
                "slot" to Schema.string("Meal slot: breakfast, lunch, snacks, or dinner"),
                "max_price_tk" to Schema.integer("Maximum price in Taka")
            ),
            optionalParameters = listOf("max_price_tk")
        )

        val tools = listOf(
            Tool.functionDeclarations(
                listOf(searchMenuDecl, checkDecl, findFundingDecl, updateDecl, getAlternativesDecl)
            )
        )

        val sysInstruction = content {
            text(
                """
                You are NutriMate AI, a meal planner for students on a tight budget in Bangladesh.
                The user has a monthly plan: 4 meals a day (breakfast, lunch, snack, dinner) and planned weekend feasts.
                The user can change this plan by talking or ask normal food/nutrition questions.
                
                TWO TYPES OF REQUEST:
                1. Plan-changing ("swap my lunch to biriyani", "something cheaper for dinner"):
                   You MUST use tools and change data ONLY through them:
                   - search_menu(query, slot, max_price_tk)
                   - check(slot, item_id)
                   - If check.ok is true: call update(check_token). Once update returns applied, explain in 1 simple sentence.
                   - If check.ok is false (over_budget): do NOT refuse. Call find_funding(slot, item_id). Choose the option with smallest sacrifice (free unallocated balance first, then feast cancellation). Then call update(option.token).
                   - If update returns needs_confirmation: explain the trade-off in 1-2 plain sentences ("I can pay for this by cancelling Friday's feast. You'd lose the feast.") and ask the user to confirm using the app's Confirm button. STOP and do NOT call update again!
                   - If user declines or item not found: call get_alternatives(slot, max_price_tk) and offer them.
                2. Food questions ("is a protein shake worth it?", "healthy snacks"):
                   Answer directly in plain text. Do NOT call tools. No data changes. Keep answer realistic for a student in Bangladesh using Taka (৳).
                
                CRITICAL CONSTRAINTS:
                - Never do budget arithmetic yourself.
                - Never invent prices, item IDs, or tokens.
                - Never confirm a feast trade-off yourself.
                - Never claim a change happened unless update returned "applied".
                - Keep all answers short, encouraging, and clear (1-2 sentences).
                """.trimIndent()
            )
        }

        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.2f
            },
            tools = tools,
            systemInstruction = sysInstruction
        )
    }

    private var chatSession: Chat? = null

    /**
     * Resets the multi-turn chat session if requested.
     */
    fun resetChatSession() {
        chatSession = null
    }

    /**
     * Executes real Gemini inference via Firebase AI Logic and handles the
     * Gemini Function Calling loop deterministically with gatekeeper tools.
     */
    /**
     * Two-Tier Hybrid Architecture:
     * - Tier 1: On-Device Router & Classifier handles Step 1 locally in milliseconds.
     *   Executes search_menu, check, find_funding, and update deterministically without
     *   waiting for multi-turn cloud round-trips.
     * - Tier 2: Real Gemini model (via Firebase AI Logic) synthesizes conversational explanations
     *   for trade-offs or directly answers nutritional inquiries in a single fast hop.
     */
    suspend fun processUserMessage(
        userText: String,
        currentSlots: List<MealSlot>,
        isVegetarian: Boolean,
        dateString: String,
        userId: String?,
        repository: NutriMateFirestoreRepository?,
        onStepGenerated: (ChatMessage) -> Unit
    ): ChatMessage {
        val route = onDeviceRouter.classify(userText)

        // -------------------------------------------------------------------------
        // TIER 1: Plan-changing Request (Local Step 1 Execution)
        // -------------------------------------------------------------------------
        if (route.isPlanChange) {
            val slot = route.slot
            val query = route.query
            val maxPrice = route.maxPriceTk

            // 1. Instantly execute search_menu locally
            val searchResults = toolsGatekeeper.searchMenu(query, slot, maxPrice)
            val searchBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "search_menu(query=\"$query\", slot=\"$slot\")",
                toolName = "search_menu",
                toolArgs = "query=\"$query\", slot=\"$slot\"",
                toolResult = if (searchResults.isNotEmpty()) {
                    "Found ${searchResults.size} items: ${searchResults.first().name} (৳${searchResults.first().priceTk})"
                } else {
                    "No items found"
                }
            )
            onStepGenerated(searchBadge)

            if (searchResults.isEmpty()) {
                val alts = toolsGatekeeper.getAlternatives(slot, maxPrice)
                val altBadge = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.FUNCTION_CALL,
                    content = "get_alternatives(slot=\"$slot\")",
                    toolName = "get_alternatives",
                    toolArgs = "slot=\"$slot\"",
                    toolResult = "${alts.size} alternatives: ${alts.joinToString { "${it.name} (৳${it.priceTk})" }}"
                )
                onStepGenerated(altBadge)

                val altNames = alts.take(3).joinToString { "${it.name} (৳${it.priceTk})" }
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = if (query.isNotBlank()) {
                        "I couldn't find \"$query\" on the campus menu. Here are some alternatives within your budget: $altNames."
                    } else {
                        "Here are budget-friendly alternatives for $slot: $altNames."
                    }
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }

            // 2. Evaluate candidate item with check() locally
            val candidate = searchResults.first()
            val checkRes = toolsGatekeeper.check(slot, candidate.itemId)
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
                }
            )
            onStepGenerated(checkBadge)

            // 3. Within budget: update() and return immediately!
            if (checkRes.ok && checkRes.checkToken != null) {
                val updateRes = toolsGatekeeper.update(checkRes.checkToken, currentSlots, userId, repository)
                val updateBadge = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.FUNCTION_CALL,
                    content = "update(token=\"${checkRes.checkToken.take(8)}...\")",
                    toolName = "update",
                    toolArgs = "token=\"${checkRes.checkToken.take(8)}...\"",
                    toolResult = "status: ${updateRes.status}"
                )
                onStepGenerated(updateBadge)

                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Updated your $slot to ${candidate.name} (৳${candidate.priceTk}, ${candidate.kcal} kcal). It fits nicely within today's allowance!"
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }

            // 4. Over budget: find_funding() locally
            val fundingRes = toolsGatekeeper.findFunding(slot, candidate.itemId)
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
                }
            )
            onStepGenerated(fundingBadge)

            if (fundingRes.options.isEmpty()) {
                val alts = toolsGatekeeper.getAlternatives(slot, maxPrice)
                val altNames = alts.take(3).joinToString { "${it.name} (৳${it.priceTk})" }
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Unfortunately ${candidate.name} is over budget (short by ৳${checkRes.shortByTk ?: 0.0}) and there is no unspent balance or feast available. Try: $altNames."
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }

            // Pick option with smallest sacrifice (first option: unallocated balance or nearest feast)
            val bestOption = fundingRes.options.first()
            val updateRes = toolsGatekeeper.update(bestOption.token, currentSlots, userId, repository)
            val updateBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "update(token=\"${bestOption.token.take(8)}...\")",
                toolName = "update",
                toolArgs = "token=\"${bestOption.token.take(8)}...\"",
                toolResult = "status: ${updateRes.status}"
            )
            onStepGenerated(updateBadge)

            if (updateRes.status == "needs_confirmation") {
                // Tier 2: Generate friendly conversational explanation for the feast trade-off in 1 quick shot
                val prompt = """
                    The student wants to eat ${candidate.name} (৳${candidate.priceTk}) for $slot.
                    It exceeds budget by ৳${bestOption.shortfallTk}.
                    We can pay for it via this trade-off: ${bestOption.summary}.
                    Explain this trade-off in 1 or 2 plain, polite sentences:
                    "I can pay for this by cancelling ${bestOption.feastTitle ?: "your upcoming feast"}. You'd lose the feast."
                    Ask them to confirm using the Confirm button. Keep it under 2 sentences.
                """.trimIndent()

                val explanationText = try {
                    withTimeout(15_000L) {
                        val resp = synthesisModel.generateContent(prompt)
                        resp.text?.takeIf { it.isNotBlank() }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Tier 2 synthesis timed out or failed, using instant fallback", e)
                    null
                } ?: "I can pay for this by cancelling ${bestOption.feastTitle ?: "your upcoming feast"}. You'd lose the feast. Please tap Confirm below if you agree."

                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = explanationText,
                    tradeoffToken = bestOption.token,
                    isPendingConfirmation = true
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            } else if (updateRes.status == "applied") {
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Applied! Funded ৳${bestOption.shortfallTk} from your unallocated monthly balance. Your $slot is now ${candidate.name}!"
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }
        }

        // -------------------------------------------------------------------------
        // TIER 2: Direct Food / Nutrition / Advice Question (Single Turn, No Tools!)
        // -------------------------------------------------------------------------
        try {
            val answer = withTimeout(20_000L) {
                val resp = synthesisModel.generateContent(userText)
                resp.text?.takeIf { it.isNotBlank() }
            } ?: "I am here to help with your student meal plan and budget in Bangladesh!"

            val assistantMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ASSISTANT,
                content = answer
            )
            onStepGenerated(assistantMsg)
            return assistantMsg
        } catch (e: Exception) {
            Log.e(TAG, "Tier 2 generation error, falling back", e)
            val assistantMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ASSISTANT,
                content = "I'm here to help with your meals and budget in Bangladesh. Ask me to change any meal or check your budget!"
            )
            onStepGenerated(assistantMsg)
            return assistantMsg
        }
    }
}
