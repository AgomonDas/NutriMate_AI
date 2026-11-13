package com.example.nutrimate

import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.location.MapDirectionsHelper
import com.example.nutrimate.data.service.RestaurantFirestoreService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDirectionsHelperTest {

    @Test
    fun resolveCoordinates_prefersStoredNonZeroCoordinates() {
        val resolved = MapDirectionsHelper.resolveCoordinates(
            placeName = "Star Kabab & Biriyani",
            storedLat = 23.7501,
            storedLon = 90.3912
        )
        assertEquals(23.7501, resolved.latitude, 0.0001)
        assertEquals(90.3912, resolved.longitude, 0.0001)
    }

    @Test
    fun resolveCoordinates_matchesAllSeededCampusRestaurantsInBangladesh() {
        CampusLocationManager.CAMPUS_AREAS.forEach { area ->
            area.defaultRestaurants.forEach { partner ->
                val resolved = MapDirectionsHelper.resolveCoordinates(
                    placeName = partner.name,
                    storedLat = partner.latitude,
                    storedLon = partner.longitude,
                    fallbackArea = area
                )
                assertTrue(
                    "Invalid latitude for ${partner.name}: ${resolved.latitude}",
                    resolved.latitude in 20.0..27.0
                )
                assertTrue(
                    "Invalid longitude for ${partner.name}: ${resolved.longitude}",
                    resolved.longitude in 88.0..93.0
                )
                assertTrue(
                    "Empty address for ${partner.name}",
                    resolved.address.isNotBlank()
                )
            }
            area.defaultMeals.forEach { meal ->
                val resolved = MapDirectionsHelper.resolveCoordinates(
                    placeName = meal.place,
                    storedLat = meal.latitude,
                    storedLon = meal.longitude,
                    fallbackArea = area
                )
                assertTrue(
                    "Invalid meal latitude for ${meal.place}: ${resolved.latitude}",
                    resolved.latitude in 20.0..27.0
                )
                assertTrue(
                    "Invalid meal longitude for ${meal.place}: ${resolved.longitude}",
                    resolved.longitude in 88.0..93.0
                )
            }
        }
    }

    @Test
    fun defaultFirestoreRestaurants_coverAll15CampusHubsWithCoordinates() {
        val service = RestaurantFirestoreService(customDb = null)
        assertTrue(
            "Expected at least 30 seeded restaurants across 15 campuses",
            service.DEFAULT_RESTAURANTS.size >= 30
        )
        service.DEFAULT_RESTAURANTS.forEach { rest ->
            assertTrue(
                "Invalid latitude for ${rest.name}: ${rest.latitude}",
                rest.latitude in 20.0..27.0
            )
            assertTrue(
                "Invalid longitude for ${rest.name}: ${rest.longitude}",
                rest.longitude in 88.0..93.0
            )
            assertTrue(
                "Missing address for ${rest.name}",
                rest.address.isNotBlank()
            )
        }
    }

    @Test
    fun resolveCoordinates_generatesDeterministicFallbackAroundCampusForUnknownPlaces() {
        val kuet = CampusLocationManager.CAMPUS_AREAS.first { it.id == "kuet_khulna" }
        val first = MapDirectionsHelper.resolveCoordinates(
            placeName = "Secret Night Canteen",
            fallbackArea = kuet
        )
        val second = MapDirectionsHelper.resolveCoordinates(
            placeName = "Secret Night Canteen",
            fallbackArea = kuet
        )
        assertEquals(first, second)
        assertTrue(first.latitude in 22.89..22.91)
        assertTrue(first.longitude in 89.49..89.51)
    }

    @Test
    fun all15Campuses_haveNearbyRestaurantsAndStudentDealsWithin10Km() {
        val service = RestaurantFirestoreService(customDb = null)
        CampusLocationManager.CAMPUS_AREAS.forEach { area ->
            val nearby = service.DEFAULT_RESTAURANTS.filter { rest ->
                service.calculateDistanceKm(area.latitude, area.longitude, rest.latitude, rest.longitude) <= 10.0
            }
            assertTrue(
                "Expected nearby restaurants within 10km for ${area.name}",
                nearby.isNotEmpty()
            )
            assertTrue(
                "Expected at least one partner deal within 10km for ${area.name}",
                nearby.any { it.isPartner && it.discountPercentage > 0 }
            )
            assertTrue(
                "Expected menu items within 10km for ${area.name}",
                nearby.sumOf { it.menu.size } >= 3
            )
        }
    }
}
