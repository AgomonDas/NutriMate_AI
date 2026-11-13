package com.example.nutrimate

import com.example.nutrimate.data.feast.NutriMateChatEngine
import com.example.nutrimate.data.feast.NutriMateOnDeviceRouter
import com.example.nutrimate.model.NutriMateUserContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutriMateAiRouterAndContextTest {

    private val router = NutriMateOnDeviceRouter()

    @Test
    fun greeting_routesToPersonalizedGemini() {
        val result = router.classify("Hello! How can you help me?")
        assertFalse(result.isPlanChange)
    }

    @Test
    fun nutritionQuestion_routesToPersonalizedGemini() {
        val result = router.classify("How much protein is in 2 boiled eggs and dal?")
        assertFalse(result.isPlanChange)
    }

    @Test
    fun recommendationQuestion_routesToPersonalizedGemini() {
        val result = router.classify("What should I eat for dinner under 80 tk?")
        assertFalse(result.isPlanChange)
    }

    @Test
    fun explicitSwapCommand_routesToDeterministicGatekeeper() {
        val result = router.classify("Swap my lunch to kacchi biriyani")
        assertTrue(result.isPlanChange)
        assertEquals("lunch", result.slot)
        assertEquals("kacchi biriyani", result.query)
    }

    @Test
    fun cheaperDinnerCommand_routesToDeterministicGatekeeper() {
        val result = router.classify("Cheaper dinner")
        assertTrue(result.isPlanChange)
        assertEquals("dinner", result.slot)
    }

    @Test
    fun userContext_serializesCompactValidJsonSafely() {
        val ctx = NutriMateUserContext(
            userName = "Arif \"Student\"",
            age = 21,
            userGoal = "Muscle building",
            dailyCalorieTarget = 2300,
            plannedCaloriesToday = 1650,
            remainingCaloriesToday = 650,
            monthlyBudgetTk = 6000,
            monthlySpentTk = 2800,
            remainingMonthlyBudgetTk = 3200,
            todaySlotAllowanceTotalTk = 200,
            unallocatedReserveTk = 150,
            dietaryPreferences = listOf("Bangladeshi campus food", "Non-vegetarian"),
            isStudent = true,
            campusArea = "BUET & Palashi",
            university = "BUET",
            onMessPlan = false,
            plannedMealsToday = listOf("Lunch: Chicken Rice (৳75, 720 kcal)"),
            slotAllowancesTk = mapOf("breakfast" to 40, "lunch" to 80),
            activeFeast = "Friday Kacchi (৳250)",
            nearbyPartnerDeals = listOf("Palashi Cafe (-15%, 350 m)"),
            dateString = "Thursday, Nov 12"
        )
        val json = ctx.toCompactJson()
        assertTrue(json.contains("\"daily_calorie_target\":2300"))
        assertTrue(json.contains("\"remaining_calories_today\":650"))
        assertTrue(json.contains("\"remaining_monthly_budget_bdt\":3200"))
        assertTrue(json.contains("Arif \\\"Student\\\""))
        assertFalse(json.contains("\n"))
    }

    @Test
    fun promptTemplate_usesNutrimateCopilotV1AndSafeInputMap() {
        assertEquals("nutrimate-copilot-v1", NutriMateChatEngine.PROMPT_TEMPLATE_ID)

        val ctx = NutriMateUserContext(
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
            slotAllowancesTk = mapOf("breakfast" to 35, "lunch" to 80),
            activeFeast = null,
            nearbyPartnerDeals = listOf("TSC Canteen (-10%, 200 m)"),
            dateString = "Friday, Nov 13"
        )

        val inputs = NutriMateChatEngine.buildTemplateInputs(
            userText = "How much protein in 2 boiled eggs?",
            userContext = ctx,
            historyText = "Student: Hi\nNutriMate: Hello!"
        )

        assertEquals("How much protein in 2 boiled eggs?", inputs["user_message"])
        assertEquals("How much protein in 2 boiled eggs?", inputs["prompt"])
        assertEquals("Nadia", inputs["user_name"])
        assertEquals(2000, inputs["daily_calorie_target"])
        assertTrue((inputs["user_context"] as String).contains("\"campus\":\"DU & TSC\""))
        assertEquals("Student: Hi\nNutriMate: Hello!", inputs["chat_history"])
        // Never reserve/overwrite SDK's {{history}} binding key inside inputs map
        assertFalse(inputs.containsKey("history"))
    }
}
