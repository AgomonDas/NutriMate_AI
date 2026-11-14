package com.example.nutrimate

import com.example.nutrimate.data.feast.FeastBudgetGatekeeper
import com.example.nutrimate.data.feast.NutriMateAiToolsGatekeeper
import com.example.nutrimate.data.feast.NutriMateChatEngine
import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.mcp.McpToolRegistry
import com.example.nutrimate.data.mcp.NutriMateTurnToolContext
import com.example.nutrimate.data.mcp.RemoteMcpClient
import com.example.nutrimate.data.mcp.buildNutriMateLocalMcpTools
import com.example.nutrimate.model.MealOption
import com.example.nutrimate.model.MealSlot
import com.example.nutrimate.model.NutriMateUserContext
import com.google.firebase.ai.type.FunctionCallPart
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutriMateAiRouterAndContextTest {

    private fun sampleUserContext(): NutriMateUserContext = NutriMateUserContext(
        userName = "Nadia",
        age = 20,
        userGoal = "Balanced maintenance",
        dailyCalorieTarget = 2000,
        plannedCaloriesToday = 1100,
        remainingCaloriesToday = 900,
        monthlyBudgetTk = 5500,
        monthlySpentTk = 2100,
        remainingMonthlyBudgetTk = 3400,
        todaySlotAllowanceTotalTk = 180,
        unallocatedReserveTk = 120,
        dietaryPreferences = listOf("Bangladeshi campus food"),
        isStudent = true,
        campusArea = "DU & TSC",
        university = "Dhaka University",
        onMessPlan = false,
        plannedMealsToday = listOf("Breakfast: Ruti & Bhaji (৳30, 320 kcal)"),
        slotAllowancesTk = mapOf("breakfast" to 35, "lunch" to 80, "snacks" to 30, "dinner" to 55),
        activeFeast = "Friday Feast #1 (৳360)",
        nearbyPartnerDeals = listOf("TSC Canteen (-10%, 200 m)"),
        dateString = "Friday, Nov 13"
    )

    private fun sampleSlots(): List<MealSlot> = listOf(
        MealSlot(
            id = "lunch",
            name = "Lunch",
            emoji = "🍛",
            budget = 80,
            targetKcal = 650,
            activeHourLimit = 16,
            options = listOf(
                MealOption(place = "Hall Mess", title = "Rice, dal, fish", priceStr = "৳60", priceNum = 60)
            )
        )
    )

    @Test
    fun userContext_serializesCompactValidJsonSafely() {
        val ctx = sampleUserContext().copy(userName = "Arif \"Student\"")
        val json = ctx.toCompactJson()
        assertTrue(json.contains("\"daily_calorie_target\":2000"))
        assertTrue(json.contains("\"remaining_calories_today\":900"))
        assertTrue(json.contains("\"remaining_monthly_budget_bdt\":3400"))
        assertTrue(json.contains("Arif \\\"Student\\\""))
        assertFalse(json.contains("\n"))
    }

    @Test
    fun agenticEngine_usesGemini35FlashLiteAndRegisters7McpTools() {
        assertEquals("gemini-3.5-flash-lite", NutriMateChatEngine.MODEL_NAME)

        val feastGatekeeper = FeastBudgetGatekeeper(null)
        val toolsGatekeeper = NutriMateAiToolsGatekeeper(feastGatekeeper)
        val engine = NutriMateChatEngine(toolsGatekeeper)

        val names = engine.toolRegistry.getRegisteredToolNames()
        assertEquals(7, names.size)
        assertTrue(names.contains("get_user_context"))
        assertTrue(names.contains("search_campus_menu"))
        assertTrue(names.contains("get_nearby_restaurants"))
        assertTrue(names.contains("check_meal_budget"))
        assertTrue(names.contains("find_funding_options"))
        assertTrue(names.contains("propose_meal_swap"))
        assertTrue(names.contains("log_manual_meal"))
    }

    @Test
    fun proposeMealSwapTool_stagesConfirmationWithoutMutatingBudget() = runBlocking {
        val feastGatekeeper = FeastBudgetGatekeeper(null)
        val toolsGatekeeper = NutriMateAiToolsGatekeeper(feastGatekeeper)
        val initialState = feastGatekeeper.budgetState.value
        val turnContext = NutriMateTurnToolContext(
            userContext = sampleUserContext(),
            currentSlots = sampleSlots(),
            nearbyRestaurants = emptyList(),
            campusArea = CampusLocationManager.getDefaultArea()
        )
        val registry = McpToolRegistry().apply {
            registerTools(buildNutriMateLocalMcpTools(toolsGatekeeper) { turnContext })
        }

        // 1. Search for biryani (tests spelling tolerance biryani -> biriyani)
        val searchRecord = registry.executeCall(
            FunctionCallPart(
                name = "search_campus_menu",
                args = mapOf(
                    "query" to JsonPrimitive("biryani"),
                    "slot" to JsonPrimitive("lunch")
                )
            )
        )
        assertFalse(searchRecord.isError)
        assertTrue(searchRecord.resultSummary.contains("Kacchi Biriyani"))

        // 2. Propose swap -> must stage pending confirmation and NOT mutate ledger
        val proposeRecord = registry.executeCall(
            FunctionCallPart(
                name = "propose_meal_swap",
                args = mapOf(
                    "slot" to JsonPrimitive("lunch"),
                    "item_id" to JsonPrimitive("kacchi_biriyani")
                )
            )
        )
        assertFalse(proposeRecord.isError)
        assertEquals(initialState, feastGatekeeper.budgetState.value)

        val pending = turnContext.pendingSwapProposal
        assertNotNull(pending)
        assertEquals("lunch", pending?.slotId)
        assertEquals("Kacchi Biriyani", pending?.itemName)
        assertEquals(120, pending?.priceTk)

        // 3. Confirming applies the proposal into FeastBudgetGatekeeper
        val applied = toolsGatekeeper.confirmTradeoff(
            token = pending!!.token,
            currentSlots = sampleSlots(),
            userId = null,
            repository = null
        )
        assertNotNull(applied)
        assertEquals(12_000L, feastGatekeeper.budgetState.value.mealAllowancesPaisa["lunch"])
    }

    @Test
    fun remoteMcpClient_parsesToolsListAndSchemasAndToolCalls() {
        val req = RemoteMcpClient.buildJsonRpcRequest(
            id = "req-1",
            method = "tools/list",
            params = buildJsonObject {}
        )
        assertEquals("2.0", req["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals("tools/list", req["method"]?.jsonPrimitive?.content)

        val toolsListResponse = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "req-1")
            put(
                "result",
                buildJsonObject {
                    put(
                        "tools",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("name", "lookup_nutrition_facts")
                                    put("description", "Fetches detailed macro data for a dish")
                                    put(
                                        "inputSchema",
                                        buildJsonObject {
                                            put("type", "object")
                                            put(
                                                "properties",
                                                buildJsonObject {
                                                    put(
                                                        "dish",
                                                        buildJsonObject {
                                                            put("type", "string")
                                                            put("description", "Dish name")
                                                        }
                                                    )
                                                    put(
                                                        "servings",
                                                        buildJsonObject {
                                                            put("type", "integer")
                                                        }
                                                    )
                                                }
                                            )
                                            put("required", buildJsonArray { add(JsonPrimitive("dish")) })
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }

        val descriptors = RemoteMcpClient.parseToolsListResponse(toolsListResponse)
        assertEquals(1, descriptors.size)
        assertEquals("lookup_nutrition_facts", descriptors.first().name)

        val (params, optional) = RemoteMcpClient.extractParametersFromInputSchema(descriptors.first().inputSchema)
        assertEquals(2, params.size)
        assertEquals(listOf("servings"), optional)

        val callResponse = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "req-2")
            put(
                "result",
                buildJsonObject {
                    put("isError", false)
                    put(
                        "content",
                        buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", "2 eggs: 12g protein, 140 kcal") })
                        }
                    )
                }
            )
        }

        val result = RemoteMcpClient.parseToolCallResponse(callResponse)
        assertFalse(result.isError)
        assertTrue(result.displaySummary.contains("12g protein"))
        assertTrue(result.payload.containsKey("text"))
    }

    @Test
    fun functionResponseContent_usesUserRoleForGemini() = runBlocking {
        val feastGatekeeper = FeastBudgetGatekeeper(null)
        val toolsGatekeeper = NutriMateAiToolsGatekeeper(feastGatekeeper)
        val turnContext = NutriMateTurnToolContext(
            userContext = sampleUserContext(),
            currentSlots = sampleSlots(),
            nearbyRestaurants = emptyList(),
            campusArea = CampusLocationManager.getDefaultArea()
        )
        val registry = McpToolRegistry().apply {
            registerTools(buildNutriMateLocalMcpTools(toolsGatekeeper) { turnContext })
        }

        val record = registry.executeCall(
            FunctionCallPart(name = "get_user_context", args = emptyMap())
        )
        val responseContent = com.google.firebase.ai.type.Content(
            role = "user",
            parts = listOf(record.responsePart)
        )
        assertEquals("user", responseContent.role)
        assertEquals(1, responseContent.parts.size)
    }
}
