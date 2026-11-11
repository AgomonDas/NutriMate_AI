package com.example.nutrimate.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.example.nutrimate.data.model.CentralFoodEntity
import com.example.nutrimate.data.model.PartnerRestaurantEntity
import kotlin.math.*

private const val TAG = "CampusLocationManager"

data class CampusArea(
    val id: String,
    val name: String,
    val shortName: String,
    val latitude: Double,
    val longitude: Double,
    val district: String = "Dhaka",
    val description: String,
    val universities: List<String>,
    val defaultRestaurants: List<PartnerRestaurantEntity>,
    val defaultMeals: List<CentralFoodEntity>
)

object CampusLocationManager {

    /**
     * 15 major University & Campus Hubs across Bangladesh
     */
    val CAMPUS_AREAS: List<CampusArea> = listOf(
        // 1. KUET, Khulna
        CampusArea(
            id = "kuet_khulna",
            name = "KUET, Khulna",
            shortName = "KUET, Khulna",
            latitude = 22.9006,
            longitude = 89.5024,
            district = "Khulna",
            description = "Teligati, Fulbarigate, Khan Jahan Ali & KUET Gate",
            universities = listOf("Khulna University of Engineering & Technology (KUET)", "Khulna University"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("kuet_chuijhal", "kuet_chuijhal", "Chuijhal Hotel & Restora", "kuet_khulna", "KUET, Khulna", "Khulna Chuijhal & Beef", 15, "−15% Student OFF", 4.8, "Chuijhal Beef Rice"),
                PartnerRestaurantEntity("kuet_teligati_tong", "kuet_teligati_tong", "Teligati Student Tong", "kuet_khulna", "KUET, Khulna", "Breakfast & Snacks", 10, "−10% Student OFF", 4.5, "Paratha, Dim Omelette"),
                PartnerRestaurantEntity("kuet_canteen", "kuet_canteen", "KUET Central Canteen", "kuet_khulna", "KUET, Khulna", "Campus Dining", 10, "−10% Student OFF", 4.6, "Chicken Curry Rice Bowl")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("kuet_b1", "kuet_b1", "Paratha, dim omelette & cha", "Teligati Student Tong", 30, 3000L, 420, "breakfast", true, "−10%", "kuet_khulna", "KUET, Khulna"),
                CentralFoodEntity("kuet_b2", "kuet_b2", "Khulna Dal Puri & Cha", "Teligati Student Tong", 20, 2000L, 340, "breakfast", false, "", "kuet_khulna", "KUET, Khulna"),
                CentralFoodEntity("kuet_l1", "kuet_l1", "Chuijhal Beef with Rice", "Chuijhal Hotel & Restora", 110, 11000L, 780, "lunch", true, "−15%", "kuet_khulna", "KUET, Khulna"),
                CentralFoodEntity("kuet_l2", "kuet_l2", "Chicken Curry Rice Platter", "KUET Central Canteen", 60, 6000L, 650, "lunch", true, "−10%", "kuet_khulna", "KUET, Khulna"),
                CentralFoodEntity("kuet_l3", "kuet_l3", "Egg Curry, Rice & Dal", "KUET Central Canteen", 40, 4000L, 560, "lunch", false, "", "kuet_khulna", "KUET, Khulna"),
                CentralFoodEntity("kuet_s1", "kuet_s1", "Shingara (2 pcs) & Milk Tea", "Teligati Student Tong", 15, 1500L, 250, "snacks", true, "−10%", "kuet_khulna", "KUET, Khulna"),
                CentralFoodEntity("kuet_s2", "kuet_s2", "Chotpoti & Fuchka Platter", "Campus Corner", 30, 3000L, 280, "snacks", false, "", "kuet_khulna", "KUET, Khulna"),
                CentralFoodEntity("kuet_d1", "kuet_d1", "Khulna Duck Bhuna with Ruti", "Chuijhal Hotel & Restora", 90, 9000L, 720, "dinner", true, "−15%", "kuet_khulna", "KUET, Khulna"),
                CentralFoodEntity("kuet_d2", "kuet_d2", "Dim Khichuri with Salad", "KUET Central Canteen", 45, 4500L, 620, "dinner", true, "−10%", "kuet_khulna", "KUET, Khulna")
            )
        ),

        // 2. BUET & Palashi, Dhaka
        CampusArea(
            id = "buet_palashi",
            name = "BUET & Palashi, Dhaka",
            shortName = "BUET, Dhaka",
            latitude = 23.7266,
            longitude = 90.3925,
            district = "Dhaka",
            description = "Palashi, Bakshibazar, Nazimuddin Road & Lalbagh",
            universities = listOf("BUET", "Dhaka Medical College"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("buet_star_kabab", "buet_star_kabab", "Star Kabab & Biriyani", "buet_palashi", "BUET, Dhaka", "Bangladeshi & Biriyani", 10, "−10% Student OFF", 4.7, "Kacchi Biriyani"),
                PartnerRestaurantEntity("buet_paratha_house", "buet_paratha_house", "Dhaka Paratha House", "buet_palashi", "BUET, Dhaka", "Breakfast & Tea", 10, "−10% Student OFF", 4.5, "Paratha, Dim Bhaji"),
                PartnerRestaurantEntity("buet_palashi_tong", "buet_palashi_tong", "Palashi Tong & Cha Corner", "buet_palashi", "BUET, Dhaka", "Street Food & Snacks", 15, "−15% Student OFF", 4.6, "Khichuri Dim Combo")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("buet_b1", "buet_b1", "Paratha, egg, tea", "Dhaka Paratha House", 35, 3500L, 450, "breakfast", true, "−10%", "buet_palashi", "BUET, Dhaka"),
                CentralFoodEntity("buet_l1", "buet_l1", "Chicken rice bowl", "Star Kabab & Biriyani", 75, 7500L, 720, "lunch", true, "−10%", "buet_palashi", "BUET, Dhaka"),
                CentralFoodEntity("buet_l2", "buet_l2", "Kacchi Biriyani", "Star Kabab & Biriyani", 120, 12000L, 850, "lunch", true, "−10%", "buet_palashi", "BUET, Dhaka"),
                CentralFoodEntity("buet_s1", "buet_s1", "Fuchka + chotpoti", "Palashi Tong & Cha Corner", 30, 3000L, 260, "snacks", true, "−15%", "buet_palashi", "BUET, Dhaka"),
                CentralFoodEntity("buet_d1", "buet_d1", "Old Dhaka Tehari + salad", "Star Kabab & Biriyani", 55, 5500L, 780, "dinner", true, "−10%", "buet_palashi", "BUET, Dhaka")
            )
        ),

        // 3. Dhaka University & Nilkhet, Dhaka
        CampusArea(
            id = "du_nilkhet",
            name = "Dhaka University & Nilkhet, Dhaka",
            shortName = "DU, Dhaka",
            latitude = 23.7340,
            longitude = 90.3928,
            district = "Dhaka",
            description = "TSC, Arts Faculty, Curzon Hall, Nilkhet Mor & Baburchi Lane",
            universities = listOf("University of Dhaka (DU)", "Eden College"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("du_madhur_canteen", "du_madhur_canteen", "Madhur Canteen (DU)", "du_nilkhet", "DU, Dhaka", "Campus Traditional", 15, "−15% Student OFF", 4.9, "Luchi & Chhola Dal"),
                PartnerRestaurantEntity("du_nilkhet_baburchi", "du_nilkhet_baburchi", "Nilkhet Baburchi", "du_nilkhet", "DU, Dhaka", "Tehari & Khichuri", 10, "−10% Student OFF", 4.7, "Beef Khichuri"),
                PartnerRestaurantEntity("du_shadow_cafe", "du_shadow_cafe", "Shadow Cafe (TSC)", "du_nilkhet", "DU, Dhaka", "Cafe & Snacks", 10, "−10% Student OFF", 4.6, "Cold Coffee & Samucha")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("du_b1", "du_b1", "Luchi with chhola dal", "Madhur Canteen (DU)", 30, 3000L, 420, "breakfast", true, "−15%", "du_nilkhet", "DU, Dhaka"),
                CentralFoodEntity("du_l1", "du_l1", "Beef Khichuri special", "Nilkhet Baburchi", 80, 8000L, 780, "lunch", true, "−10%", "du_nilkhet", "DU, Dhaka"),
                CentralFoodEntity("du_s1", "du_s1", "TSC Shingara & Cha", "Madhur Canteen (DU)", 15, 1500L, 280, "snacks", true, "−15%", "du_nilkhet", "DU, Dhaka"),
                CentralFoodEntity("du_d1", "du_d1", "Nilkhet Beef Tehari", "Nilkhet Baburchi", 50, 5000L, 750, "dinner", true, "−10%", "du_nilkhet", "DU, Dhaka")
            )
        ),

        // 4. RUET, Rajshahi
        CampusArea(
            id = "ruet_rajshahi",
            name = "RUET, Rajshahi",
            shortName = "RUET, Rajshahi",
            latitude = 24.3636,
            longitude = 88.6284,
            district = "Rajshahi",
            description = "Kazla, Talaimari, Kajla Gate & RUET Station",
            universities = listOf("Rajshahi University of Engineering & Technology (RUET)"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("ruet_kalai_ruti", "ruet_kalai_ruti", "Kalai Ruti Ghar (Kazla)", "ruet_rajshahi", "RUET, Rajshahi", "Traditional Kalai Ruti", 15, "−15% Student OFF", 4.8, "Kalai Ruti & Begun Bhorta"),
                PartnerRestaurantEntity("ruet_talaimari_hotel", "ruet_talaimari_hotel", "Talaimari Student Hotel", "ruet_rajshahi", "RUET, Rajshahi", "Rice & Meat", 10, "−10% Student OFF", 4.5, "Chicken Polao")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("ruet_b1", "ruet_b1", "Kalai ruti with begun bhorta", "Kalai Ruti Ghar (Kazla)", 25, 2500L, 380, "breakfast", true, "−15%", "ruet_rajshahi", "RUET, Rajshahi"),
                CentralFoodEntity("ruet_l1", "ruet_l1", "Rajshahi Chicken Rice Meal", "Talaimari Student Hotel", 65, 6500L, 670, "lunch", true, "−10%", "ruet_rajshahi", "RUET, Rajshahi"),
                CentralFoodEntity("ruet_s1", "ruet_s1", "Rajshahi Samucha & Tea", "Talaimari Student Hotel", 15, 1500L, 240, "snacks", false, "", "ruet_rajshahi", "RUET, Rajshahi"),
                CentralFoodEntity("ruet_d1", "ruet_d1", "Kalai Ruti with Duck Bhuna", "Kalai Ruti Ghar (Kazla)", 75, 7500L, 740, "dinner", true, "−15%", "ruet_rajshahi", "RUET, Rajshahi")
            )
        ),

        // 5. CUET, Chittagong
        CampusArea(
            id = "cuet_chittagong",
            name = "CUET, Chittagong",
            shortName = "CUET, Chittagong",
            latitude = 22.4601,
            longitude = 91.9710,
            district = "Chittagong",
            description = "Raozan, Pahartoli & Kaptai Road",
            universities = listOf("Chittagong University of Engineering & Technology (CUET)"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("cuet_mezbani", "cuet_mezbani", "Chittagong Mezbani House", "cuet_chittagong", "CUET, Chittagong", "Mezbani Beef", 15, "−15% Student OFF", 4.9, "Mezbani Gosht & Bhaat"),
                PartnerRestaurantEntity("cuet_pahartoli_hotel", "cuet_pahartoli_hotel", "Pahartoli Student Cafe", "cuet_chittagong", "CUET, Chittagong", "Breakfast & Snacks", 10, "−10% Student OFF", 4.5, "Paratha & Dal")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("cuet_b1", "cuet_b1", "Paratha, Dim & Milk Tea", "Pahartoli Student Cafe", 30, 3000L, 430, "breakfast", true, "−10%", "cuet_chittagong", "CUET, Chittagong"),
                CentralFoodEntity("cuet_l1", "cuet_l1", "Chittagong Mezbani Beef Platter", "Chittagong Mezbani House", 115, 11500L, 820, "lunch", true, "−15%", "cuet_chittagong", "CUET, Chittagong"),
                CentralFoodEntity("cuet_s1", "cuet_s1", "Shingara & Special Cha", "Pahartoli Student Cafe", 15, 1500L, 230, "snacks", false, "", "cuet_chittagong", "CUET, Chittagong"),
                CentralFoodEntity("cuet_d1", "cuet_d1", "Kala Bhuna with Rice", "Chittagong Mezbani House", 95, 9500L, 760, "dinner", true, "−15%", "cuet_chittagong", "CUET, Chittagong")
            )
        ),

        // 6. University of Chittagong (CU)
        CampusArea(
            id = "cu_chittagong",
            name = "University of Chittagong (CU)",
            shortName = "CU, Chittagong",
            latitude = 22.4716,
            longitude = 91.7877,
            district = "Chittagong",
            description = "Fatehpur, Hathazari, 1 No Gate, Shuttle Train Station",
            universities = listOf("University of Chittagong (CU)"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("cu_jhupri", "cu_jhupri", "CU Jhupri Student Canteen", "cu_chittagong", "CU, Chittagong", "Campus Dining", 10, "−10% Student OFF", 4.6, "Khichuri & Dim"),
                PartnerRestaurantEntity("cu_gate1_hotel", "cu_gate1_hotel", "Gate 1 Mezbani Hotel", "cu_chittagong", "CU, Chittagong", "Local Meals", 10, "−10% Student OFF", 4.7, "Beef Kala Bhuna")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("cu_b1", "cu_b1", "Luchi & Boot Dal", "CU Jhupri Student Canteen", 25, 2500L, 390, "breakfast", true, "−10%", "cu_chittagong", "CU, Chittagong"),
                CentralFoodEntity("cu_l1", "cu_l1", "Jhupri Special Chicken Rice", "CU Jhupri Student Canteen", 60, 6000L, 640, "lunch", true, "−10%", "cu_chittagong", "CU, Chittagong"),
                CentralFoodEntity("cu_s1", "cu_s1", "Shuttle Station Tea & Toast", "Campus Tong", 15, 1500L, 220, "snacks", false, "", "cu_chittagong", "CU, Chittagong"),
                CentralFoodEntity("cu_d1", "cu_d1", "Chittagong Beef Tehari", "Gate 1 Mezbani Hotel", 65, 6500L, 730, "dinner", true, "−10%", "cu_chittagong", "CU, Chittagong")
            )
        ),

        // 7. University of Rajshahi (RU)
        CampusArea(
            id = "ru_rajshahi",
            name = "University of Rajshahi (RU)",
            shortName = "RU, Rajshahi",
            latitude = 24.3707,
            longitude = 88.6367,
            district = "Rajshahi",
            description = "Motihar, Stadium Gate, Station Bazar & Tukitaki",
            universities = listOf("University of Rajshahi (RU)"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("ru_tukitaki", "ru_tukitaki", "Tukitaki Chattar Cafe", "ru_rajshahi", "RU, Rajshahi", "Student Snacks", 10, "−10% Student OFF", 4.6, "Special Shingara & Cha"),
                PartnerRestaurantEntity("ru_station_hotel", "ru_station_hotel", "RU Station View Hotel", "ru_rajshahi", "RU, Rajshahi", "Rice & Meat", 10, "−10% Student OFF", 4.5, "Beef Khichuri")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("ru_b1", "ru_b1", "Kalai Ruti & Morich Bhorta", "Tukitaki Chattar Cafe", 25, 2500L, 370, "breakfast", true, "−10%", "ru_rajshahi", "RU, Rajshahi"),
                CentralFoodEntity("ru_l1", "ru_l1", "Chicken Khichuri Platter", "RU Station View Hotel", 65, 6500L, 680, "lunch", true, "−10%", "ru_rajshahi", "RU, Rajshahi"),
                CentralFoodEntity("ru_s1", "ru_s1", "Tukitaki Cha & Shingara", "Tukitaki Chattar Cafe", 15, 1500L, 230, "snacks", true, "−10%", "ru_rajshahi", "RU, Rajshahi"),
                CentralFoodEntity("ru_d1", "ru_d1", "RU Hall Special Beef Meal", "RU Station View Hotel", 70, 7000L, 740, "dinner", true, "−10%", "ru_rajshahi", "RU, Rajshahi")
            )
        ),

        // 8. SUST, Sylhet
        CampusArea(
            id = "sust_sylhet",
            name = "SUST, Sylhet",
            shortName = "SUST, Sylhet",
            latitude = 24.9172,
            longitude = 91.8319,
            district = "Sylhet",
            description = "Kumargaon, Kiloro Road, Akhalia & SUST Gate",
            universities = listOf("Shahjalal University of Science & Technology (SUST)"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("sust_shatkora", "sust_shatkora", "Sylhet Shatkora Restora", "sust_sylhet", "SUST, Sylhet", "Sylheti Cuisine", 15, "−15% Student OFF", 4.8, "Shatkora Beef with Rice"),
                PartnerRestaurantEntity("sust_canteen", "sust_canteen", "SUST Central Canteen", "sust_sylhet", "SUST, Sylhet", "Campus Meals", 10, "−10% Student OFF", 4.5, "Chicken Polao")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("sust_b1", "sust_b1", "Sylheti Bakarkhani & Cha", "Campus Tong", 25, 2500L, 360, "breakfast", false, "", "sust_sylhet", "SUST, Sylhet"),
                CentralFoodEntity("sust_l1", "sust_l1", "Shatkora Beef Rice Bowl", "Sylhet Shatkora Restora", 95, 9500L, 770, "lunch", true, "−15%", "sust_sylhet", "SUST, Sylhet"),
                CentralFoodEntity("sust_s1", "sust_s1", "Seven Color Layer Tea & Samucha", "Campus Tong", 35, 3500L, 290, "snacks", false, "", "sust_sylhet", "SUST, Sylhet"),
                CentralFoodEntity("sust_d1", "sust_d1", "Chicken Khichuri with Salad", "SUST Central Canteen", 60, 6000L, 690, "dinner", true, "−10%", "sust_sylhet", "SUST, Sylhet")
            )
        ),

        // 9. Dhanmondi, Dhaka
        CampusArea(
            id = "dhanmondi",
            name = "Dhanmondi, Dhaka",
            shortName = "Dhanmondi, Dhaka",
            latitude = 23.7461,
            longitude = 90.3742,
            district = "Dhaka",
            description = "Road 27, Shimanto Square, Sobhanbag, Rangs Fortune",
            universities = listOf("UIU", "DIU", "Stamford", "ULAB", "State University"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("dhan_kacchi_bhai", "dhan_kacchi_bhai", "Kacchi Bhai Dhanmondi", "dhanmondi", "Dhanmondi, Dhaka", "Biriyani & Polao", 10, "−10% Student OFF", 4.8, "Bashmati Kacchi"),
                PartnerRestaurantEntity("dhan_tong_27", "dhan_tong_27", "Dhanmondi 27 Tong", "dhanmondi", "Dhanmondi, Dhaka", "Street Foods", 15, "−15% Student OFF", 4.5, "Cha & Egg Roll")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("dhan_b1", "dhan_b1", "Beef Nehari with Naan", "Star Kabab Dhanmondi", 45, 4500L, 520, "breakfast", true, "−10%", "dhanmondi", "Dhanmondi, Dhaka"),
                CentralFoodEntity("dhan_l1", "dhan_l1", "Student Chicken Rice Meal", "Dhanmondi 27 Tong", 70, 7000L, 680, "lunch", true, "−15%", "dhanmondi", "Dhanmondi, Dhaka"),
                CentralFoodEntity("dhan_l2", "dhan_l2", "Bashmati Kacchi (Half)", "Kacchi Bhai Dhanmondi", 130, 13000L, 890, "lunch", true, "−10%", "dhanmondi", "Dhanmondi, Dhaka"),
                CentralFoodEntity("dhan_s1", "dhan_s1", "Chicken Shawarma Wrap", "Dhanmondi 27 Tong", 60, 6000L, 420, "snacks", true, "−15%", "dhanmondi", "Dhanmondi, Dhaka"),
                CentralFoodEntity("dhan_d1", "dhan_d1", "Beef Tehari with Borhani", "Kacchi Bhai Dhanmondi", 70, 7000L, 790, "dinner", true, "−10%", "dhanmondi", "Dhanmondi, Dhaka")
            )
        ),

        // 10. Banani & Mohakhali, Dhaka
        CampusArea(
            id = "banani_mohakhali",
            name = "Banani & Mohakhali, Dhaka",
            shortName = "Banani, Dhaka",
            latitude = 23.7771,
            longitude = 90.4057,
            district = "Dhaka",
            description = "BRACU Campus Area, Mohakhali Wireless & Banani 11",
            universities = listOf("BRAC University", "AIUB", "Primeasia University"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("ban_campus_khichuri", "ban_campus_khichuri", "Campus Khichuri Ghar", "banani_mohakhali", "Banani, Dhaka", "Khichuri & Thali", 10, "−10% Student OFF", 4.6, "Special Rice Bowl"),
                PartnerRestaurantEntity("ban_mohakhali_tong", "ban_mohakhali_tong", "Mohakhali Tong Plaza", "banani_mohakhali", "Banani, Dhaka", "Fast Food & Snacks", 15, "−15% Student OFF", 4.5, "Ruti Sobji Set")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("ban_b1", "ban_b1", "Ruti, Sobji & Egg", "Mohakhali Tong Plaza", 35, 3500L, 420, "breakfast", true, "−15%", "banani_mohakhali", "Banani, Dhaka"),
                CentralFoodEntity("ban_l1", "ban_l1", "BRACU Student Rice Bowl", "Campus Khichuri Ghar", 75, 7500L, 710, "lunch", true, "−10%", "banani_mohakhali", "Banani, Dhaka"),
                CentralFoodEntity("ban_s1", "ban_s1", "Student Chicken Burger", "Mohakhali Tong Plaza", 70, 7000L, 480, "snacks", true, "−15%", "banani_mohakhali", "Banani, Dhaka"),
                CentralFoodEntity("ban_d1", "ban_d1", "Banani Beef Tehari", "Campus Khichuri Ghar", 65, 6500L, 760, "dinner", true, "−10%", "banani_mohakhali", "Banani, Dhaka")
            )
        ),

        // 11. Uttara, Dhaka
        CampusArea(
            id = "uttara",
            name = "Uttara, Dhaka",
            shortName = "Uttara, Dhaka",
            latitude = 23.8759,
            longitude = 90.3795,
            district = "Dhaka",
            description = "Sector 10, Sector 7, IUBAT & Shanto-Mariam zone",
            universities = listOf("IUBAT", "Shanto-Mariam", "Uttara University"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("utt_student_canteen", "utt_student_canteen", "Uttara Student Canteen", "uttara", "Uttara, Dhaka", "Bangladeshi & Rice", 15, "−15% Student OFF", 4.6, "Chicken Khichuri"),
                PartnerRestaurantEntity("utt_kacchi_palace", "utt_kacchi_palace", "Uttara Kacchi Palace", "uttara", "Uttara, Dhaka", "Biriyani", 10, "−10% Student OFF", 4.7, "Kacchi Biriyani")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("utt_b1", "utt_b1", "Paratha, Dim Bhaji & Cha", "Uttara Student Canteen", 35, 3500L, 440, "breakfast", true, "−15%", "uttara", "Uttara, Dhaka"),
                CentralFoodEntity("utt_l1", "utt_l1", "Chicken Khichuri Platter", "Uttara Student Canteen", 70, 7000L, 690, "lunch", true, "−15%", "uttara", "Uttara, Dhaka"),
                CentralFoodEntity("utt_s1", "utt_s1", "Singara (2 pcs) + Cha", "Uttara Student Canteen", 20, 2000L, 270, "snacks", true, "−15%", "uttara", "Uttara, Dhaka"),
                CentralFoodEntity("utt_d1", "utt_d1", "Tehari with Salad", "Uttara Kacchi Palace", 55, 5500L, 740, "dinner", true, "−10%", "uttara", "Uttara, Dhaka")
            )
        ),

        // 12. Jahangirnagar University (JU), Savar
        CampusArea(
            id = "ju_savar",
            name = "Jahangirnagar University (JU), Savar",
            shortName = "JU, Savar",
            latitude = 23.8817,
            longitude = 90.2673,
            district = "Dhaka",
            description = "Dairy Gate, Prantik, Tarash & Bottola",
            universities = listOf("Jahangirnagar University (JU)"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("ju_bottola", "ju_bottola", "Bottola Student Khichuri", "ju_savar", "JU, Savar", "Traditional Rice & Vorta", 15, "−15% Student OFF", 4.8, "Bottola Special Vorta Rice"),
                PartnerRestaurantEntity("ju_dairy_gate", "ju_dairy_gate", "Dairy Gate Fast Food", "ju_savar", "JU, Savar", "Breakfast & Snacks", 10, "−10% Student OFF", 4.5, "Dim Toast & Cha")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("ju_b1", "ju_b1", "Bottola Khichuri & Egg", "Bottola Student Khichuri", 30, 3000L, 410, "breakfast", true, "−15%", "ju_savar", "JU, Savar"),
                CentralFoodEntity("ju_l1", "ju_l1", "Bottola 5 Vortas with Rice", "Bottola Student Khichuri", 40, 4000L, 540, "lunch", true, "−15%", "ju_savar", "JU, Savar"),
                CentralFoodEntity("ju_s1", "ju_s1", "JU Dairy Sweet Curd & Cha", "Dairy Gate Fast Food", 25, 2500L, 310, "snacks", false, "", "ju_savar", "JU, Savar"),
                CentralFoodEntity("ju_d1", "ju_d1", "Chicken Bhuna & Rice", "Bottola Student Khichuri", 60, 6000L, 660, "dinner", true, "−15%", "ju_savar", "JU, Savar")
            )
        ),

        // 13. Jagannath University (JnU), Old Dhaka
        CampusArea(
            id = "jnu_olddhaka",
            name = "Jagannath University (JnU), Old Dhaka",
            shortName = "JnU, Old Dhaka",
            latitude = 23.7088,
            longitude = 90.4107,
            district = "Dhaka",
            description = "Sadarghat, Shankhari Bazar, Victoria Park & Malitola",
            universities = listOf("Jagannath University (JnU)"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("jnu_beauty_lassi", "jnu_beauty_lassi", "Beauty Lassi & Faluda", "jnu_olddhaka", "JnU, Old Dhaka", "Heritage Desserts & Cha", 10, "−10% Student OFF", 4.9, "Special Lassi"),
                PartnerRestaurantEntity("jnu_bismillah", "jnu_bismillah", "Bismillah Kabab Ghar", "jnu_olddhaka", "JnU, Old Dhaka", "Old Dhaka Chaap & Naan", 10, "−10% Student OFF", 4.7, "Beef Chaap")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("jnu_b1", "jnu_b1", "Bakarkhani with Sweet Tea", "Campus Tong", 20, 2000L, 330, "breakfast", false, "", "jnu_olddhaka", "JnU, Old Dhaka"),
                CentralFoodEntity("jnu_l1", "jnu_l1", "Old Dhaka Beef Tehari", "Bismillah Kabab Ghar", 60, 6000L, 750, "lunch", true, "−10%", "jnu_olddhaka", "JnU, Old Dhaka"),
                CentralFoodEntity("jnu_s1", "jnu_s1", "Beauty Special Lassi", "Beauty Lassi & Faluda", 35, 3500L, 290, "snacks", true, "−10%", "jnu_olddhaka", "JnU, Old Dhaka"),
                CentralFoodEntity("jnu_d1", "jnu_d1", "Beef Chaap with Rumali Ruti", "Bismillah Kabab Ghar", 75, 7500L, 780, "dinner", true, "−10%", "jnu_olddhaka", "JnU, Old Dhaka")
            )
        ),

        // 14. Bangladesh Agricultural University (BAU), Mymensingh
        CampusArea(
            id = "bau_mymensingh",
            name = "BAU, Mymensingh",
            shortName = "BAU, Mymensingh",
            latitude = 24.7260,
            longitude = 90.4347,
            district = "Mymensingh",
            description = "Brahmaputra Bank, Jabbar Mor, KRF & Campus Canteen",
            universities = listOf("Bangladesh Agricultural University (BAU)"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("bau_jabbar_mor", "bau_jabbar_mor", "Jabbar Mor Student Hotel", "bau_mymensingh", "BAU, Mymensingh", "Fresh Dairy & Meals", 15, "−15% Student OFF", 4.7, "Pure Milk Tea & Polao"),
                PartnerRestaurantEntity("bau_hall_dining", "bau_hall_dining", "BAU Central Dining", "bau_mymensingh", "BAU, Mymensingh", "Hall Dining", 10, "−10% Student OFF", 4.5, "Fish Curry Rice")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("bau_b1", "bau_b1", "Ruti with Fresh BAU Cow Milk", "Jabbar Mor Student Hotel", 30, 3000L, 410, "breakfast", true, "−15%", "bau_mymensingh", "BAU, Mymensingh"),
                CentralFoodEntity("bau_l1", "bau_l1", "Fresh Pangas Fish Curry & Rice", "BAU Central Dining", 50, 5000L, 620, "lunch", true, "−10%", "bau_mymensingh", "BAU, Mymensingh"),
                CentralFoodEntity("bau_s1", "bau_s1", "Malaichai & Shingara", "Jabbar Mor Student Hotel", 20, 2000L, 260, "snacks", true, "−15%", "bau_mymensingh", "BAU, Mymensingh"),
                CentralFoodEntity("bau_d1", "bau_d1", "Chicken Khichuri Platter", "BAU Central Dining", 55, 5500L, 690, "dinner", true, "−10%", "bau_mymensingh", "BAU, Mymensingh")
            )
        ),

        // 15. Begum Rokeya University (BRUR), Rangpur
        CampusArea(
            id = "brur_rangpur",
            name = "BRUR, Rangpur",
            shortName = "BRUR, Rangpur",
            latitude = 25.7202,
            longitude = 89.2605,
            district = "Rangpur",
            description = "Park Mor, Modern Mor & Campus Avenue",
            universities = listOf("Begum Rokeya University, Rangpur (BRUR)"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("brur_park_mor", "brur_park_mor", "Park Mor Student Restora", "brur_rangpur", "BRUR, Rangpur", "North Bengal Cuisine", 15, "−15% Student OFF", 4.6, "Beef Shidhol Rice"),
                PartnerRestaurantEntity("brur_campus_canteen", "brur_campus_canteen", "BRUR Central Cafe", "brur_rangpur", "BRUR, Rangpur", "Snacks & Meals", 10, "−10% Student OFF", 4.5, "Chicken Polao")
            ),
            defaultMeals = listOf(
                CentralFoodEntity("brur_b1", "brur_b1", "Dim Paratha & Spiced Tea", "Park Mor Student Restora", 28, 2800L, 390, "breakfast", true, "−15%", "brur_rangpur", "BRUR, Rangpur"),
                CentralFoodEntity("brur_l1", "brur_l1", "North Bengal Beef Rice Platter", "Park Mor Student Restora", 70, 7000L, 710, "lunch", true, "−15%", "brur_rangpur", "BRUR, Rangpur"),
                CentralFoodEntity("brur_s1", "brur_s1", "Shingara & Rangpur Special Cha", "BRUR Central Cafe", 15, 1500L, 230, "snacks", false, "", "brur_rangpur", "BRUR, Rangpur"),
                CentralFoodEntity("brur_d1", "brur_d1", "Egg Khichuri with Mixed Pickle", "BRUR Central Cafe", 40, 4000L, 590, "dinner", true, "−10%", "brur_rangpur", "BRUR, Rangpur")
            )
        )
    )

    fun getDefaultArea(): CampusArea = CAMPUS_AREAS.first()

    fun findAreaById(areaId: String?): CampusArea {
        return CAMPUS_AREAS.find { it.id == areaId } ?: getDefaultArea()
    }

    /**
     * Calculates distance in kilometers between two geo-coordinates using Haversine formula.
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
     * Returns the closest campus area to the given coordinates.
     */
    fun findClosestArea(lat: Double, lon: Double): CampusArea {
        return CAMPUS_AREAS.minByOrNull { calculateDistanceKm(lat, lon, it.latitude, it.longitude) }
            ?: getDefaultArea()
    }

    /**
     * Attempts to read device location from GPS or Network provider.
     */
    @SuppressLint("MissingPermission")
    fun detectDeviceLocation(context: Context): Location? {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) return loc
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Location detection failed or permission denied: ${e.message}")
        }
        return null
    }

    /**
     * Detects closest campus area using device location, or returns default if unavailable.
     */
    fun detectCampusFromDevice(context: Context): CampusArea {
        val loc = detectDeviceLocation(context)
        return if (loc != null) {
            findClosestArea(loc.latitude, loc.longitude)
        } else {
            getDefaultArea()
        }
    }
}
