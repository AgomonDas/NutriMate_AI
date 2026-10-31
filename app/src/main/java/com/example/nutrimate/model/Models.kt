package com.example.nutrimate.model

data class UserGoals(
    val phone: String = "+880 1712 345678",
    val otp: String = "482910",
    val name: String = "Dhruv",
    val age: Int = 21,
    val city: String = "Dhaka",
    val heightCm: Int = 172,
    val weightKg: Int = 64,
    val isVegetarian: Boolean = false,
    val allowLocation: Boolean = true,
    val goesToGym: Boolean = true,
    val isStudent: Boolean = true,
    val university: String = "BUET",
    val monthlyBudget: Int = 6000,
    val monthlySpent: Int = 2800,
    val onMessPlan: Boolean = false,
    val dailyCalorieGoal: Int = 2310
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
    val discountTag: String = ""
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
    val timestamp: Long = System.currentTimeMillis()
)

data class MonthlyDaySpend(
    val day: Int,
    val spent: Int,
    val isCurrentDay: Boolean = false
)

enum class AppNavTab {
    TODAY,
    CHAT,
    SCAN,
    MONTH,
    ME
}
