package com.example.nutrimate.data.location

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

data class RestaurantGeoPoint(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

object MapDirectionsHelper {

    /**
     * Canonical coordinates for all seeded campus restaurants, canteens, and partner hubs.
     * Used when seeding Firestore documents and as a resolver for meal options.
     */
    val KNOWN_PLACE_COORDINATES: Map<String, RestaurantGeoPoint> = mapOf(
        // KUET, Khulna
        "chuijhal hotel" to RestaurantGeoPoint(22.8980, 89.5050, "Fulbarigate, near KUET Main Gate, Khulna"),
        "teligati student" to RestaurantGeoPoint(22.9020, 89.5010, "Teligati Mor, KUET, Khulna"),
        "kuet central canteen" to RestaurantGeoPoint(22.9010, 89.5030, "Inside KUET Campus, Khulna"),
        "campus corner" to RestaurantGeoPoint(22.9002, 89.5028, "KUET Student Plaza, Khulna"),

        // BUET & Palashi
        "star kabab & biriyani" to RestaurantGeoPoint(23.7275, 90.3920, "Nazimuddin Road, Palashi, Dhaka"),
        "dhaka paratha house" to RestaurantGeoPoint(23.7258, 90.3942, "Bakshibazar Mor, Dhaka"),
        "palashi tong" to RestaurantGeoPoint(23.7266, 90.3925, "Palashi Chottor, BUET Gate"),
        "royal biriyani" to RestaurantGeoPoint(23.7240, 90.3890, "Lalbagh Fort Road, Old Dhaka"),

        // DU & Nilkhet
        "madhur canteen" to RestaurantGeoPoint(23.7335, 90.3920, "Arts Faculty, University of Dhaka"),
        "nilkhet baburchi" to RestaurantGeoPoint(23.7328, 90.3860, "Nilkhet Mor, Baburchi Lane"),
        "shadow cafe" to RestaurantGeoPoint(23.7342, 90.3935, "TSC Compound, University of Dhaka"),

        // RUET, Rajshahi
        "kalai ruti ghar" to RestaurantGeoPoint(24.3642, 88.6295, "Kazla Gate, RUET, Rajshahi"),
        "talaimari student hotel" to RestaurantGeoPoint(24.3621, 88.6258, "Talaimari Mor, Rajshahi"),

        // CUET, Chittagong
        "chittagong mezbani house" to RestaurantGeoPoint(22.4618, 91.9722, "Pahartoli Bazar, CUET Gate, Chittagong"),
        "pahartoli student cafe" to RestaurantGeoPoint(22.4596, 91.9704, "Kaptai Road, CUET, Chittagong"),

        // CU, Chittagong
        "cu jhupri" to RestaurantGeoPoint(22.4720, 91.7885, "Arts Jhupri, University of Chittagong"),
        "gate 1 mezbani hotel" to RestaurantGeoPoint(22.4695, 91.7845, "1 No Gate, Hathazari, Chittagong"),

        // RU, Rajshahi
        "tukitaki chattar cafe" to RestaurantGeoPoint(24.3712, 88.6371, "Tukitaki Chattar, University of Rajshahi"),
        "ru station view hotel" to RestaurantGeoPoint(24.3689, 88.6345, "Station Bazar Gate, Rajshahi"),

        // SUST, Sylhet
        "sylhet shatkora restora" to RestaurantGeoPoint(24.9165, 91.8330, "Akhalia Mor, SUST Gate, Sylhet"),
        "sust central canteen" to RestaurantGeoPoint(24.9182, 91.8315, "SUST Campus Canteen, Sylhet"),

        // Dhanmondi, Dhaka
        "kacchi bhai dhanmondi" to RestaurantGeoPoint(23.7465, 90.3750, "Road 27, Dhanmondi, Dhaka"),
        "tehari ghar sobhanbag" to RestaurantGeoPoint(23.7510, 90.3762, "Mirpur Road, Sobhanbag, Dhanmondi"),
        "dhanmondi 27 tong" to RestaurantGeoPoint(23.7470, 90.3735, "Road 27 (Old), Dhanmondi, Dhaka"),
        "star kabab dhanmondi" to RestaurantGeoPoint(23.7438, 90.3728, "Satmasjid Road, Dhanmondi, Dhaka"),

        // Banani & Mohakhali
        "banani tehari hub" to RestaurantGeoPoint(23.7925, 90.4048, "Road 11, Banani, Dhaka"),
        "campus khichuri ghar" to RestaurantGeoPoint(23.7770, 90.4060, "Wireless Gate, Mohakhali, Dhaka"),
        "mohakhali tong plaza" to RestaurantGeoPoint(23.7784, 90.4045, "Mohakhali T&T Gate, Dhaka"),

        // Uttara
        "uttara student canteen" to RestaurantGeoPoint(23.8768, 90.3802, "Sector 10, Uttara, Dhaka"),
        "uttara kacchi palace" to RestaurantGeoPoint(23.8745, 90.3788, "Sector 7, Uttara, Dhaka"),

        // JU, Savar
        "bottola student khichuri" to RestaurantGeoPoint(23.8824, 90.2681, "Bottola Market, JU, Savar"),
        "dairy gate fast food" to RestaurantGeoPoint(23.8805, 90.2698, "Dairy Gate, JU, Savar"),

        // JnU, Old Dhaka
        "beauty lassi" to RestaurantGeoPoint(23.7094, 90.4112, "Johnson Road, Old Dhaka"),
        "bismillah kabab ghar" to RestaurantGeoPoint(23.7082, 90.4095, "Victoria Park, Old Dhaka"),

        // BAU, Mymensingh
        "jabbar mor student hotel" to RestaurantGeoPoint(24.7255, 90.4360, "Jabbar Mor, BAU, Mymensingh"),
        "bau central dining" to RestaurantGeoPoint(24.7268, 90.4339, "BAU Central Campus, Mymensingh"),

        // BRUR, Rangpur
        "park mor student restora" to RestaurantGeoPoint(25.7195, 89.2612, "Park Mor, BRUR, Rangpur"),
        "brur central cafe" to RestaurantGeoPoint(25.7208, 89.2600, "BRUR Campus, Rangpur"),

        // Generic fallbacks
        "campus tong" to RestaurantGeoPoint(23.7268, 90.3929, "Campus Main Gate"),
        "campus canteen" to RestaurantGeoPoint(23.7264, 90.3921, "Central Campus Dining")
    )

    fun resolveCoordinates(
        placeName: String,
        storedLat: Double = 0.0,
        storedLon: Double = 0.0,
        fallbackArea: CampusArea? = null
    ): RestaurantGeoPoint {
        if (storedLat != 0.0 || storedLon != 0.0) {
            return RestaurantGeoPoint(storedLat, storedLon, placeName)
        }
        val lower = placeName.trim().lowercase()
        val matched = KNOWN_PLACE_COORDINATES.entries.firstOrNull { (key, _) ->
            lower.contains(key) || key.contains(lower)
        }?.value
        if (matched != null) return matched

        val area = fallbackArea ?: CampusLocationManager.getDefaultArea()
        // Deterministic slight offset around the campus hub if custom/unknown eatery
        val hashOffsetLat = ((lower.hashCode() % 9) * 0.0003)
        val hashOffsetLon = (((lower.hashCode() / 9) % 9) * 0.0003)
        return RestaurantGeoPoint(
            latitude = area.latitude + hashOffsetLat,
            longitude = area.longitude + hashOffsetLon,
            address = area.name
        )
    }

    /**
     * Opens Google Maps showing turn-by-turn / route directions from the user's current location
     * to the selected restaurant coordinates. Falls back gracefully to geo: or browser URL.
     */
    fun openGoogleMapsDirections(
        context: Context,
        restaurantName: String,
        latitude: Double,
        longitude: Double
    ) {
        val dirUri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude"
        )
        val mapsAppIntent = Intent(Intent.ACTION_VIEW, dirUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(mapsAppIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to geo: or browser
        }

        val geoUri = Uri.parse(
            "geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(restaurantName)})"
        )
        val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
        try {
            context.startActivity(geoIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to browser
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, dirUri)
        try {
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "No Maps app found. Location: $latitude, $longitude",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
