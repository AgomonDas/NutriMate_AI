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

    val CAMPUS_AREAS: List<CampusArea> = listOf(
        // 1. BUET & Palashi Hub
        CampusArea(
            id = "buet_palashi",
            name = "BUET & Palashi Campus Hub",
            shortName = "BUET / Palashi",
            latitude = 23.7266,
            longitude = 90.3925,
            description = "Palashi, Bakshibazar, Nazimuddin Road & Lalbagh",
            universities = listOf("BUET", "Dhaka Medical College", "Bakshibazar College"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("buet_star_kabab", "buet_star_kabab", "Star Kabab & Biriyani", "buet_palashi", "BUET & Palashi", "Bangladeshi & Biriyani", 10, "−10% Student OFF", 4.7, "Kacchi Biriyani"),
                PartnerRestaurantEntity("buet_paratha_house", "buet_paratha_house", "Dhaka Paratha House", "buet_palashi", "BUET & Palashi", "Breakfast & Tea", 10, "−10% Student OFF", 4.5, "Paratha, Dim Bhaji"),
                PartnerRestaurantEntity("buet_palashi_tong", "buet_palashi_tong", "Palashi Tong & Cha Corner", "buet_palashi", "BUET & Palashi", "Street Food & Snacks", 15, "−15% Student OFF", 4.6, "Khichuri Dim Combo"),
                PartnerRestaurantEntity("buet_royal_biriyani", "buet_royal_biriyani", "Royal Biriyani House", "buet_palashi", "BUET & Palashi", "Old Dhaka Biriyani", 10, "−10% Student OFF", 4.8, "Tehari & Salad"),
                PartnerRestaurantEntity("buet_cafe_d", "buet_cafe_d", "Cafe D (Polashi)", "buet_palashi", "BUET & Palashi", "Cafe & Snacks", 10, "−10% Student OFF", 4.4, "Singara & Cha")
            ),
            defaultMeals = listOf(
                // Breakfast
                CentralFoodEntity("buet_b1", "buet_b1", "Paratha, egg, tea", "Dhaka Paratha House", 35, 3500L, 450, "breakfast", true, "−10%", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_b2", "buet_b2", "Khichuri combo", "Palashi Tong & Cha Corner", 38, 3800L, 430, "breakfast", true, "−15%", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_b3", "buet_b3", "Ruti with mixed vegetable", "Bakshibazar Hotel", 28, 2800L, 310, "breakfast", false, "", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_b4", "buet_b4", "2 Boiled eggs & banana", "Campus Tong", 30, 3000L, 290, "breakfast", false, "", "buet_palashi", "BUET & Palashi"),
                // Lunch
                CentralFoodEntity("buet_l1", "buet_l1", "Chicken rice bowl", "Star Kabab & Biriyani", 75, 7500L, 720, "lunch", true, "−10%", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_l2", "buet_l2", "Kacchi Biriyani", "Star Kabab & Biriyani", 120, 12000L, 850, "lunch", true, "−10%", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_l3", "buet_l3", "Rice, dal & fish curry", "BUET Hall Mess", 60, 6000L, 650, "lunch", false, "", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_l4", "buet_l4", "Veg thali with 3 bhartas", "Haji Dhaba", 55, 5500L, 580, "lunch", false, "", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_l5", "buet_l5", "Beef bhuna rice platter", "Royal Biriyani House", 95, 9500L, 820, "lunch", true, "−10%", "buet_palashi", "BUET & Palashi"),
                // Snacks
                CentralFoodEntity("buet_s1", "buet_s1", "Fuchka + chotpoti", "Palashi Tong & Cha Corner", 30, 3000L, 260, "snacks", true, "−15%", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_s2", "buet_s2", "Singara (2 pcs) + Cha", "Cafe D (Polashi)", 20, 2000L, 290, "snacks", true, "−10%", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_s3", "buet_s3", "Chicken egg roll", "Food Corner", 45, 4500L, 380, "snacks", false, "", "buet_palashi", "BUET & Palashi"),
                // Dinner
                CentralFoodEntity("buet_d1", "buet_d1", "Old Dhaka Tehari + salad", "Royal Biriyani House", 51, 5100L, 780, "dinner", true, "−10%", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_d2", "buet_d2", "Hot chicken khichuri", "Palashi Tong & Cha Corner", 75, 7500L, 710, "dinner", true, "−15%", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_d3", "buet_d3", "Egg khichuri with achar", "Bakshibazar Tong", 40, 4000L, 620, "dinner", false, "", "buet_palashi", "BUET & Palashi"),
                CentralFoodEntity("buet_d4", "buet_d4", "Rice, 3 vortas & thick dal", "Campus Tong", 35, 3500L, 520, "dinner", false, "", "buet_palashi", "BUET & Palashi")
            )
        ),

        // 2. Dhaka University & Nilkhet Hub
        CampusArea(
            id = "du_nilkhet",
            name = "Dhaka University & Nilkhet Hub",
            shortName = "DU / Nilkhet",
            latitude = 23.7340,
            longitude = 90.3928,
            description = "TSC, Arts Faculty, Curzon Hall, Nilkhet Mor & Baburchi Lane",
            universities = listOf("University of Dhaka (DU)", "Eden Mohila College", "Home Economics"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("du_madhur_canteen", "du_madhur_canteen", "Madhur Canteen (DU)", "du_nilkhet", "DU & Nilkhet", "Campus Traditional", 15, "−15% Student OFF", 4.9, "Luchi & Chhola Dal"),
                PartnerRestaurantEntity("du_nilkhet_baburchi", "du_nilkhet_baburchi", "Nilkhet Baburchi", "du_nilkhet", "DU & Nilkhet", "Tehari & Khichuri", 10, "−10% Student OFF", 4.7, "Beef Khichuri"),
                PartnerRestaurantEntity("du_shadow_cafe", "du_shadow_cafe", "Shadow Cafe (TSC)", "du_nilkhet", "DU & Nilkhet", "Cafe & Snacks", 10, "−10% Student OFF", 4.6, "Cold Coffee & Samucha"),
                PartnerRestaurantEntity("du_curzon_tong", "du_curzon_tong", "Curzon Hall Tong", "du_nilkhet", "DU & Nilkhet", "Fast Food & Tea", 10, "−10% Student OFF", 4.5, "Egg Curry & Rice")
            ),
            defaultMeals = listOf(
                // Breakfast
                CentralFoodEntity("du_b1", "du_b1", "Luchi with chhola dal", "Madhur Canteen (DU)", 30, 3000L, 420, "breakfast", true, "−15%", "du_nilkhet", "DU & Nilkhet"),
                CentralFoodEntity("du_b2", "du_b2", "DU Canteen Dim Paratha", "Shadow Cafe (TSC)", 35, 3500L, 440, "breakfast", true, "−10%", "du_nilkhet", "DU & Nilkhet"),
                CentralFoodEntity("du_b3", "du_b3", "Tong Toast & Banana", "Hakim Chattar Tong", 25, 2500L, 270, "breakfast", false, "", "du_nilkhet", "DU & Nilkhet"),
                // Lunch
                CentralFoodEntity("du_l1", "du_l1", "Beef Khichuri special", "Nilkhet Baburchi", 80, 8000L, 780, "lunch", true, "−10%", "du_nilkhet", "DU & Nilkhet"),
                CentralFoodEntity("du_l2", "du_l2", "Chicken Curry, Rice & Dal", "DU Central Dining", 55, 5500L, 640, "lunch", false, "", "du_nilkhet", "DU & Nilkhet"),
                CentralFoodEntity("du_l3", "du_l3", "Ruhi Fish Rice Platter", "Shadow Cafe (TSC)", 65, 6500L, 660, "lunch", true, "−10%", "du_nilkhet", "DU & Nilkhet"),
                CentralFoodEntity("du_l4", "du_l4", "Nilkhet Kacchi Biriyani", "Nilkhet Baburchi", 115, 11500L, 840, "lunch", true, "−10%", "du_nilkhet", "DU & Nilkhet"),
                // Snacks
                CentralFoodEntity("du_s1", "du_s1", "TSC Shingara & Cha", "Madhur Canteen (DU)", 15, 1500L, 280, "snacks", true, "−15%", "du_nilkhet", "DU & Nilkhet"),
                CentralFoodEntity("du_s2", "du_s2", "Fuchka with spicy tok", "Hakim Chattar Tong", 30, 3000L, 250, "snacks", false, "", "du_nilkhet", "DU & Nilkhet"),
                CentralFoodEntity("du_s3", "du_s3", "Samucha & Cold Coffee", "Shadow Cafe (TSC)", 40, 4000L, 320, "snacks", true, "−10%", "du_nilkhet", "DU & Nilkhet"),
                // Dinner
                CentralFoodEntity("du_d1", "du_d1", "Nilkhet Beef Tehari", "Nilkhet Baburchi", 50, 5000L, 750, "dinner", true, "−10%", "du_nilkhet", "DU & Nilkhet"),
                CentralFoodEntity("du_d2", "du_d2", "Egg Curry with Bhaat & Dal", "Curzon Hall Tong", 40, 4000L, 580, "dinner", true, "−10%", "du_nilkhet", "DU & Nilkhet"),
                CentralFoodEntity("du_d3", "du_d3", "Dal, Rice & Aloobhorta", "DU Hall Dining", 35, 3500L, 510, "dinner", false, "", "du_nilkhet", "DU & Nilkhet")
            )
        ),

        // 3. Dhanmondi & Sobhanbag Student Hub
        CampusArea(
            id = "dhanmondi",
            name = "Dhanmondi & Sobhanbag Hub",
            shortName = "Dhanmondi",
            latitude = 23.7461,
            longitude = 90.3742,
            description = "Road 27, Shimanto Square, Sobhanbag, Rangs Fortune",
            universities = listOf("UIU", "DIU", "Stamford", "ULAB", "State University"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("dhan_kacchi_bhai", "dhan_kacchi_bhai", "Kacchi Bhai Dhanmondi", "dhanmondi", "Dhanmondi", "Biriyani & Polao", 10, "−10% Student OFF", 4.8, "Bashmati Kacchi"),
                PartnerRestaurantEntity("dhan_tong_27", "dhan_tong_27", "Dhanmondi 27 Tong", "dhanmondi", "Dhanmondi", "Street Foods", 15, "−15% Student OFF", 4.5, "Cha & Egg Roll"),
                PartnerRestaurantEntity("dhan_star_kabab", "dhan_star_kabab", "Star Kabab Dhanmondi", "dhanmondi", "Dhanmondi", "Grill & Kebab", 10, "−10% Student OFF", 4.7, "Chicken Grill & Naan"),
                PartnerRestaurantEntity("dhan_tehari_ghar", "dhan_tehari_ghar", "Tehari Ghar Sobhanbag", "dhanmondi", "Dhanmondi", "Traditional Tehari", 10, "−10% Student OFF", 4.6, "Beef Tehari")
            ),
            defaultMeals = listOf(
                // Breakfast
                CentralFoodEntity("dhan_b1", "dhan_b1", "Beef Nehari with Naan", "Star Kabab Dhanmondi", 45, 4500L, 520, "breakfast", true, "−10%", "dhanmondi", "Dhanmondi"),
                CentralFoodEntity("dhan_b2", "dhan_b2", "Dim Paratha & Special Cha", "Dhanmondi 27 Tong", 35, 3500L, 440, "breakfast", true, "−15%", "dhanmondi", "Dhanmondi"),
                // Lunch
                CentralFoodEntity("dhan_l1", "dhan_l1", "Student Chicken Rice Meal", "Dhanmondi 27 Tong", 70, 7000L, 680, "lunch", true, "−15%", "dhanmondi", "Dhanmondi"),
                CentralFoodEntity("dhan_l2", "dhan_l2", "Bashmati Kacchi (Half)", "Kacchi Bhai Dhanmondi", 130, 13000L, 890, "lunch", true, "−10%", "dhanmondi", "Dhanmondi"),
                CentralFoodEntity("dhan_l3", "dhan_l3", "Morog Polao & Egg", "Tehari Ghar Sobhanbag", 90, 9000L, 780, "lunch", true, "−10%", "dhanmondi", "Dhanmondi"),
                CentralFoodEntity("dhan_l4", "dhan_l4", "Student Veg Khichuri", "Sobhanbag Mess", 45, 4500L, 530, "lunch", false, "", "dhanmondi", "Dhanmondi"),
                // Snacks
                CentralFoodEntity("dhan_s1", "dhan_s1", "Chicken Shawarma Wrap", "Dhanmondi 27 Tong", 60, 6000L, 420, "snacks", true, "−15%", "dhanmondi", "Dhanmondi"),
                CentralFoodEntity("dhan_s2", "dhan_s2", "French Fries & Malt Tea", "Dhanmondi 27 Tong", 35, 3500L, 310, "snacks", true, "−15%", "dhanmondi", "Dhanmondi"),
                // Dinner
                CentralFoodEntity("dhan_d1", "dhan_d1", "Beef Tehari with Borhani", "Tehari Ghar Sobhanbag", 70, 7000L, 790, "dinner", true, "−10%", "dhanmondi", "Dhanmondi"),
                CentralFoodEntity("dhan_d2", "dhan_d2", "Grilled Chicken (1/4) & Naan", "Star Kabab Dhanmondi", 85, 8500L, 760, "dinner", true, "−10%", "dhanmondi", "Dhanmondi"),
                CentralFoodEntity("dhan_d3", "dhan_d3", "Egg Khichuri with Vorta", "Dhanmondi 27 Tong", 45, 4500L, 610, "dinner", true, "−15%", "dhanmondi", "Dhanmondi")
            )
        ),

        // 4. Banani & Mohakhali Hub
        CampusArea(
            id = "banani_mohakhali",
            name = "Banani & Mohakhali Hub",
            shortName = "Banani / Mohakhali",
            latitude = 23.7771,
            longitude = 90.4057,
            description = "BRACU Campus Area, Mohakhali Wireless & Banani 11",
            universities = listOf("BRAC University", "AIUB", "Primeasia University"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("ban_campus_khichuri", "ban_campus_khichuri", "Campus Khichuri Ghar", "banani_mohakhali", "Banani & Mohakhali", "Khichuri & Thali", 10, "−10% Student OFF", 4.6, "Special Rice Bowl"),
                PartnerRestaurantEntity("ban_mohakhali_tong", "ban_mohakhali_tong", "Mohakhali Tong Plaza", "banani_mohakhali", "Banani & Mohakhali", "Fast Food & Snacks", 15, "−15% Student OFF", 4.5, "Ruti Sobji Set"),
                PartnerRestaurantEntity("ban_tehari_hub", "ban_tehari_hub", "Banani Tehari Hub", "banani_mohakhali", "Banani & Mohakhali", "Tehari & Biriyani", 10, "−10% Student OFF", 4.7, "Banani Beef Tehari")
            ),
            defaultMeals = listOf(
                // Breakfast
                CentralFoodEntity("ban_b1", "ban_b1", "Ruti, Sobji & Egg", "Mohakhali Tong Plaza", 35, 3500L, 420, "breakfast", true, "−15%", "banani_mohakhali", "Banani & Mohakhali"),
                CentralFoodEntity("ban_b2", "ban_b2", "Paratha Omelette Combo", "Campus Khichuri Ghar", 35, 3500L, 450, "breakfast", true, "−10%", "banani_mohakhali", "Banani & Mohakhali"),
                // Lunch
                CentralFoodEntity("ban_l1", "ban_l1", "BRACU Student Rice Bowl", "Campus Khichuri Ghar", 75, 7500L, 710, "lunch", true, "−10%", "banani_mohakhali", "Banani & Mohakhali"),
                CentralFoodEntity("ban_l2", "ban_l2", "Chicken Biriyani Platter", "Banani Tehari Hub", 95, 9500L, 800, "lunch", true, "−10%", "banani_mohakhali", "Banani & Mohakhali"),
                CentralFoodEntity("ban_l3", "ban_l3", "Fish Curry & Rice Meal", "Mohakhali Mess", 60, 6000L, 640, "lunch", false, "", "banani_mohakhali", "Banani & Mohakhali"),
                // Snacks
                CentralFoodEntity("ban_s1", "ban_s1", "Student Chicken Burger", "Mohakhali Tong Plaza", 70, 7000L, 480, "snacks", true, "−15%", "banani_mohakhali", "Banani & Mohakhali"),
                CentralFoodEntity("ban_s2", "ban_s2", "Singara & Lemon Tea", "Mohakhali Tong Plaza", 20, 2000L, 240, "snacks", true, "−15%", "banani_mohakhali", "Banani & Mohakhali"),
                // Dinner
                CentralFoodEntity("ban_d1", "ban_d1", "Banani Beef Tehari", "Banani Tehari Hub", 65, 6500L, 760, "dinner", true, "−10%", "banani_mohakhali", "Banani & Mohakhali"),
                CentralFoodEntity("ban_d2", "ban_d2", "Egg Khichuri & Salad", "Campus Khichuri Ghar", 45, 4500L, 600, "dinner", true, "−10%", "banani_mohakhali", "Banani & Mohakhali")
            )
        ),

        // 5. Uttara Campus Hub
        CampusArea(
            id = "uttara",
            name = "Uttara Campus Hub",
            shortName = "Uttara",
            latitude = 23.8759,
            longitude = 90.3795,
            description = "Sector 10, Sector 7, IUBAT & Shanto-Mariam zone",
            universities = listOf("IUBAT", "Shanto-Mariam", "Uttara University"),
            defaultRestaurants = listOf(
                PartnerRestaurantEntity("utt_student_canteen", "utt_student_canteen", "Uttara Student Canteen", "uttara", "Uttara", "Bangladeshi & Rice", 15, "−15% Student OFF", 4.6, "Chicken Khichuri"),
                PartnerRestaurantEntity("utt_kacchi_palace", "utt_kacchi_palace", "Uttara Kacchi Palace", "uttara", "Uttara", "Biriyani", 10, "−10% Student OFF", 4.7, "Kacchi Biriyani")
            ),
            defaultMeals = listOf(
                // Breakfast
                CentralFoodEntity("utt_b1", "utt_b1", "Paratha, Dim Bhaji & Cha", "Uttara Student Canteen", 35, 3500L, 440, "breakfast", true, "−15%", "uttara", "Uttara"),
                // Lunch
                CentralFoodEntity("utt_l1", "utt_l1", "Chicken Khichuri Platter", "Uttara Student Canteen", 70, 7000L, 690, "lunch", true, "−15%", "uttara", "Uttara"),
                CentralFoodEntity("utt_l2", "utt_l2", "Kacchi Biriyani Bowl", "Uttara Kacchi Palace", 110, 11000L, 830, "lunch", true, "−10%", "uttara", "Uttara"),
                // Snacks
                CentralFoodEntity("utt_s1", "utt_s1", "Singara (2 pcs) + Cha", "Uttara Student Canteen", 20, 2000L, 270, "snacks", true, "−15%", "uttara", "Uttara"),
                // Dinner
                CentralFoodEntity("utt_d1", "utt_d1", "Tehari with Salad", "Uttara Kacchi Palace", 55, 5500L, 740, "dinner", true, "−10%", "uttara", "Uttara")
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
