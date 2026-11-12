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
    val onboardingCompleted: Boolean = false,
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
    @DocumentId val docId: String = "",
    val id: String = "",
    val name: String = "",
    val place: String = "",
    val priceTk: Int = 0,
    val pricePaisa: Long = 0L,
    val kcal: Int = 0,
    val slot: String = "lunch",
    val isPartner: Boolean = false,
    val discountTag: String = "",
    val areaId: String = "buet_palashi",
    val areaName: String = "BUET & Palashi"
)

@IgnoreExtraProperties
data class PartnerRestaurantEntity(
    @DocumentId val docId: String = "",
    val id: String = "",
    val name: String = "",
    val areaId: String = "buet_palashi",
    val areaName: String = "BUET & Palashi",
    val cuisine: String = "Bangladeshi & Biriyani",
    val discountPercentage: Int = 10,
    val discountTag: String = "−10% Student OFF",
    val rating: Double = 4.5,
    val popularDish: String = "Kacchi Biriyani"
)

@IgnoreExtraProperties
data class RestaurantMenuItem(
    val id: String = "",
    val name: String = "",
    val priceTk: Int = 0,
    val pricePaisa: Long = 0L,
    val kcal: Int = 0,
    val slot: String = "lunch",
    val isVegetarian: Boolean = false,
    val isAvailable: Boolean = true
)

@IgnoreExtraProperties
data class RestaurantEntity(
    @DocumentId val docId: String = "",
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val cuisine: String = "Bangladeshi",
    val rating: Double = 4.5,
    val isPartner: Boolean = false,
    val discountPercentage: Int = 0,
    val discountTag: String = "",
    val menu: List<Map<String, Any>> = emptyList()
)

data class RestaurantWithDistance(
    val restaurant: RestaurantEntity,
    val distanceKm: Double,
    val distanceFormatted: String
)
