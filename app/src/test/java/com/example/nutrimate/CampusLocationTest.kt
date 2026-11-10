package com.example.nutrimate

import com.example.nutrimate.data.feast.FeastBudgetGatekeeper
import com.example.nutrimate.data.feast.NutriMateAiToolsGatekeeper
import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.model.RestaurantWithDistance
import com.example.nutrimate.data.service.RestaurantFirestoreService
import org.junit.Assert.*
import org.junit.Test

class CampusLocationTest {

    @Test
    fun testCampusAreasContainPartnerRestaurantsAndMeals() {
        val areas = CampusLocationManager.CAMPUS_AREAS
        assertTrue("Must support multiple university campus hubs", areas.size >= 4)

        for (area in areas) {
            assertTrue("Area ${area.name} must have partner restaurants", area.defaultRestaurants.isNotEmpty())
            assertTrue("Area ${area.name} must have meals across slots", area.defaultMeals.isNotEmpty())

            // Verify slots coverage
            val slots = area.defaultMeals.map { it.slot.lowercase() }.toSet()
            assertTrue("Area ${area.name} must cover breakfast", slots.contains("breakfast"))
            assertTrue("Area ${area.name} must cover lunch", slots.contains("lunch"))
            assertTrue("Area ${area.name} must cover dinner", slots.contains("dinner"))

            // Verify partner restaurant student discounts
            assertTrue("Partner restaurants must offer student discounts", area.defaultRestaurants.all { it.discountPercentage > 0 })
        }
    }

    @Test
    fun testFindClosestCampusArea() {
        // Test near Palashi (BUET)
        val buetArea = CampusLocationManager.findClosestArea(23.7268, 90.3922)
        assertEquals("buet_palashi", buetArea.id)

        // Test near TSC (Dhaka University)
        val duArea = CampusLocationManager.findClosestArea(23.7345, 90.3930)
        assertEquals("du_nilkhet", duArea.id)

        // Test near Dhanmondi 27
        val dhanArea = CampusLocationManager.findClosestArea(23.7460, 90.3740)
        assertEquals("dhanmondi", dhanArea.id)

        // Test near Banani 11 / Mohakhali
        val banArea = CampusLocationManager.findClosestArea(23.7770, 90.4060)
        assertEquals("banani_mohakhali", banArea.id)
    }

    @Test
    fun testAiGatekeeperCatalogDynamicUpdate() {
        val feastGatekeeper = FeastBudgetGatekeeper(null)
        val aiGatekeeper = NutriMateAiToolsGatekeeper(feastGatekeeper)

        val defaultArea = CampusLocationManager.getDefaultArea()
        aiGatekeeper.updateCatalogFromEntities(defaultArea.defaultMeals)

        // Search menu should now resolve meals from the active area
        val lunchResults = aiGatekeeper.searchMenu("", "lunch")
        assertFalse("Should find lunch meals in catalog", lunchResults.isEmpty())
        assertTrue("Meals should have positive price and calories", lunchResults.all { it.priceTk > 0 && it.kcal > 0 })

        // Switch to Dhanmondi
        val dhanmondi = CampusLocationManager.findAreaById("dhanmondi")
        aiGatekeeper.updateCatalogFromEntities(dhanmondi.defaultMeals)

        val kacchiResults = aiGatekeeper.searchMenu("kacchi", "lunch")
        assertFalse("Should find Kacchi Bhai in Dhanmondi", kacchiResults.isEmpty())
        assertTrue(kacchiResults.first().place.contains("Kacchi Bhai") || kacchiResults.first().name.contains("Kacchi"))
    }

    @Test
    fun testRestaurantFirestoreServiceSeedAndSpatialCalculations() {
        val service = RestaurantFirestoreService(null)

        // 1. Verify default restaurants dataset
        val restaurants = service.DEFAULT_RESTAURANTS
        assertTrue("Must have at least 10 seeded restaurants across Dhaka campuses", restaurants.size >= 10)

        for (restaurant in restaurants) {
            assertTrue("Restaurant ID must be non-empty", restaurant.id.isNotBlank())
            assertTrue("Restaurant Name must be non-empty", restaurant.name.isNotBlank())
            assertTrue("Latitude must be valid in Dhaka", restaurant.latitude in 23.6..24.0)
            assertTrue("Longitude must be valid in Dhaka", restaurant.longitude in 90.3..90.6)
            assertTrue("Rating must be between 1.0 and 5.0", restaurant.rating in 1.0..5.0)
            assertTrue("Menu must not be empty", restaurant.menu.isNotEmpty())

            // Validate each menu item
            for (item in restaurant.menu) {
                assertNotNull("Menu item must have name", item["name"])
                val price = (item["priceTk"] as? Number)?.toInt() ?: 0
                val kcal = (item["kcal"] as? Number)?.toInt() ?: 0
                assertTrue("Menu item price must be positive", price > 0)
                assertTrue("Menu item kcal must be positive", kcal > 0)
            }
        }

        // 2. Verify Haversine distance formula
        val sameDist = service.calculateDistanceKm(23.7268, 90.3922, 23.7268, 90.3922)
        assertEquals(0.0, sameDist, 0.001)

        // BUET Palashi to Star Kabab Palashi (~100m)
        val closeDist = service.calculateDistanceKm(23.7268, 90.3922, 23.7275, 90.3920)
        assertTrue("Distance should be within 0.05km to 0.2km", closeDist in 0.05..0.20)

        // BUET Palashi to Dhanmondi (~2.5km to 3.5km)
        val farDist = service.calculateDistanceKm(23.7268, 90.3922, 23.7460, 90.3740)
        assertTrue("Distance should be ~2.8km", farDist in 2.2..3.6)

        // 3. Verify formatDistance
        assertEquals("100 m", service.formatDistance(0.100))
        assertEquals("450 m", service.formatDistance(0.450))
        assertTrue(service.formatDistance(1.23).contains("1.2 km"))
        assertTrue(service.formatDistance(2.80).contains("2.8 km"))

        // 4. Verify extractMealsFromNearbyRestaurants
        val sampleList = listOf(
            RestaurantWithDistance(
                restaurant = restaurants.first(),
                distanceKm = closeDist,
                distanceFormatted = service.formatDistance(closeDist)
            )
        )
        val extractedMeals = service.extractMealsFromNearbyRestaurants(sampleList)
        assertTrue("Should extract meals from restaurant menu", extractedMeals.isNotEmpty())
        assertEquals(restaurants.first().menu.size, extractedMeals.size)
        assertTrue("Extracted meals must have positive price", extractedMeals.all { it.priceTk > 0 && it.pricePaisa > 0 })
    }
}
