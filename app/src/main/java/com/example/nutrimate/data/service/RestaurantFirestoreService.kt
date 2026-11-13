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
            isPartner = false,
            discountPercentage = 0,
            discountTag = "",
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
            isPartner = false,
            discountPercentage = 0,
            discountTag = "",
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
            isPartner = false,
            discountPercentage = 0,
            discountTag = "",
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
            isPartner = false,
            discountPercentage = 0,
            discountTag = "",
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
            isPartner = false,
            discountPercentage = 0,
            discountTag = "",
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
            isPartner = false,
            discountPercentage = 0,
            discountTag = "",
            menu = listOf(
                mapOf("id" to "ck_1", "name" to "BRACU Student Rice Bowl", "priceTk" to 75, "kcal" to 710, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "ck_2", "name" to "Paratha Omelette Combo", "priceTk" to 35, "kcal" to 450, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "ck_3", "name" to "Egg Khichuri & Salad", "priceTk" to 45, "kcal" to 600, "slot" to "dinner", "isVegetarian" to false)
            )
        ),

        // RUET, Rajshahi
        RestaurantEntity(
            id = "rest_ruet_kalai_ruti",
            name = "Kalai Ruti Ghar (Kazla)",
            latitude = 24.3642,
            longitude = 88.6295,
            address = "Kazla Gate, RUET, Rajshahi",
            cuisine = "Traditional Kalai Ruti",
            rating = 4.8,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "rkr_1", "name" to "Kalai Ruti with Begun Bhorta", "priceTk" to 25, "kcal" to 380, "slot" to "breakfast", "isVegetarian" to true),
                mapOf("id" to "rkr_2", "name" to "Kalai Ruti with Duck Bhuna", "priceTk" to 75, "kcal" to 740, "slot" to "dinner", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_ruet_talaimari",
            name = "Talaimari Student Hotel",
            latitude = 24.3621,
            longitude = 88.6258,
            address = "Talaimari Mor, Rajshahi",
            cuisine = "Rice & Curry",
            rating = 4.5,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "rth_1", "name" to "Rajshahi Chicken Rice Meal", "priceTk" to 65, "kcal" to 670, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "rth_2", "name" to "Rajshahi Samucha & Tea", "priceTk" to 15, "kcal" to 240, "slot" to "snacks", "isVegetarian" to true)
            )
        ),

        // CUET, Chittagong
        RestaurantEntity(
            id = "rest_cuet_mezbani",
            name = "Chittagong Mezbani House",
            latitude = 22.4618,
            longitude = 91.9722,
            address = "Pahartoli Bazar, CUET Gate, Chittagong",
            cuisine = "Mezbani Beef & Rice",
            rating = 4.9,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "cmz_1", "name" to "Chittagong Mezbani Beef Platter", "priceTk" to 115, "kcal" to 820, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "cmz_2", "name" to "Kala Bhuna with Rice", "priceTk" to 95, "kcal" to 760, "slot" to "dinner", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_cuet_pahartoli",
            name = "Pahartoli Student Cafe",
            latitude = 22.4596,
            longitude = 91.9704,
            address = "Kaptai Road, CUET, Chittagong",
            cuisine = "Breakfast & Snacks",
            rating = 4.5,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "cps_1", "name" to "Paratha, Dim & Milk Tea", "priceTk" to 30, "kcal" to 430, "slot" to "breakfast", "isVegetarian" to false)
            )
        ),

        // CU, Chittagong
        RestaurantEntity(
            id = "rest_cu_jhupri",
            name = "CU Jhupri Student Canteen",
            latitude = 22.4720,
            longitude = 91.7885,
            address = "Arts Jhupri, University of Chittagong",
            cuisine = "Campus Dining",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "cuj_1", "name" to "Luchi & Boot Dal", "priceTk" to 25, "kcal" to 390, "slot" to "breakfast", "isVegetarian" to true),
                mapOf("id" to "cuj_2", "name" to "Jhupri Special Chicken Rice", "priceTk" to 60, "kcal" to 640, "slot" to "lunch", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_cu_gate1",
            name = "Gate 1 Mezbani Hotel",
            latitude = 22.4695,
            longitude = 91.7845,
            address = "1 No Gate, Hathazari, Chittagong",
            cuisine = "Local Meals",
            rating = 4.7,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "cug_1", "name" to "Chittagong Beef Tehari", "priceTk" to 65, "kcal" to 730, "slot" to "dinner", "isVegetarian" to false)
            )
        ),

        // RU, Rajshahi
        RestaurantEntity(
            id = "rest_ru_tukitaki",
            name = "Tukitaki Chattar Cafe",
            latitude = 24.3712,
            longitude = 88.6371,
            address = "Tukitaki Chattar, University of Rajshahi",
            cuisine = "Student Snacks",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "rut_1", "name" to "Kalai Ruti & Morich Bhorta", "priceTk" to 25, "kcal" to 370, "slot" to "breakfast", "isVegetarian" to true),
                mapOf("id" to "rut_2", "name" to "Tukitaki Cha & Shingara", "priceTk" to 15, "kcal" to 230, "slot" to "snacks", "isVegetarian" to true)
            )
        ),
        RestaurantEntity(
            id = "rest_ru_station",
            name = "RU Station View Hotel",
            latitude = 24.3689,
            longitude = 88.6345,
            address = "Station Bazar Gate, Rajshahi",
            cuisine = "Rice & Meat",
            rating = 4.5,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "rus_1", "name" to "Chicken Khichuri Platter", "priceTk" to 65, "kcal" to 680, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "rus_2", "name" to "RU Hall Special Beef Meal", "priceTk" to 70, "kcal" to 740, "slot" to "dinner", "isVegetarian" to false)
            )
        ),

        // SUST, Sylhet
        RestaurantEntity(
            id = "rest_sust_shatkora",
            name = "Sylhet Shatkora Restora",
            latitude = 24.9165,
            longitude = 91.8330,
            address = "Akhalia Mor, SUST Gate, Sylhet",
            cuisine = "Sylheti Cuisine",
            rating = 4.8,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "ssr_1", "name" to "Shatkora Beef Rice Bowl", "priceTk" to 95, "kcal" to 770, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "ssr_2", "name" to "Akhni Polao & Egg", "priceTk" to 75, "kcal" to 710, "slot" to "dinner", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_sust_canteen",
            name = "SUST Central Canteen",
            latitude = 24.9182,
            longitude = 91.8315,
            address = "SUST Campus Canteen, Sylhet",
            cuisine = "Campus Meals",
            rating = 4.5,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "scc_1", "name" to "Chicken Khichuri with Salad", "priceTk" to 60, "kcal" to 690, "slot" to "dinner", "isVegetarian" to false),
                mapOf("id" to "scc_2", "name" to "Paratha, Dim Omelette & Sylheti Tea", "priceTk" to 30, "kcal" to 420, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "scc_3", "name" to "Chana Chotpoti & Lemon Cha", "priceTk" to 25, "kcal" to 260, "slot" to "snacks", "isVegetarian" to true)
            )
        ),

        // Uttara, Dhaka
        RestaurantEntity(
            id = "rest_uttara_canteen",
            name = "Uttara Student Canteen",
            latitude = 23.8768,
            longitude = 90.3802,
            address = "Sector 10, Uttara, Dhaka",
            cuisine = "Bangladeshi & Rice",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "usc_1", "name" to "Paratha, Dim Bhaji & Cha", "priceTk" to 35, "kcal" to 440, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "usc_2", "name" to "Chicken Khichuri Platter", "priceTk" to 70, "kcal" to 690, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "usc_3", "name" to "Singara (2 pcs) + Cha", "priceTk" to 20, "kcal" to 270, "slot" to "snacks", "isVegetarian" to true)
            )
        ),
        RestaurantEntity(
            id = "rest_uttara_kacchi",
            name = "Uttara Kacchi Palace",
            latitude = 23.8745,
            longitude = 90.3788,
            address = "Sector 7, Uttara, Dhaka",
            cuisine = "Biriyani",
            rating = 4.7,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "ukp_1", "name" to "Tehari with Salad", "priceTk" to 55, "kcal" to 740, "slot" to "dinner", "isVegetarian" to false)
            )
        ),

        // JU, Savar
        RestaurantEntity(
            id = "rest_ju_bottola",
            name = "Bottola Student Khichuri",
            latitude = 23.8824,
            longitude = 90.2681,
            address = "Bottola Market, JU, Savar",
            cuisine = "Traditional Rice & Vorta",
            rating = 4.8,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "jb_1", "name" to "Bottola Khichuri & Egg", "priceTk" to 30, "kcal" to 410, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "jb_2", "name" to "Bottola 5 Vortas with Rice", "priceTk" to 40, "kcal" to 540, "slot" to "lunch", "isVegetarian" to true),
                mapOf("id" to "jb_3", "name" to "Chicken Bhuna & Rice", "priceTk" to 60, "kcal" to 660, "slot" to "dinner", "isVegetarian" to false)
            )
        ),

        // JnU, Old Dhaka
        RestaurantEntity(
            id = "rest_jnu_bismillah",
            name = "Bismillah Kabab Ghar",
            latitude = 23.7082,
            longitude = 90.4095,
            address = "Victoria Park, Old Dhaka",
            cuisine = "Old Dhaka Chaap & Naan",
            rating = 4.7,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "jbg_1", "name" to "Old Dhaka Beef Tehari", "priceTk" to 60, "kcal" to 750, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "jbg_2", "name" to "Beef Chaap with Rumali Ruti", "priceTk" to 75, "kcal" to 780, "slot" to "dinner", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_jnu_beauty_lassi",
            name = "Beauty Lassi & Faluda",
            latitude = 23.7094,
            longitude = 90.4112,
            address = "Johnson Road, Old Dhaka",
            cuisine = "Heritage Desserts & Cha",
            rating = 4.9,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "jbl_1", "name" to "Beauty Special Lassi", "priceTk" to 35, "kcal" to 290, "slot" to "snacks", "isVegetarian" to true)
            )
        ),

        // BAU, Mymensingh
        RestaurantEntity(
            id = "rest_bau_jabbar",
            name = "Jabbar Mor Student Hotel",
            latitude = 24.7255,
            longitude = 90.4360,
            address = "Jabbar Mor, BAU, Mymensingh",
            cuisine = "Fresh Dairy & Meals",
            rating = 4.7,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "bjm_1", "name" to "Ruti with Fresh BAU Cow Milk", "priceTk" to 30, "kcal" to 410, "slot" to "breakfast", "isVegetarian" to true),
                mapOf("id" to "bjm_2", "name" to "Malaichai & Shingara", "priceTk" to 20, "kcal" to 260, "slot" to "snacks", "isVegetarian" to true)
            )
        ),
        RestaurantEntity(
            id = "rest_bau_dining",
            name = "BAU Central Dining",
            latitude = 24.7268,
            longitude = 90.4339,
            address = "BAU Central Campus, Mymensingh",
            cuisine = "Hall Dining",
            rating = 4.5,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "bcd_1", "name" to "Fresh Pangas Fish Curry & Rice", "priceTk" to 50, "kcal" to 620, "slot" to "lunch", "isVegetarian" to false),
                mapOf("id" to "bcd_2", "name" to "Chicken Khichuri Platter", "priceTk" to 55, "kcal" to 690, "slot" to "dinner", "isVegetarian" to false)
            )
        ),

        // BRUR, Rangpur
        RestaurantEntity(
            id = "rest_brur_park_mor",
            name = "Park Mor Student Restora",
            latitude = 25.7195,
            longitude = 89.2612,
            address = "Park Mor, BRUR, Rangpur",
            cuisine = "North Bengal Cuisine",
            rating = 4.6,
            isPartner = true,
            discountPercentage = 15,
            discountTag = "−15% Student OFF",
            menu = listOf(
                mapOf("id" to "bpm_1", "name" to "Dim Paratha & Spiced Tea", "priceTk" to 28, "kcal" to 390, "slot" to "breakfast", "isVegetarian" to false),
                mapOf("id" to "bpm_2", "name" to "North Bengal Beef Rice Platter", "priceTk" to 70, "kcal" to 710, "slot" to "lunch", "isVegetarian" to false)
            )
        ),
        RestaurantEntity(
            id = "rest_brur_cafe",
            name = "BRUR Central Cafe",
            latitude = 25.7208,
            longitude = 89.2600,
            address = "BRUR Campus, Rangpur",
            cuisine = "Snacks & Meals",
            rating = 4.5,
            isPartner = true,
            discountPercentage = 10,
            discountTag = "−10% Student OFF",
            menu = listOf(
                mapOf("id" to "bcc_1", "name" to "Egg Khichuri with Mixed Pickle", "priceTk" to 40, "kcal" to 590, "slot" to "dinner", "isVegetarian" to false)
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
                val snapshot = collectionRef.get().await()
                val existingIds = snapshot.documents.map { it.id }.toSet()
                val sampleNonPartner = snapshot.documents.find { it.id == "rest_kuet_canteen" }
                val needsPartnerMigration = sampleNonPartner != null && sampleNonPartner.getBoolean("isPartner") == true
                val hasMissingCampusRestaurants = DEFAULT_RESTAURANTS.any { it.id !in existingIds }
                if (!snapshot.isEmpty && !needsPartnerMigration && !hasMissingCampusRestaurants) {
                    Log.d(TAG, "'restaurants' collection already up-to-date (${snapshot.size()} docs), skipping seed.")
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
                val firestoreEntities = if (!snapshot.isEmpty) {
                    snapshot.toObjects(RestaurantEntity::class.java)
                } else {
                    emptyList()
                }
                val mergedEntities = mergeWithDefaultRestaurants(firestoreEntities)

                mergedEntities.map { restaurant ->
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
            val firestoreEntities = if (!snapshot.isEmpty) {
                snapshot.toObjects(RestaurantEntity::class.java)
            } else {
                emptyList()
            }
            val mergedEntities = mergeWithDefaultRestaurants(firestoreEntities)

            mergedEntities.map { restaurant ->
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

    private fun mergeWithDefaultRestaurants(firestoreEntities: List<RestaurantEntity>): List<RestaurantEntity> {
        if (firestoreEntities.isEmpty()) return DEFAULT_RESTAURANTS
        val defaultsById = DEFAULT_RESTAURANTS.associateBy { it.id }
        val enrichedFirestore = firestoreEntities.map { doc ->
            val fallback = defaultsById[doc.id]
            if (fallback != null && ((doc.latitude == 0.0 && doc.longitude == 0.0) || doc.menu.isEmpty())) {
                doc.copy(
                    latitude = if (doc.latitude == 0.0) fallback.latitude else doc.latitude,
                    longitude = if (doc.longitude == 0.0) fallback.longitude else doc.longitude,
                    address = doc.address.ifBlank { fallback.address },
                    menu = doc.menu.ifEmpty { fallback.menu }
                )
            } else {
                doc
            }
        }
        val presentIds = enrichedFirestore.map { it.id }.toSet()
        return enrichedFirestore + DEFAULT_RESTAURANTS.filter { it.id !in presentIds }
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
                        areaName = rest.address,
                        latitude = rest.latitude,
                        longitude = rest.longitude
                    )
                )
            }
        }
        return result
    }
}
