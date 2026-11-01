package com.example.nutrimate.data.repository

import android.util.Log
import com.example.nutrimate.data.model.CentralFoodEntity
import com.example.nutrimate.data.model.MealItemEntity
import com.example.nutrimate.data.model.PaymentRecordEntity
import com.example.nutrimate.data.model.UserProfileEntity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TAG = "NutriMateRepo"

class NutriMateFirestoreRepository(
    private val db: FirebaseFirestore
) {

    fun observeUserProfile(userId: String): Flow<UserProfileEntity?> {
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfileEntity::class.java)
                } else {
                    null
                }
            }
    }

    suspend fun saveUserProfile(profile: UserProfileEntity) {
        val userDoc = db.collection("users").document(profile.userId)
        val snapshot = userDoc.get().await()

        val data = hashMapOf<String, Any>(
            "userId" to profile.userId,
            "name" to profile.name,
            "phone" to profile.phone,
            "city" to profile.city,
            "age" to profile.age,
            "heightCm" to profile.heightCm,
            "weightKg" to profile.weightKg,
            "isVegetarian" to profile.isVegetarian,
            "allowLocation" to profile.allowLocation,
            "goesToGym" to profile.goesToGym,
            "isStudent" to profile.isStudent,
            "university" to profile.university,
            "monthlyBudget" to profile.monthlyBudget,
            "totalSpentThisMonth" to profile.totalSpentThisMonth,
            "totalSavedFromDiscounts" to profile.totalSavedFromDiscounts,
            "unallocatedPaisa" to profile.unallocatedPaisa,
            "onMessPlan" to profile.onMessPlan,
            "dailyCalorieGoal" to profile.dailyCalorieGoal
        )

        if (!snapshot.exists()) {
            data["createdAt"] = FieldValue.serverTimestamp()
            userDoc.set(data).await()
        } else {
            data["updatedAt"] = FieldValue.serverTimestamp()
            userDoc.update(data).await()
        }
    }

    fun observeMeals(userId: String): Flow<List<MealItemEntity>> {
        return db.collection("users").document(userId).collection("meals")
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(MealItemEntity::class.java)
            }
    }

    suspend fun saveMeal(userId: String, meal: MealItemEntity) {
        val mealDoc = db.collection("users").document(userId).collection("meals").document(meal.id)
        val snapshot = mealDoc.get().await()

        val data = hashMapOf<String, Any>(
            "id" to meal.id,
            "name" to meal.name,
            "emoji" to meal.emoji,
            "budget" to meal.budget,
            "targetKcal" to meal.targetKcal,
            "activeHourLimit" to meal.activeHourLimit,
            "selectedPlace" to meal.selectedPlace,
            "selectedTitle" to meal.selectedTitle,
            "selectedPrice" to meal.selectedPrice,
            "isPartner" to meal.isPartner,
            "discountTag" to meal.discountTag
        )

        if (!snapshot.exists()) {
            data["createdAt"] = FieldValue.serverTimestamp()
            mealDoc.set(data).await()
        } else {
            data["updatedAt"] = FieldValue.serverTimestamp()
            mealDoc.update(data).await()
        }
    }

    fun observePayments(userId: String): Flow<List<PaymentRecordEntity>> {
        return db.collection("users").document(userId).collection("payments")
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(PaymentRecordEntity::class.java)
            }
    }

    suspend fun recordPayment(userId: String, payment: PaymentRecordEntity) {
        val payDoc = db.collection("users").document(userId).collection("payments").document(payment.id)
        val data = hashMapOf<String, Any>(
            "id" to payment.id,
            "vendorName" to payment.vendorName,
            "billAmount" to payment.billAmount,
            "discountAmount" to payment.discountAmount,
            "netPayable" to payment.netPayable,
            "paymentMethod" to payment.paymentMethod,
            "createdAt" to FieldValue.serverTimestamp()
        )
        payDoc.set(data).await()

        // Also update user's monthly spent and discount totals
        val userDoc = db.collection("users").document(userId)
        userDoc.update(
            "totalSpentThisMonth", FieldValue.increment(payment.netPayable.toLong()),
            "totalSavedFromDiscounts", FieldValue.increment(payment.discountAmount.toLong()),
            "updatedAt", FieldValue.serverTimestamp()
        ).await()
    }

    fun observeCentralFoods(): Flow<List<CentralFoodEntity>> {
        return db.collection("central_foods")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CentralFoodEntity::class.java)
            }
    }

    suspend fun seedInitialDataIfEmpty(userId: String, displayName: String) {
        try {
            val userDoc = db.collection("users").document(userId).get().await()
            if (!userDoc.exists()) {
                val initialProfile = UserProfileEntity(
                    userId = userId,
                    name = if (displayName.isNotBlank()) displayName else "Dhruv",
                    phone = "+880 1712 345678",
                    city = "Dhaka",
                    age = 21,
                    heightCm = 172,
                    weightKg = 64,
                    isVegetarian = false,
                    allowLocation = true,
                    goesToGym = true,
                    isStudent = true,
                    university = "BUET",
                    monthlyBudget = 7000,
                    totalSpentThisMonth = 0,
                    totalSavedFromDiscounts = 0,
                    unallocatedPaisa = 0L,
                    onMessPlan = false,
                    dailyCalorieGoal = 2310
                )
                saveUserProfile(initialProfile)

                // Seed default meal slots
                val initialMeals = listOf(
                    MealItemEntity(
                        id = "breakfast",
                        name = "Breakfast",
                        emoji = "🍳",
                        budget = 35,
                        targetKcal = 450,
                        activeHourLimit = 11,
                        selectedPlace = "Dhaka Paratha House",
                        selectedTitle = "Paratha, egg, tea",
                        selectedPrice = 35,
                        isPartner = true,
                        discountTag = "−10%"
                    ),
                    MealItemEntity(
                        id = "lunch",
                        name = "Lunch",
                        emoji = "🍛",
                        budget = 81,
                        targetKcal = 800,
                        activeHourLimit = 16,
                        selectedPlace = "Star Kabab & Biriyani",
                        selectedTitle = "Chicken rice bowl",
                        selectedPrice = 75,
                        isPartner = true,
                        discountTag = "−10%"
                    ),
                    MealItemEntity(
                        id = "snacks",
                        name = "Snacks",
                        emoji = "🥪",
                        budget = 30,
                        targetKcal = 260,
                        activeHourLimit = 19,
                        selectedPlace = "Fuchka Corner",
                        selectedTitle = "Fuchka + chotpoti",
                        selectedPrice = 30,
                        isPartner = true,
                        discountTag = "−5%"
                    ),
                    MealItemEntity(
                        id = "dinner",
                        name = "Dinner",
                        emoji = "🍲",
                        budget = 51,
                        targetKcal = 800,
                        activeHourLimit = 24,
                        selectedPlace = "Biryani Bhaban",
                        selectedTitle = "Tehari + salad",
                        selectedPrice = 51,
                        isPartner = true,
                        discountTag = "−10%"
                    )
                )

                for (m in initialMeals) {
                    saveMeal(userId, m)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding initial user data in Firestore", e)
        }
    }
}
