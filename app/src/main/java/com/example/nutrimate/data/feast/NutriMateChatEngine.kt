package com.example.nutrimate.data.feast

import android.util.Log
import com.example.nutrimate.data.model.MealItemEntity
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.ChatMessage
import com.example.nutrimate.model.MealSlot
import com.example.nutrimate.model.MessageSender
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "NutriMateChatEngine"

sealed interface ChatEngineResult {
    data class Success(val messages: List<ChatMessage>) : ChatEngineResult
    data class Error(val message: String) : ChatEngineResult
}

class NutriMateChatEngine(
    val toolsGatekeeper: NutriMateAiToolsGatekeeper
) {
    private val model by lazy {
        try {
            val searchMenuDecl = FunctionDeclaration(
                name = "search_menu",
                description = "Searches the student menu catalog for meals matching query, meal slot, or budget.",
                parameters = mapOf(
                    "query" to Schema.string("Food item name or keyword (e.g. biriyani, paratha, egg)"),
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
                    You let the user change this plan by talking and answer normal food/nutrition questions.
                    
                    RULES:
                    1. Plan-changing requests: Use tools and change data ONLY through them.
                    2. Food/nutrition questions: Answer directly in text. No tools, no data changes.
                    3. Never edit data directly. Never invent prices or item IDs. Never do budget arithmetic yourself.
                    4. Flow for plan changes:
                       - search_menu for the item
                       - check(slot, item_id)
                       - if ok: update(check_token), tell user in one sentence
                       - if over_budget: call find_funding(slot, item_id), pick the option with smallest sacrifice, call update(option_token)
                       - if update returns needs_confirmation: explain the trade-off in 1-2 plain sentences ("I can pay for this by cancelling Friday's feast. You'd lose the feast.") and ask the user to confirm. Stop. Do not call update again.
                    5. Style: Short, friendly, plain language. Use Tk (৳). 1-2 sentences per reply.
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase AI GenerativeModel", e)
            null
        }
    }

    /**
     * Executes chat interaction for user input.
     * Evaluates whether request is food question or plan change, dispatches tools,
     * and streams monospace tool execution badges into the chat trace.
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
        val lower = userText.trim().lowercase()

        // 1. Check if user is asking a nutrition/food question (Type 2: direct answer, no tools)
        val isFoodQuestion = isNutritionQuestion(lower)
        if (isFoodQuestion) {
            val answer = answerNutritionQuestion(lower, isVegetarian)
            val assistantMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ASSISTANT,
                content = answer
            )
            onStepGenerated(assistantMsg)
            return assistantMsg
        }

        // 2. Plan-changing request (Type 1: deterministic tool execution with tool-call trace)
        return executePlanChangeFlow(
            userText = userText,
            currentSlots = currentSlots,
            isVegetarian = isVegetarian,
            dateString = dateString,
            userId = userId,
            repository = repository,
            onStepGenerated = onStepGenerated
        )
    }

    private fun isNutritionQuestion(text: String): Boolean {
        val triggers = listOf(
            "protein shake", "is it worth", "how much protein", "calorie", "calories in",
            "gym diet", "healthy snack", "good for gym", "benefits of", "nutrition in",
            "vitamins", "boiled egg nutrition", "creatine", "whey"
        )
        return triggers.any { text.contains(it) } && !text.contains("swap") && !text.contains("change") && !text.contains("want")
    }

    private fun answerNutritionQuestion(text: String, isVegetarian: Boolean): String {
        return when {
            text.contains("protein shake") || text.contains("whey") -> {
                "Protein shakes are helpful if you lift heavy and can't meet your protein goal from canteen food. However, on a student budget, 2 boiled eggs (৳30) or roasted peanuts (৳15) offer excellent high-protein value for much less Tk."
            }
            text.contains("egg") -> {
                "One boiled egg contains about 6g of protein and 75 kcal. Having 2 eggs with breakfast or as an afternoon tong snack is the most cost-effective protein source for students in Dhaka."
            }
            text.contains("snack") || text.contains("healthy") -> {
                if (isVegetarian) {
                    "Seasonal fruit cups (৳25) from campus carts or roasted peanuts with tong cha (৳15) keep you energized without breaking your monthly student food pool."
                } else {
                    "Fresh seasonal fruit from campus carts (৳25) or 2 boiled eggs (৳30) provide sustained energy without wasting feast reserve money."
                }
            }
            else -> {
                "Balancing protein and carbohydrates within your daily allowance (b) keeps your nutrition high and your monthly savings intact. Let me know if you want to test a meal swap!"
            }
        }
    }

    private suspend fun executePlanChangeFlow(
        userText: String,
        currentSlots: List<MealSlot>,
        isVegetarian: Boolean,
        dateString: String,
        userId: String?,
        repository: NutriMateFirestoreRepository?,
        onStepGenerated: (ChatMessage) -> Unit
    ): ChatMessage {
        val lower = userText.lowercase()

        // Infer slot (breakfast, lunch, snacks, dinner)
        val targetSlot = when {
            lower.contains("breakfast") -> "breakfast"
            lower.contains("snack") -> "snacks"
            lower.contains("dinner") -> "dinner"
            else -> "lunch" // Default slot if unspecified e.g. "I want biriyani"
        }

        // Infer search query
        val query = when {
            lower.contains("kacchi") || lower.contains("biriyani") || lower.contains("biryani") -> "biriyani"
            lower.contains("khichuri") -> "khichuri"
            lower.contains("paratha") -> "paratha"
            lower.contains("fish") -> "fish"
            lower.contains("thali") || lower.contains("veg") -> "veg"
            lower.contains("tehari") -> "tehari"
            lower.contains("fruit") -> "fruit"
            lower.contains("cheap") || lower.contains("save") -> ""
            else -> userText.replace("i want", "").replace("swap my", "").replace("for lunch", "").replace("for dinner", "").trim()
        }

        delay(350)

        // Step 1: search_menu
        val searchResults = toolsGatekeeper.searchMenu(query, targetSlot)
        val searchBadge = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.FUNCTION_CALL,
            content = "search_menu(query=\"$query\", slot=\"$targetSlot\")",
            toolName = "search_menu",
            toolArgs = "query=\"$query\", slot=\"$targetSlot\"",
            toolResult = if (searchResults.isNotEmpty()) "Found ${searchResults.size} items: ${searchResults.first().name} (৳${searchResults.first().priceTk})" else "No items found"
        )
        onStepGenerated(searchBadge)

        if (searchResults.isEmpty()) {
            delay(400)
            val alternatives = toolsGatekeeper.getAlternatives(targetSlot)
            val altBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "get_alternatives(slot=\"$targetSlot\")",
                toolName = "get_alternatives",
                toolArgs = "slot=\"$targetSlot\"",
                toolResult = "${alternatives.size} alternatives: ${alternatives.joinToString { "${it.name} (৳${it.priceTk})" }}"
            )
            onStepGenerated(altBadge)

            val reply = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ASSISTANT,
                content = "I couldn't find \"$query\" for $targetSlot. How about ${alternatives.firstOrNull()?.name} (৳${alternatives.firstOrNull()?.priceTk}) instead?"
            )
            onStepGenerated(reply)
            return reply
        }

        val chosenItem = searchResults.first()
        delay(400)

        // Step 2: check(slot, item_id)
        val checkRes = toolsGatekeeper.check(targetSlot, chosenItem.itemId)
        val checkBadge = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.FUNCTION_CALL,
            content = "check(slot=\"$targetSlot\", item_id=\"${chosenItem.itemId}\")",
            toolName = "check",
            toolArgs = "slot=\"$targetSlot\", item=\"${chosenItem.itemId}\"",
            toolResult = if (checkRes.ok) "ok: true, price=৳${checkRes.priceTk}, allowance=৳${checkRes.slotBudgetTk}" else "ok: false, reason=over_budget, short_by=৳${String.format("%.2f", checkRes.shortByTk ?: 0.0)}"
        )
        onStepGenerated(checkBadge)

        delay(400)

        if (checkRes.ok && checkRes.checkToken != null) {
            // Fits within budget: call update(check_token) directly
            val updateRes = toolsGatekeeper.update(checkRes.checkToken, currentSlots, userId, repository)
            val updateBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "update(token=\"${checkRes.checkToken.take(8)}...\")",
                toolName = "update",
                toolArgs = "token=\"${checkRes.checkToken.take(8)}...\"",
                toolResult = "status: applied"
            )
            onStepGenerated(updateBadge)

            delay(350)
            val finalReply = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ASSISTANT,
                content = "Swapped $targetSlot to ${chosenItem.name} at ${chosenItem.place} for ৳${chosenItem.priceTk}. It fits within your daily allowance!"
            )
            onStepGenerated(finalReply)
            return finalReply

        } else {
            // Over budget: call find_funding(slot, item_id)
            val fundingRes = toolsGatekeeper.findFunding(targetSlot, chosenItem.itemId)
            val fundingBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "find_funding(slot=\"$targetSlot\", item_id=\"${chosenItem.itemId}\")",
                toolName = "find_funding",
                toolArgs = "slot=\"$targetSlot\", item=\"${chosenItem.itemId}\"",
                toolResult = if (fundingRes.options.isNotEmpty()) "option: ${fundingRes.options.first().summary}" else "no funding options"
            )
            onStepGenerated(fundingBadge)

            if (fundingRes.options.isEmpty()) {
                val reply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "${chosenItem.name} is ৳${chosenItem.priceTk}, exceeding your allowance. All feast reserves and buffers are exhausted."
                )
                onStepGenerated(reply)
                return reply
            }

            val chosenOption = fundingRes.options.first()
            delay(400)

            // Step 4: Call update(token)
            val updateRes = toolsGatekeeper.update(chosenOption.token, currentSlots, userId, repository)
            val updateBadge = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.FUNCTION_CALL,
                content = "update(token=\"${chosenOption.token.take(8)}...\")",
                toolName = "update",
                toolArgs = "token=\"${chosenOption.token.take(8)}...\"",
                toolResult = "status: ${updateRes.status}"
            )
            onStepGenerated(updateBadge)

            delay(400)

            return if (updateRes.status == "needs_confirmation") {
                val feastName = chosenOption.feastTitle ?: "Friday's feast"
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "${chosenItem.name} is ৳${chosenItem.priceTk} and today's $targetSlot allowance is ৳${checkRes.slotBudgetTk}. I can cover the ৳${String.format("%.2f", chosenOption.shortfallTk)} gap by cancelling $feastName. You'd lose the feast. Tap Confirm if you want to go ahead.",
                    tradeoffToken = chosenOption.token,
                    isPendingConfirmation = true
                )
                onStepGenerated(assistantMsg)
                assistantMsg
            } else {
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = updateRes.message ?: "Plan updated successfully!"
                )
                onStepGenerated(assistantMsg)
                assistantMsg
            }
        }
    }
}
