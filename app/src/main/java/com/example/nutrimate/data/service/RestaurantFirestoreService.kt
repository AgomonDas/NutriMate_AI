package com.example.nutrimate.data.service

import android.util.Log
import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.model.CentralFoodEntity
import com.example.nutrimate.data.model.RestaurantEntity
import com.example.nutrimate.data.model.RestaurantWithDistance
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.*

private const val TAG = "RestaurantService"

class RestaurantFirestoreService(
    private val customDb: FirebaseFirestore? = null
) {
    private val db: FirebaseFirestore
        get() = customDb ?: FirebaseFirestore.getInstance()

    /**
     * Default curated list of student-friendly restaurants in Dhaka across major university hubs
     * with exact geographic coordinates and complete menus.
     */
    val DEFAULT_RESTAURANTS: List<RestaurantEntity> = listOf(
        // KUET & Teligati, Khulna
        RestaurantEntity(
            id = "rest_kuet_chuijhal",
            name = "Chuijhal Hotel & Restora (KUET Gate)",
            latitude = 22.8980,
            longitude = 89.5050,
            address = "Fulbarigate, near KUET Main Gate, Khulna",
            cuisine = "Khulna Chuijhal & Bengali Rice",
            rating = 4.8,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "kj_1", "name" to "Chuijhal Beef with Rice", "priceTk" to 110, "kcal" to 780, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "kj_2", "name" to "Khulna Duck Bhuna with Ruti", "priceTk" to 90, "kcal" to 720, "slot" to "dinner", "isVegetarian" to false),
                mapOf("id" to "kj_3", "name" to "Dal & Aloobhorta Thali", "priceTk" to 40, "kcal" to 510, "slot" to "lunch", "isVegetarian" to true),
                mapOf("id" to "kj_4", "name" to "Egg Khichuri with Salad", "priceTk" to 45, "kcal" to 610, "slot" to "dinner", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_kuet_teligati_tong",
            name = "Teligati Student Cafe & Tong",
            latitude = 22.9020,
            longitude = 89.5010,
            address = "Teligati Mor, KUET, Khulna",
            cuisine = "Breakfast & Student Snacks",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "kt_1", "name" to "Paratha, Dim Omelette & Cha", "priceTk" to 30, "kcal" to 420, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "kt_2", "name" to "Khulna Dal Puri (2 pcs) & Tea", "priceTk" to 20, "kcal" to 340, "slot" to "breakfast", "isVegetarian" to true),
                mapOf("id" to "kt_3", "name" to "Shingara (2 pcs) & Spiced Milk Tea", "priceTk" to 15, "kcal" to 250, "slot" to "snacks", "isVegetarian" to true),
                mapOf("id" to "kt_4", "name" to "Chotpoti & Fuchka Platter", "priceTk" to 30, "kcal" to 280, "slot" to "snacks", "isVegetarian" to true)
            )
        ),
        RestaurantEntity(
            id = "rest_kuet_canteen",
            name = "KUET Central Canteen",
            latitude = 22.9010,
            longitude = 89.5030,
            address = "Inside KUET Campus, Khulna",
            cuisine = "Campus Daily Meals",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "kc_1", "name" to "Chicken Curry Rice Platter", "priceTk" to 60, "kcal" to 650, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "kc_2", "name" to "Egg Curry, Rice & Dal", "priceTk" to 40, "kcal" to 560, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "kc_3", "name" to "Mixed Veg Khichuri", "priceTk" to 35, "kcal" to 490, "slot" to "dinner", "isVegetarian" to true)
            )
        ),

        // BUET & Palashi Hub
        RestaurantEntity(
            id = "rest_star_kabab_palashi",
            name = "Star Kabab & Biriyani (Palashi)",
            latitude = 23.7275,
            longitude = 90.3920,
            address = "Nazimuddin Road, near Palashi, Dhaka",
            cuisine = "Bangladeshi & Biriyani",
            rating = 4.7,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "sk_p1", "name" to "Chicken Rice Bowl", "priceTk" to 75, "kcal" to 720, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "sk_p2", "name" to "Kacchi Biriyani", "priceTk" to 120, "kcal" to 850, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "sk_p3", "name" to "Grilled Chicken (1/4) & Naan", "priceTk" to 90, "kcal" to 810, "slot" to "dinner", "isVegetarian" to false),
                mapOf("id" to "sk_p4", "name" to "Morog Polao", "priceTk" to 110, "kcal" to 800, "slot" to "lunch", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_dhaka_paratha_house",
            name = "Dhaka Paratha House",
            latitude = 23.7258,
            longitude = 90.3942,
            address = "Bakshibazar Mor, Dhaka",
            cuisine = "Traditional Breakfast",
            rating = 4.5,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "dph_1", "name" to "Paratha, Dim Bhaji & Cha", "priceTk" to 35, "kcal" to 450, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "dph_2", "name" to "Ruti & Mixed Vegetable", "priceTk" to 28, "kcal" to 310, "slot" to "breakfast", "isVegetarian" to true),
                mapOf("id" to "dph_3", "name" to "2 Boiled Eggs & Banana", "priceTk" to 30, "kcal" to 290, "slot" to "breakfast", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_palashi_tong_corner",
            name = "Palashi Tong & Cha Corner",
            latitude = 23.7266,
            longitude = 90.3925,
            address = "Palashi Chottor, BUET Gate",
            cuisine = "Street Food & Khichuri",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "pt_1", "name" to "Khichuri Dim Combo", "priceTk" to 38, "kcal" to 430, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "pt_2", "name" to "Fuchka + Chotpoti", "priceTk" to 30, "kcal" to 260, "slot" to "snacks", "isVegetarian" to true),
                mapOf("id" to "pt_3", "name" to "Hot Chicken Khichuri", "priceTk" to 75, "kcal" to 710, "slot" to "dinner", "isVegetarian" to false),
                mapOf("id" to "pt_4", "name" to "Roasted Peanuts & Lemon Cha", "priceTk" to 15, "kcal" to 180, "slot" to "snacks", "isVegetarian" to true)
            )
        ),
        RestaurantEntity(
            id = "rest_royal_biriyani",
            name = "Royal Biriyani House",
            latitude = 23.7240,
            longitude = 90.3890,
            address = "Lalbagh Fort Road, Old Dhaka",
            cuisine = "Old Dhaka Heritage Biriyani",
            rating = 4.8,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "rb_1", "name" to "Old Dhaka Beef Tehari", "priceTk" to 51, "kcal" to 780, "slot" to "dinner", "isVegetarian" to false),
                mapOf("id" to "rb_2", "name" to "Beef Bhuna Rice Platter", "priceTk" to 95, "kcal" to 820, "slot" to "lunch", "isVegetarian" to false)
            )
        ),

        // Dhaka University & Nilkhet Hub
        RestaurantEntity(
            id = "rest_madhur_canteen",
            name = "Madhur Canteen (DU)",
            latitude = 23.7335,
            longitude = 90.3920,
            address = "Arts Faculty, University of Dhaka",
            cuisine = "Campus Heritage Food",
            rating = 4.9,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "mc_1", "name" to "Luchi with Chhola Dal", "priceTk" to 30, "kcal" to 420, "slot" to "breakfast", "isVegetarian" to true),
                mapOf("id" to "mc_2", "name" to "TSC Shingara & Cha", "priceTk" to 15, "kcal" to 280, "slot" to "snacks", "isVegetarian" to true),
                mapOf("id" to "mc_3", "name" to "Chicken Curry, Rice & Dal", "priceTk" to 55, "kcal" to 640, "slot" to "lunch", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_nilkhet_baburchi",
            name = "Nilkhet Baburchi",
            latitude = 23.7328,
            longitude = 90.3860,
            address = "Nilkhet Mor, Baburchi Lane",
            cuisine = "Tehari & Khichuri",
            rating = 4.7,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "nb_1", "name" to "Beef Khichuri Special", "priceTk" to 80, "kcal" to 780, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "nb_2", "name" to "Nilkhet Kacchi Biriyani", "priceTk" to 115, "kcal" to 840, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "nb_3", "name" to "Nilkhet Beef Tehari", "priceTk" to 50, "kcal" to 750, "slot" to "dinner", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_shadow_cafe_tsc",
            name = "Shadow Cafe (TSC)",
            latitude = 23.7342,
            longitude = 90.3935,
            address = "TSC Compound, University of Dhaka",
            cuisine = "Student Cafe & Snacks",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "sc_1", "name" to "DU Canteen Dim Paratha", "priceTk" to 35, "kcal" to 440, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "sc_2", "name" to "Ruhi Fish Rice Platter", "priceTk" to 65, "kcal" to 660, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "sc_3", "name" to "Samucha & Cold Coffee", "priceTk" to 40, "kcal" to 320, "slot" to "snacks", "isVegetarian" to true)
            )
        ),

        // Dhanmondi Hub
        RestaurantEntity(
            id = "rest_kacchi_bhai_dhanmondi",
            name = "Kacchi Bhai Dhanmondi",
            latitude = 23.7465,
            longitude = 90.3750,
            address = "Road 27, Dhanmondi, Dhaka",
            cuisine = "Bashmati Kacchi & Borhani",
            rating = 4.8,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "kb_d1", "name" to "Bashmati Kacchi (Half)", "priceTk" to 130, "kcal" to 890, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "kb_d2", "name" to "Borhani Glass", "priceTk" to 40, "kcal" to 120, "slot" to "snacks", "isVegetarian" to true)
            )
        ),
        RestaurantEntity(
            id = "rest_tehari_ghar_sobhanbag",
            name = "Tehari Ghar Sobhanbag",
            latitude = 23.7510,
            longitude = 90.3762,
            address = "Mirpur Road, Sobhanbag, Dhanmondi",
            cuisine = "Old Dhaka Style Tehari",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "tg_1", "name" to "Beef Tehari with Borhani", "priceTk" to 70, "kcal" to 790, "slot" to "dinner", "isVegetarian" to false),
                mapOf("id" to "tg_2", "name" to "Morog Polao & Egg", "priceTk" to 90, "kcal" to 780, "slot" to "lunch", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_dhanmondi_27_tong",
            name = "Dhanmondi 27 Tong Plaza",
            latitude = 23.7470,
            longitude = 90.3735,
            address = "Road 27 (Old), Dhanmondi",
            cuisine = "Street Food & Fast Food",
            rating = 4.5,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "d27_1", "name" to "Dim Paratha & Special Cha", "priceTk" to 35, "kcal" to 440, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "d27_2", "name" to "Student Chicken Rice Meal", "priceTk" to 70, "kcal" to 680, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "d27_3", "name" to "Chicken Shawarma Wrap", "priceTk" to 60, "kcal" to 420, "slot" to "snacks", "isVegetarian" to false),
                mapOf("id" to "d27_4", "name" to "Egg Khichuri with Vorta", "priceTk" to 45, "kcal" to 610, "slot" to "dinner", "isVegetarian" to false)
            )
        ),

        // Banani Hub
        RestaurantEntity(
            id = "rest_banani_tehari_hub",
            name = "Banani Tehari Hub",
            latitude = 23.7925,
            longitude = 90.4048,
            address = "Road 11, Banani, Dhaka",
            cuisine = "Tehari & Biriyani",
            rating = 4.7,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "bth_1", "name" to "Banani Beef Tehari", "priceTk" to 65, "kcal" to 760, "slot" to "dinner", "isVegetarian" to false),
                mapOf("id" to "bth_2", "name" to "Chicken Biriyani Platter", "priceTk" to 95, "kcal" to 800, "slot" to "lunch", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_campus_khichuri_bracu",
            name = "Campus Khichuri Ghar (BRACU)",
            latitude = 23.7770,
            longitude = 90.4060,
            address = "Wireless Gate, Mohakhali / BRAC University",
            cuisine = "Student Rice Bowls & Khichuri",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "ck_1", "name" to "BRACU Student Rice Bowl", "priceTk" to 75, "kcal" to 710, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "ck_2", "name" to "Paratha Omelette Combo", "priceTk" to 35, "kcal" to 450, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "ck_3", "name" to "Egg Khichuri & Salad", "priceTk" to 45, "kcal" to 600, "slot" to "dinner", "isVegetarian" to false)
            )
        )
    )

    /**
     * Seeds the top-level 'restaurants' collection in Cloud Firestore if it has not been populated.
     */
    suspend fun seedRestaurantsIfEmpty(force: Boolean = false): Boolean {
        try {
            val collectionRef = db.collection("restaurants")
            if (!force) {
                val snapshot = collectionRef.limit(1).get().await()
                if (!snapshot.isEmpty) {
                    Log.d(TAG, "'restaurants' collection already contains data, skipping seed.")
                    return true
                }
            }

            Log.d(TAG, "Seeding ${DEFAULT_RESTAURANTS.size} restaurants to Firestore 'restaurants' collection...")
            for (restaurant in DEFAULT_RESTAURANTS) {
                val data = hashMapOf<String, Any>(
                    "id" to restaurant.id,
                    "name" to restaurant.name,
                    "latitude" to restaurant.latitude,
                    "longitude" to restaurant.longitude,
                    "address" to restaurant.address,
                    "cuisine" to restaurant.cuisine,
                    "rating" to restaurant.rating,
                    "isPartner" to restaurant.isPartner,
                    "discountPercentage" to restaurant.discountPercentage,
                    "discountTag" to restaurant.discountTag,
                    "menu" to restaurant.menu
                )
                collectionRef.document(restaurant.id).set(data).await()
            }
            Log.d(TAG, "Successfully seeded 'restaurants' collection.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed 'restaurants' collection in Firestore", e)
            return false
        }
    }

    /**
     * Calculates distance in kilometers between two geo-coordinates using the Haversine formula.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    /**
     * Formats distance into a clean string (e.g., "450 m" or "1.8 km").
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).roundToInt()} m"
        } else {
            String.format("%.1f km", distanceKm)
        }
    }

    /**
     * Observes all restaurants from Cloud Firestore and dynamically computes distance
     * relative to user's (latitude, longitude), filtering within max radius and sorting closest first.
     */
    fun observeNearbyRestaurants(
        userLat: Double,
        userLon: Double,
        radiusKm: Double = 15.0
    ): Flow<List<RestaurantWithDistance>> {
        return db.collection("restaurants")
            .snapshots()
            .map { snapshot ->
                val entities = if (!snapshot.isEmpty) {
                    snapshot.toObjects(RestaurantEntity::class.java)
                } else {
                    // Fall back to default in-memory list if offline or before initial seed
                    DEFAULT_RESTAURANTS
                }

                entities.map { restaurant ->
                    val distance = calculateDistanceKm(userLat, userLon, restaurant.latitude, restaurant.longitude)
                    RestaurantWithDistance(
                        restaurant = restaurant,
                        distanceKm = distance,
                        distanceFormatted = formatDistance(distance)
                    )
                }
                .filter { it.distanceKm <= radiusKm }
                .sortedBy { it.distanceKm }
            }
    }

    /**
     * One-shot asynchronous query for nearby restaurants within radius.
     */
    suspend fun getNearbyRestaurants(
        userLat: Double,
        userLon: Double,
        radiusKm: Double = 15.0
    ): List<RestaurantWithDistance> {
        return try {
            val snapshot = db.collection("restaurants").get().await()
            val entities = if (!snapshot.isEmpty) {
                snapshot.toObjects(RestaurantEntity::class.java)
            } else {
                DEFAULT_RESTAURANTS
            }

            entities.map { restaurant ->
                val distance = calculateDistanceKm(userLat, userLon, restaurant.latitude, restaurant.longitude)
                RestaurantWithDistance(
                    restaurant = restaurant,
                    distanceKm = distance,
                    distanceFormatted = formatDistance(distance)
                )
            }
            .filter { it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying nearby restaurants, using local fallback", e)
            DEFAULT_RESTAURANTS.map { restaurant ->
                val distance = calculateDistanceKm(userLat, userLon, restaurant.latitude, restaurant.longitude)
                RestaurantWithDistance(
                    restaurant = restaurant,
                    distanceKm = distance,
                    distanceFormatted = formatDistance(distance)
                )
            }
            .filter { it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
        }
    }

    /**
     * Flattens menu items of nearby restaurants into CentralFoodEntity list for AI tools and daily meal slots.
     */
    fun extractMealsFromNearbyRestaurants(
        nearby: List<RestaurantWithDistance>
    ): List<CentralFoodEntity> {
        val result = mutableListOf<CentralFoodEntity>()
        for (item in nearby) {
            val rest = item.restaurant
            for (m in rest.menu) {
                val name = m["name"] as? String ?: continue
                val priceTk = (m["priceTk"] as? Number)?.toInt() ?: 50
                val kcal = (m["kcal"] as? Number)?.toInt() ?: 500
                val slot = m["slot"] as? String ?: "lunch"
                val id = m["id"] as? String ?: "${rest.id}_${name.lowercase().replace(" ", "_")}"

                result.add(
                    CentralFoodEntity(
                        docId = id,
                        id = id,
                        name = name,
                        place = rest.name,
                        priceTk = priceTk,
                        pricePaisa = priceTk * 100L,
                        kcal = kcal,
                        slot = slot,
                        isPartner = rest.isPartner,
                        discountTag = rest.discountTag,
                        areaId = rest.id,
                        areaName = rest.address
                    )
                )
            }
        }
        return result
    }
}
