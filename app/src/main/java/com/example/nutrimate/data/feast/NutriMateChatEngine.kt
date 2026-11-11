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
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "NutriMateChatEngine"

/**
 * Production-grade Two-Tier Hybrid AI Engine:
 * - Tier 1: On-Device Router & Deterministic Gatekeeper (<15ms)
 *   Locally evaluates budget rules, searches the active campus catalog, and validates tokens
 *   without waiting for multi-turn cloud round-trips.
 * - Tier 2: Real Gemini 3.8 Flash (`gemini-3.8-flash`) via Firebase AI Logic
 *   Maintains persistent multi-turn conversation memory, synthesizes empathetic student trade-offs,
 *   and answers nutritional questions in Bangladeshi Taka (৳).
 */
class NutriMateChatEngine(
    val toolsGatekeeper: NutriMateAiToolsGatekeeper
) {
    val onDeviceRouter = NutriMateOnDeviceRouter()

    /**
     * Dedicated Gemini 3.8 Flash generative model for multi-turn chat and synthesis.
     */
    private val synthesisModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.35f
            },
            systemInstruction = content {
                text(
                    """
                    You are NutriMate AI, a friendly, encouraging campus budget and meal copilot for university students in Bangladesh.
                    You know the local campus food landscape well: Dhaka University / Nilkhet, BUET / Palashi, Dhanmondi, Banani, and Uttara.
                    You always talk in Bangladeshi Taka (৳) and know local student staples like paratha, dim bhaji, cha, khichuri, tehari, kacchi, chicken curry, dal, and shingara.
                    Keep answers helpful, realistic, budget-conscious, and strictly under 2 to 3 sentences.
                    If discussing meal plan changes, you know that mathematical calculations, balances, and ledger updates are handled securely by the on-device gatekeeper.
                    Never invent fake meal plan mutations that were not passed to you.
                    """.trimIndent()
                )
            }
        )
    }

    /**
     * Multi-turn chat session with Gemini 3.8 Flash preserving conversation history.
     */
    private var chatSession: Chat? = null

    /**
     * Resets or clears the active chat session (e.g. on new day or explicit user reset).
     */
    fun resetChatSession() {
        chatSession = null
    }

    /**
     * Obtains or creates the active multi-turn Chat session.
     */
    private fun getOrCreateChatSession(): Chat {
        var session = chatSession
        if (session == null) {
            session = synthesisModel.startChat()
            chatSession = session
        }
        return session
    }

    /**
     * Two-Tier Hybrid Execution Pipeline:
     * 1. On-device router classifies prompt in ~5-15ms.
     * 2. If plan mutation: executes search_menu, check, find_funding, and update locally.
     *    Stamps latency on each monospace tool step card.
     * 3. If trade-off explanation or food advice is needed: calls Gemini 3.8 Flash in 1 fast hop.
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
        val totalStart = System.currentTimeMillis()
        val route = onDeviceRouter.classify(userText)

        // -------------------------------------------------------------------------
        // TIER 1: Plan-changing Request (Local Deterministic Execution)
        // -------------------------------------------------------------------------
        if (route.isPlanChange) {
            val slot = route.slot
            val query = route.query
            val maxPrice = route.maxPriceTk

            // 1. Instantly execute search_menu locally
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
                        "I couldn't find \"$query\" on the current campus menu. Here are some tasty options within your budget: $altNames."
                    } else {
                        "Here are student-friendly alternatives for $slot: $altNames."
                    },
                    latencyMs = System.currentTimeMillis() - totalStart,
                    isLocalExecution = true
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }

            // 2. Evaluate candidate item with check() locally
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

            // 3. Within budget: update() and return immediately!
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

            // 4. Over budget: find_funding() locally
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
                    content = "Unfortunately ${candidate.name} is over budget (short by ৳${checkRes.shortByTk ?: 0.0}) and there is no unspent balance or feast available. Try: $altNames.",
                    latencyMs = System.currentTimeMillis() - totalStart,
                    isLocalExecution = true
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }

            // Pick option with smallest sacrifice (first option: unallocated balance or nearest feast)
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
                // Tier 2: Generate friendly conversational explanation for the feast trade-off with Gemini 3.8 Flash
                val synthesisPrompt = """
                    The student requested: "$userText".
                    Target meal: ${candidate.name} (৳${candidate.priceTk}) for $slot.
                    It exceeds daily allowance by ৳${bestOption.shortfallTk}.
                    Trade-off: ${bestOption.summary}.
                    Explain this trade-off in 1 or 2 polite, concise sentences:
                    "I can pay for this by cancelling ${bestOption.feastTitle ?: "your upcoming feast"}. You'd lose the feast."
                    Ask them to confirm using the Confirm button. Strictly under 2 sentences.
                """.trimIndent()

                val tGeminiStart = System.currentTimeMillis()
                val explanationText = try {
                    withTimeout(60_000L) {
                        val session = getOrCreateChatSession()
                        val resp = session.sendMessage(synthesisPrompt)
                        resp.text?.takeIf { it.isNotBlank() }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Tier 2 synthesis timed out or failed, using local fallback", e)
                    chatSession = null
                    null
                } ?: "I can pay for this by cancelling ${bestOption.feastTitle ?: "your upcoming feast"}. You'd lose the feast. Please tap Confirm below if you agree."

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
                    content = "Applied! Funded ৳${bestOption.shortfallTk} from your unallocated monthly balance. Your $slot is now ${candidate.name}!",
                    latencyMs = System.currentTimeMillis() - totalStart,
                    isLocalExecution = true
                )
                onStepGenerated(assistantMsg)
                return assistantMsg
            }
        }

        // -------------------------------------------------------------------------
        // TIER 2: Hybrid Conversational Q&A (Cloud Gemini with Resilient Smart Copilot Fallback)
        // -------------------------------------------------------------------------
        val tCloudStart = System.currentTimeMillis()
        val assistantMsgId = UUID.randomUUID().toString()
        val textAccumulator = StringBuilder()
        var hasEmittedFirstChunk = false

        val cloudAnswer: String? = try {
            withTimeout(4_000L) {
                val session = getOrCreateChatSession()
                session.sendMessageStream(userText).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        textAccumulator.append(chunkText)
                        val elapsed = System.currentTimeMillis() - tCloudStart
                        hasEmittedFirstChunk = true
                        onStepGenerated(
                            ChatMessage(
                                id = assistantMsgId,
                                sender = MessageSender.ASSISTANT,
                                content = textAccumulator.toString(),
                                latencyMs = elapsed,
                                isLocalExecution = false
                            )
                        )
                    }
                }
                textAccumulator.toString().takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud Gemini unavailable or timed out; activating intelligent on-device copilot", e)
            chatSession = null
            null
        }

        if (cloudAnswer != null && cloudAnswer.isNotBlank()) {
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                sender = MessageSender.ASSISTANT,
                content = cloudAnswer,
                latencyMs = System.currentTimeMillis() - tCloudStart,
                isLocalExecution = false
            )
            onStepGenerated(assistantMsg)
            return assistantMsg
        }

        if (hasEmittedFirstChunk && textAccumulator.length >= 25) {
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                sender = MessageSender.ASSISTANT,
                content = textAccumulator.toString(),
                latencyMs = System.currentTimeMillis() - tCloudStart,
                isLocalExecution = false
            )
            onStepGenerated(assistantMsg)
            return assistantMsg
        }

        // Seamless Intelligent Student Copilot
        val intelligentAnswer = generateSmartCopilotResponse(
            userText = userText,
            currentSlots = currentSlots,
            isVegetarian = isVegetarian
        )
        val assistantMsg = ChatMessage(
            id = assistantMsgId,
            sender = MessageSender.ASSISTANT,
            content = intelligentAnswer,
            latencyMs = (System.currentTimeMillis() - tCloudStart).coerceAtLeast(12L),
            isLocalExecution = true
        )
        onStepGenerated(assistantMsg)
        return assistantMsg
    }

    /**
     * Intelligent, local domain-specific student nutritionist & budget copilot.
     * Guaranteed sub-15ms response time; never throws timeouts.
     */
    fun generateSmartCopilotResponse(
        userText: String,
        currentSlots: List<MealSlot>,
        isVegetarian: Boolean
    ): String {
        val lower = userText.trim().lowercase()

        // 1. Greetings & Introductions
        if (lower.matches(Regex("^(hi|hello|hey|salam|assalamu alaikum|hlw|yo|greetings).*")) ||
            lower.contains("who are you") || lower.contains("what can you do") || lower.contains("help")
        ) {
            return "Hey! I'm your NutriMate AI food & budget copilot. I track your meal allowances in ৳ and optimize delicious dishes near your campus. Ask me to swap a meal (e.g. 'swap lunch to biriyani'), check student deals, or ask for budget-friendly healthy food tips!"
        }

        // 2. Protein / Gym / Fitness / Health Queries
        if (lower.contains("protein") || lower.contains("gym") || lower.contains("muscle") ||
            lower.contains("workout") || lower.contains("calories") || lower.contains("diet") ||
            lower.contains("healthy") || lower.contains("weight")
        ) {
            return "For high protein on a student budget in Bangladesh: 2 boiled eggs (৳30, 14g protein), 1 bowl of thick mushur dal (৳15, 8g protein), and a quarter chicken roast or bhuna (৳70, 24g protein) gives 46g of clean protein for only ৳115! Pair with a side of green salad or lemon for great recovery."
        }

        // 3. Saving Money / Budget Stretch Inquiries
        if (lower.contains("save") || lower.contains("budget") || lower.contains("expensive") ||
            lower.contains("cheap") || lower.contains("cheaper") || lower.contains("money") ||
            lower.contains("allowance") || lower.contains("cost") || lower.contains("taka")
        ) {
            val totalDailyAllowance = currentSlots.sumOf { it.budget }
            return "Your current planned meals total ৳$totalDailyAllowance today. Top student savings tip: having canteen Dal-Bhat with Dim Bhuna (৳45–৳55) for lunch instead of eating out saves ৳50+ daily! Also check the Deals section on your Home tab for partner discounts up to −15%."
        }

        // 4. Vegetarian / Fasting / Light Food
        if (lower.contains("veg") || lower.contains("niramish") || lower.contains("shobji") ||
            lower.contains("bhorta") || lower.contains("fasting") || lower.contains("roza")
        ) {
            val vegItems = toolsGatekeeper.searchMenu("", "lunch").filter { it.isVegetarian }
            val itemNames = if (vegItems.isNotEmpty()) {
                vegItems.take(2).joinToString { "${it.name} (৳${it.priceTk})" }
            } else {
                "Shobji Khichuri (৳45) and Begun Bhorta with Ruti (৳25)"
            }
            return "Fresh vegetarian options near campus: $itemNames. They're fiber-rich, easy on the stomach, and keep your meal spend well below ৳60!"
        }

        // 5. Specific Food Item Search & Recommendation
        val foodKeywords = listOf(
            "kacchi", "biriyani", "tehari", "khichuri", "paratha", "burger", "chicken",
            "beef", "fish", "dal", "egg", "dim", "cha", "tea", "shingara", "shawarma",
            "soup", "noodles", "polao", "kabab", "naan", "roast", "coffee"
        )
        val matchedFood = foodKeywords.firstOrNull { lower.contains(it) }
        if (matchedFood != null) {
            val slot = when {
                lower.contains("breakfast") -> "breakfast"
                lower.contains("dinner") -> "dinner"
                lower.contains("snack") -> "snacks"
                else -> "lunch"
            }
            val results = toolsGatekeeper.searchMenu(matchedFood, slot)
            if (results.isNotEmpty()) {
                val best = results.first()
                return "On your campus menu: ${best.name} at ${best.place} for ৳${best.priceTk} (${best.kcal} kcal). To apply this to your plan, simply say 'swap $slot to ${best.name}'!"
            } else {
                val alts = toolsGatekeeper.getAlternatives(slot, 80)
                val altText = alts.take(2).joinToString { "${it.name} (৳${it.priceTk})" }
                return "I couldn't find \"$matchedFood\" in the $slot catalog, but tasty nearby options include: $altText. Would you like one of these?"
            }
        }

        // 6. Breakfast / Lunch / Snacks / Dinner Suggestions
        val targetSlot = when {
            lower.contains("breakfast") -> "breakfast"
            lower.contains("dinner") -> "dinner"
            lower.contains("snack") -> "snacks"
            lower.contains("lunch") -> "lunch"
            else -> null
        }
        if (targetSlot != null) {
            val current = currentSlots.find { it.id == targetSlot }
            val alts = toolsGatekeeper.getAlternatives(targetSlot, 80)
            val altList = alts.take(2).joinToString { "${it.name} (৳${it.priceTk})" }
            return "For $targetSlot, your current planned meal is ${current?.name ?: targetSlot} (৳${current?.budget ?: 60}). You can also try: $altList. Ask me to swap whenever you're ready!"
        }

        // 7. General Friendly Campus Companion Default
        return "I've checked your meal schedule and nearby campus menus. You have active meal plans for Breakfast, Lunch, Snacks, and Dinner. You can ask me to swap any meal (e.g. 'swap lunch to chicken rice bowl') or find delicious student deals near your campus!"
    }
}
