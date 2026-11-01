package com.example.nutrimate.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfileEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val city: String = "",
    val age: Int = 20,
    val heightCm: Int = 170,
    val weightKg: Int = 60,
    val isVegetarian: Boolean = false,
    val allowLocation: Boolean = true,
    val goesToGym: Boolean = true,
    val isStudent: Boolean = true,
    val university: String = "BUET",
    val monthlyBudget: Int = 6000,
    val totalSpentThisMonth: Int = 0,
    val totalSavedFromDiscounts: Int = 0,
    val unallocatedPaisa: Long = 0L,
    val onMessPlan: Boolean = false,
    val dailyCalorieGoal: Int = 2310,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MealItemEntity(
    @DocumentId val docId: String = "",
    val id: String = "",
    val name: String = "",
    val emoji: String = "🍲",
    val budget: Int = 50,
    val targetKcal: Int = 500,
    val activeHourLimit: Int = 24,
    val selectedPlace: String = "",
    val selectedTitle: String = "",
    val selectedPrice: Int = 0,
    val isPartner: Boolean = false,
    val discountTag: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PaymentRecordEntity(
    @DocumentId val docId: String = "",
    val id: String = "",
    val trxId: String = "",
    val vendorName: String = "",
    val billAmount: Int = 0,
    val discountAmount: Int = 0,
    val netPayable: Int = 0,
    val paymentMethod: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CentralFoodEntity(
    @DocumentId val id: String = "",
    val name: String = "",
    val restaurant: String = "",
    val pricePaisa: Long = 0L,
    val calories: Int = 0,
    val mealSlot: String = "lunch",
    val isPartner: Boolean = false,
    val discountTag: String = ""
)
