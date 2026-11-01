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
    suspend fun processUserMessage(
        userText: String,
        currentSlots: List<MealSlot>,
        isVegetarian: Boolean,
        dateString: String,
        userId: String?,
        repository: NutriMateFirestoreRepository?,
        onStepGenerated: (ChatMessage) -> Unit
    ): ChatMessage {
        val chat = chatSession ?: model.startChat().also { chatSession = it }

        var pendingTradeoffToken: String? = null
        var promptContent = content("user") { text(userText) }

        var iterations = 0
        val maxIterations = 6
        var lastAssistantText: String? = null

        try {
            while (iterations < maxIterations) {
                iterations++
                val response = withTimeout(60_000L) {
                    chat.sendMessage(promptContent)
                }

                val functionCalls = response.functionCalls
                if (functionCalls.isEmpty()) {
                    lastAssistantText = response.text
                    break
                }

                val functionResponseParts = mutableListOf<FunctionResponsePart>()

                for (call in functionCalls) {
                    val callName = call.name
                    val args = call.args

                    when (callName) {
                        "search_menu" -> {
                            val query = args["query"]?.jsonPrimitive?.contentOrNull ?: ""
                            val slot = args["slot"]?.jsonPrimitive?.contentOrNull ?: "lunch"
                            val maxPrice = args["max_price_tk"]?.jsonPrimitive?.intOrNull

                            val results = toolsGatekeeper.searchMenu(query, slot, maxPrice)

                            val badge = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                sender = MessageSender.FUNCTION_CALL,
                                content = "search_menu(query=\"$query\", slot=\"$slot\")",
                                toolName = "search_menu",
                                toolArgs = "query=\"$query\", slot=\"$slot\"",
                                toolResult = if (results.isNotEmpty()) {
                                    "Found ${results.size} items: ${results.first().name} (৳${results.first().priceTk})"
                                } else {
                                    "No items found"
                                }
                            )
                            onStepGenerated(badge)

                            val responseJson = buildJsonObject {
                                put("items", buildJsonArray {
                                    for (item in results) {
                                        add(buildJsonObject {
                                            put("item_id", item.itemId)
                                            put("name", item.name)
                                            put("place", item.place)
                                            put("price_tk", item.priceTk)
                                            put("kcal", item.kcal)
                                        })
                                    }
                                })
                            }
                            functionResponseParts.add(FunctionResponsePart("search_menu", responseJson))
                        }

                        "check" -> {
                            val slot = args["slot"]?.jsonPrimitive?.contentOrNull ?: "lunch"
                            val itemId = args["item_id"]?.jsonPrimitive?.contentOrNull ?: ""

                            val checkRes = toolsGatekeeper.check(slot, itemId)

                            val badge = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                sender = MessageSender.FUNCTION_CALL,
                                content = "check(slot=\"$slot\", item_id=\"$itemId\")",
                                toolName = "check",
                                toolArgs = "slot=\"$slot\", item_id=\"$itemId\"",
                                toolResult = if (checkRes.ok) {
                                    "ok: true, price=৳${checkRes.priceTk}, allowance=৳${checkRes.slotBudgetTk}"
                                } else {
                                    "ok: false, reason=over_budget, short_by=৳${String.format("%.2f", checkRes.shortByTk ?: 0.0)}"
                                }
                            )
                            onStepGenerated(badge)

                            val responseJson = buildJsonObject {
                                put("ok", checkRes.ok)
                                put("price_tk", checkRes.priceTk)
                                put("slot_budget_tk", checkRes.slotBudgetTk)
                                put("kcal", checkRes.kcal)
                                checkRes.checkToken?.let { put("check_token", it) }
                                checkRes.reason?.let { put("reason", it) }
                                checkRes.shortByTk?.let { put("short_by_tk", it) }
                            }
                            functionResponseParts.add(FunctionResponsePart("check", responseJson))
                        }

                        "find_funding" -> {
                            val slot = args["slot"]?.jsonPrimitive?.contentOrNull ?: "lunch"
                            val itemId = args["item_id"]?.jsonPrimitive?.contentOrNull ?: ""

                            val fundingRes = toolsGatekeeper.findFunding(slot, itemId)

                            val badge = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                sender = MessageSender.FUNCTION_CALL,
                                content = "find_funding(slot=\"$slot\", item_id=\"$itemId\")",
                                toolName = "find_funding",
                                toolArgs = "slot=\"$slot\", item_id=\"$itemId\"",
                                toolResult = if (fundingRes.options.isNotEmpty()) {
                                    "option: ${fundingRes.options.first().summary}"
                                } else {
                                    "no funding available"
                                }
                            )
                            onStepGenerated(badge)

                            val responseJson = buildJsonObject {
                                put("options", buildJsonArray {
                                    for (opt in fundingRes.options) {
                                        add(buildJsonObject {
                                            put("option_id", opt.optionId)
                                            put("summary", opt.summary)
                                            put("shortfall_tk", opt.shortfallTk)
                                            opt.feastTitle?.let { put("feast_title", it) }
                                            put("token", opt.token)
                                        })
                                    }
                                })
                            }
                            functionResponseParts.add(FunctionResponsePart("find_funding", responseJson))
                        }

                        "update" -> {
                            val token = args["token"]?.jsonPrimitive?.contentOrNull ?: ""

                            val updateRes = toolsGatekeeper.update(token, currentSlots, userId, repository)

                            val badge = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                sender = MessageSender.FUNCTION_CALL,
                                content = "update(token=\"${token.take(8)}...\")",
                                toolName = "update",
                                toolArgs = "token=\"${token.take(8)}...\"",
                                toolResult = "status: ${updateRes.status}"
                            )
                            onStepGenerated(badge)

                            if (updateRes.status == "needs_confirmation") {
                                pendingTradeoffToken = token
                            }

                            val responseJson = buildJsonObject {
                                put("status", updateRes.status)
                                updateRes.message?.let { put("message", it) }
                                updateRes.reason?.let { put("reason", it) }
                            }
                            functionResponseParts.add(FunctionResponsePart("update", responseJson))
                        }

                        "get_alternatives" -> {
                            val slot = args["slot"]?.jsonPrimitive?.contentOrNull ?: "lunch"
                            val maxPrice = args["max_price_tk"]?.jsonPrimitive?.intOrNull

                            val alts = toolsGatekeeper.getAlternatives(slot, maxPrice)

                            val badge = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                sender = MessageSender.FUNCTION_CALL,
                                content = "get_alternatives(slot=\"$slot\")",
                                toolName = "get_alternatives",
                                toolArgs = "slot=\"$slot\"",
                                toolResult = "${alts.size} alternatives: ${alts.joinToString { "${it.name} (৳${it.priceTk})" }}"
                            )
                            onStepGenerated(badge)

                            val responseJson = buildJsonObject {
                                put("alternatives", buildJsonArray {
                                    for (alt in alts) {
                                        add(buildJsonObject {
                                            put("item_id", alt.itemId)
                                            put("name", alt.name)
                                            put("place", alt.place)
                                            put("price_tk", alt.priceTk)
                                            put("kcal", alt.kcal)
                                        })
                                    }
                                })
                            }
                            functionResponseParts.add(FunctionResponsePart("get_alternatives", responseJson))
                        }
                    }
                }

                // Send function execution outputs back to Gemini
                promptContent = Content(
                    role = "user",
                    parts = functionResponseParts
                )
            }

            val finalContent = lastAssistantText?.takeIf { it.isNotBlank() } ?: "I've reviewed your meal plan."
            val assistantMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ASSISTANT,
                content = finalContent,
                tradeoffToken = pendingTradeoffToken,
                isPendingConfirmation = pendingTradeoffToken != null
            )
            onStepGenerated(assistantMsg)
            return assistantMsg

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini generation timed out", e)
            chatSession = null
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI generation failed via Firebase AI Logic", e)
            chatSession = null
            throw e
        }
    }
}
