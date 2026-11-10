package com.example.nutrimate.data.repository

import android.util.Log
import com.example.nutrimate.data.model.CentralFoodEntity
import com.example.nutrimate.data.model.MealItemEntity
import com.example.nutrimate.data.model.PartnerRestaurantEntity
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
    val restaurantService: com.example.nutrimate.data.service.RestaurantFirestoreService =
        com.example.nutrimate.data.service.RestaurantFirestoreService(db)

    fun observeNearbyRestaurants(userLat: Double, userLon: Double, radiusKm: Double = 15.0) =
        restaurantService.observeNearbyRestaurants(userLat, userLon, radiusKm)

    suspend fun getNearbyRestaurants(userLat: Double, userLon: Double, radiusKm: Double = 15.0) =
        restaurantService.getNearbyRestaurants(userLat, userLon, radiusKm)

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

    fun observeCentralFoodsByArea(areaId: String): Flow<List<CentralFoodEntity>> {
        return db.collection("central_foods")
            .whereEqualTo("areaId", areaId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CentralFoodEntity::class.java)
            }
    }

    fun observePartnerRestaurants(areaId: String? = null): Flow<List<PartnerRestaurantEntity>> {
        val query = if (areaId.isNullOrBlank()) {
            db.collection("partner_restaurants")
        } else {
            db.collection("partner_restaurants").whereEqualTo("areaId", areaId)
        }
        return query.snapshots().map { snapshot ->
            snapshot.toObjects(PartnerRestaurantEntity::class.java)
        }
    }

    /**
     * Seeds nearby partner restaurants and central meals for a specific campus area into Cloud Firestore.
     */
    suspend fun seedAreaRestaurantsAndFoods(area: com.example.nutrimate.data.location.CampusArea) {
        try {
            // 1. Seed partner restaurants
            for (restaurant in area.defaultRestaurants) {
                val data = hashMapOf<String, Any>(
                    "id" to restaurant.id,
                    "name" to restaurant.name,
                    "areaId" to restaurant.areaId,
                    "areaName" to restaurant.areaName,
                    "cuisine" to restaurant.cuisine,
                    "discountPercentage" to restaurant.discountPercentage,
                    "discountTag" to restaurant.discountTag
                )
                db.collection("partner_restaurants").document(restaurant.id).set(data).await()
            }

            // 2. Seed central foods
            for (meal in area.defaultMeals) {
                val data = hashMapOf<String, Any>(
                    "id" to meal.id,
                    "name" to meal.name,
                    "place" to meal.place,
                    "priceTk" to meal.priceTk,
                    "pricePaisa" to meal.pricePaisa,
                    "kcal" to meal.kcal,
                    "slot" to meal.slot,
                    "isPartner" to meal.isPartner,
                    "discountTag" to meal.discountTag,
                    "areaId" to meal.areaId,
                    "areaName" to meal.areaName
                )
                db.collection("central_foods").document(meal.id).set(data).await()
            }
            Log.d(TAG, "Successfully seeded ${area.defaultRestaurants.size} restaurants & ${area.defaultMeals.size} meals for ${area.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding area data for ${area.name}", e)
        }
    }

    /**
     * Synchronizes a user's 4 daily meal slots with the top authentic meals from the selected campus hub.
     */
    suspend fun syncUserMealsWithArea(userId: String, area: com.example.nutrimate.data.location.CampusArea) {
        try {
            val slots = listOf("breakfast", "lunch", "snacks", "dinner")
            val emojis = mapOf("breakfast" to "🍳", "lunch" to "🍛", "snacks" to "🥪", "dinner" to "🍲")
            val limits = mapOf("breakfast" to 11, "lunch" to 16, "snacks" to 19, "dinner" to 24)

            for (slot in slots) {
                val topMeal = area.defaultMeals.find { it.slot.equals(slot, ignoreCase = true) } ?: continue
                val entity = MealItemEntity(
                    id = slot,
                    name = slot.replaceFirstChar { it.uppercase() },
                    emoji = emojis[slot] ?: "🍲",
                    budget = topMeal.priceTk,
                    targetKcal = topMeal.kcal,
                    activeHourLimit = limits[slot] ?: 24,
                    selectedPlace = topMeal.place,
                    selectedTitle = topMeal.name,
                    selectedPrice = topMeal.priceTk,
                    isPartner = topMeal.isPartner,
                    discountTag = topMeal.discountTag
                )
                saveMeal(userId, entity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user meals with area ${area.name}", e)
        }
    }

    suspend fun seedInitialDataIfEmpty(
        userId: String,
        displayName: String,
        area: com.example.nutrimate.data.location.CampusArea = com.example.nutrimate.data.location.CampusLocationManager.getDefaultArea()
    ) {
        try {
            // Seed the top-level restaurants collection with coordinates and menus
            restaurantService.seedRestaurantsIfEmpty()

            // Seed the area's central foods and partner restaurants
            seedAreaRestaurantsAndFoods(area)

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
                    university = area.universities.firstOrNull() ?: "BUET",
                    monthlyBudget = 7000,
                    totalSpentThisMonth = 0,
                    totalSavedFromDiscounts = 0,
                    unallocatedPaisa = 0L,
                    onMessPlan = false,
                    dailyCalorieGoal = 2310
                )
                saveUserProfile(initialProfile)

                // Sync initial meal slots with the selected area
                syncUserMealsWithArea(userId, area)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding initial user data in Firestore", e)
        }
    }
}
