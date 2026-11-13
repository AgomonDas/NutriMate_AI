package com.example.nutrimate.model

data class UserGoals(
    val phone: String = "",
    val otp: String = "",
    val name: String = "",
    val age: Int = 21,
    val city: String = "",
    val heightCm: Int = 172,
    val weightKg: Int = 64,
    val isVegetarian: Boolean = false,
    val allowLocation: Boolean = true,
    val goesToGym: Boolean = true,
    val isStudent: Boolean = true,
    val university: String = "",
    val monthlyBudget: Int = 6000,
    val monthlySpent: Int = 2800,
    val onMessPlan: Boolean = false,
    val dailyCalorieGoal: Int = 2310,
    val isPhoneVerified: Boolean = false
) {
    fun calculateMifflinStJeor(): Int {
        // Mifflin-St Jeor formula for young adult male
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + 5
        val multiplier = if (goesToGym) 1.55 else 1.375
        return (bmr * multiplier).toInt()
    }
}

data class MealOption(
    val place: String,
    val title: String,
    val priceStr: String,
    val priceNum: Int,
    val isPartner: Boolean = false,
    val discountTag: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class MealSlot(
    val id: String,
    val name: String,
    val emoji: String,
    val budget: Int,
    val targetKcal: Int,
    val activeHourLimit: Int,
    val options: List<MealOption>,
    val selectedOptionIndex: Int = 0
) {
    val activeOption: MealOption
        get() = options.getOrElse(selectedOptionIndex) { options.first() }
}

enum class MessageSender {
    USER,
    ASSISTANT,
    FUNCTION_CALL
}

data class ChatMessage(
    val id: String,
    val sender: MessageSender,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val tradeoffToken: String? = null,
    val isPendingConfirmation: Boolean = false,
    val latencyMs: Long? = null,
    val firstTokenMs: Long? = null,
    val isLocalExecution: Boolean = false,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val retryPrompt: String? = null
)

data class NutriMateUserContext(
    val userName: String,
    val age: Int,
    val userGoal: String,
    val dailyCalorieTarget: Int,
    val plannedCaloriesToday: Int,
    val remainingCaloriesToday: Int,
    val monthlyBudgetTk: Int,
    val monthlySpentTk: Int,
    val remainingMonthlyBudgetTk: Int,
    val todaySlotAllowanceTotalTk: Int,
    val unallocatedReserveTk: Int,
    val dietaryPreferences: List<String>,
    val isStudent: Boolean,
    val campusArea: String,
    val university: String,
    val onMessPlan: Boolean,
    val plannedMealsToday: List<String>,
    val slotAllowancesTk: Map<String, Int>,
    val activeFeast: String?,
    val nearbyPartnerDeals: List<String>,
    val dateString: String
) {
    fun toCompactJson(): String {
        val prefsJson = dietaryPreferences.joinToString(",") { "\"${escapeJson(it)}\"" }
        val mealsJson = plannedMealsToday.joinToString(",") { "\"${escapeJson(it)}\"" }
        val dealsJson = nearbyPartnerDeals.take(4).joinToString(",") { "\"${escapeJson(it)}\"" }
        val allowancesJson = slotAllowancesTk.entries.joinToString(",") { (k, v) -> "\"${escapeJson(k)}\":$v" }
        val feastJson = activeFeast?.let { "\"${escapeJson(it)}\"" } ?: "null"
        return buildString {
            append("{")
            append("\"date\":\"${escapeJson(dateString)}\",")
            append("\"name\":\"${escapeJson(userName)}\",")
            append("\"age\":$age,")
            append("\"goal\":\"${escapeJson(userGoal)}\",")
            append("\"daily_calorie_target\":$dailyCalorieTarget,")
            append("\"planned_calories_today\":$plannedCaloriesToday,")
            append("\"remaining_calories_today\":$remainingCaloriesToday,")
            append("\"monthly_budget_bdt\":$monthlyBudgetTk,")
            append("\"monthly_spent_bdt\":$monthlySpentTk,")
            append("\"remaining_monthly_budget_bdt\":$remainingMonthlyBudgetTk,")
            append("\"today_allowance_bdt\":$todaySlotAllowanceTotalTk,")
            append("\"unallocated_reserve_bdt\":$unallocatedReserveTk,")
            append("\"slot_allowances_bdt\":{$allowancesJson},")
            append("\"dietary_preferences\":[$prefsJson],")
            append("\"on_mess_plan\":$onMessPlan,")
            append("\"campus\":\"${escapeJson(campusArea)}\",")
            append("\"university\":\"${escapeJson(university)}\",")
            append("\"planned_meals_today\":[$mealsJson],")
            append("\"upcoming_feast\":$feastJson,")
            append("\"nearby_partner_deals\":[$dealsJson]")
            append("}")
        }
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
        .replace("\r", " ")
}

data class MonthlyDaySpend(
    val day: Int,
    val spent: Int,
    val isCurrentDay: Boolean = false
)

enum class AppNavTab {
    HOME,
    TODAY,
    CHAT,
    SCAN,
    MONTH,
    ME
}
