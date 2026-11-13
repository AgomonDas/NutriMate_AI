package com.example.nutrimate.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrimate.data.location.CampusArea
import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.location.MapDirectionsHelper
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.data.service.RestaurantFirestoreService
import com.example.nutrimate.data.weather.CurrentWeather
import com.example.nutrimate.data.weather.WeatherService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import kotlin.math.roundToInt

private const val TAG = "DashboardViewModel"

enum class DealSortOption(val label: String) {
    DISTANCE("Sort: Distance"),
    PRICE("Sort: Price (Low)"),
    RATING("Sort: Rating (High)");

    fun next(): DealSortOption = when (this) {
        DISTANCE -> PRICE
        PRICE -> RATING
        RATING -> DISTANCE
    }
}

enum class WeeklyBudgetStatus(val label: String) {
    ON_TRACK("On track"),
    SPENDING_FAST("Spending fast"),
    OVER_BUDGET("Over budget"),
    NO_BUDGET("No budget set")
}

data class DiscountedMealDeal(
    val key: String,
    val mealName: String,
    val restaurantName: String,
    val restaurantId: String,
    val distanceKm: Double,
    val distanceFormatted: String,
    val rating: Double,
    val originalPriceTk: Int,
    val discountedPriceTk: Int,
    val discountPercent: Int,
    val discountTag: String,
    val slot: String,
    val kcal: Int,
    val isVegetarian: Boolean,
    val isPartner: Boolean,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = ""
)

data class DashboardUiState(
    val currentCampus: CampusArea = CampusLocationManager.getDefaultArea(),
    val currentWeather: CurrentWeather? = null,
    val isWeatherLoading: Boolean = false,
    val weatherError: String? = null,
    val isLocatingGps: Boolean = false,
    val locationNote: String? = null,
    val rawDeals: List<DiscountedMealDeal> = emptyList(),
    val isDealsLoading: Boolean = false,
    val dealsError: String? = null,
    val selectedSlotFilter: String = "All",
    val isVegOnly: Boolean = false,
    val isDealsOnly: Boolean = false,
    val sortOption: DealSortOption = DealSortOption.DISTANCE,
    val showCampusPicker: Boolean = false,
    val monthlyBudgetTk: Int = 6000,
    val weeklySpentTk: Int = 680
) {
    val weeklyBudgetTk: Int
        get() = (monthlyBudgetTk / 4.0).toInt()

    val currentDayOfWeekIndex: Int
        get() {
            val cal = Calendar.getInstance()
            // In Java Calendar: SUNDAY = 1, MONDAY = 2, ..., SATURDAY = 7
            // Convert to Monday=1 .. Sunday=7
            val day = cal.get(Calendar.DAY_OF_WEEK)
            return if (day == Calendar.SUNDAY) 7 else day - 1
        }

    val weekElapsedFraction: Float
        get() = (currentDayOfWeekIndex.toFloat() / 7f).coerceIn(0.1f, 1f)

    val daysRemainingInWeek: Int
        get() = (7 - currentDayOfWeekIndex + 1).coerceAtLeast(1)

    val progressFraction: Float
        get() = if (weeklyBudgetTk > 0) (weeklySpentTk.toFloat() / weeklyBudgetTk).coerceIn(0f, 1.5f) else 0f

    val budgetStatus: WeeklyBudgetStatus
        get() = when {
            weeklyBudgetTk <= 0 -> WeeklyBudgetStatus.NO_BUDGET
            weeklySpentTk > weeklyBudgetTk -> WeeklyBudgetStatus.OVER_BUDGET
            progressFraction > weekElapsedFraction + 0.15f -> WeeklyBudgetStatus.SPENDING_FAST
            else -> WeeklyBudgetStatus.ON_TRACK
        }

    val filteredDeals: List<DiscountedMealDeal>
        get() {
            return rawDeals
                .filter { deal -> deal.distanceKm <= 10.0 }
                .filter { deal ->
                    if (selectedSlotFilter.equals("All", ignoreCase = true)) true
                    else deal.slot.equals(selectedSlotFilter, ignoreCase = true)
                }
                .filter { deal ->
                    if (isVegOnly) deal.isVegetarian else true
                }
                .filter { deal ->
                    if (isDealsOnly) deal.isPartner && deal.discountPercent > 0 else true
                }
                .let { list ->
                    when (sortOption) {
                        DealSortOption.DISTANCE -> list.sortedBy { it.distanceKm }
                        DealSortOption.PRICE -> list.sortedBy { it.discountedPriceTk }
                        DealSortOption.RATING -> list.sortedByDescending { it.rating }
                    }
                }
        }
}

class DashboardViewModel(
    private val repository: NutriMateFirestoreRepository,
    private val currentUserId: String?
) : ViewModel() {

    private val restaurantService = RestaurantFirestoreService()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Load default campus (KUET or closest), weather, and deals
        val defaultArea = CampusLocationManager.getDefaultArea()
        _uiState.update { it.copy(currentCampus = defaultArea) }
        loadWeatherForCampus(defaultArea)
        loadDealsForCampus(defaultArea)

        // Observe profile to keep monthly budget synced
        if (currentUserId != null) {
            viewModelScope.launch {
                repository.observeUserProfile(currentUserId).collect { profile ->
                    if (profile != null) {
                        _uiState.update {
                            it.copy(
                                monthlyBudgetTk = profile.monthlyBudget,
                                // Calculate weekly estimate from current monthly spend
                                weeklySpentTk = (profile.totalSpentThisMonth % (profile.monthlyBudget / 4).coerceAtLeast(100)).coerceAtLeast(320)
                            )
                        }
                    }
                }
            }
        }
    }

    fun openCampusPicker() {
        _uiState.update { it.copy(showCampusPicker = true) }
    }

    fun dismissCampusPicker() {
        _uiState.update { it.copy(showCampusPicker = false) }
    }

    fun selectCampus(campus: CampusArea) {
        _uiState.update {
            it.copy(
                currentCampus = campus,
                showCampusPicker = false,
                locationNote = null
            )
        }
        loadWeatherForCampus(campus)
        loadDealsForCampus(campus)
    }

    fun setLocationPermissionDenied() {
        val campus = _uiState.value.currentCampus
        _uiState.update {
            it.copy(
                locationNote = "Location permission is off. Showing ${campus.shortName} deals. Tap to pick another campus."
            )
        }
    }

    fun detectLocation(context: Context) {
        _uiState.update { it.copy(isLocatingGps = true) }
        viewModelScope.launch {
            try {
                val loc = CampusLocationManager.detectDeviceLocation(context)
                if (loc != null) {
                    val closest = CampusLocationManager.findClosestArea(loc.latitude, loc.longitude)
                    val distToClosestKm = restaurantService.calculateDistanceKm(
                        loc.latitude, loc.longitude,
                        closest.latitude, closest.longitude
                    )
                    val useRawGps = distToClosestKm <= 20.0
                    _uiState.update {
                        it.copy(
                            currentCampus = closest,
                            locationNote = if (useRawGps) {
                                "📍 Located near ${closest.shortName}"
                            } else {
                                "Showing ${closest.shortName} campus meals & deals"
                            }
                        )
                    }
                    loadWeatherForCampus(closest)
                    loadDealsForCampus(
                        campus = closest,
                        userLat = if (useRawGps) loc.latitude else closest.latitude,
                        userLon = if (useRawGps) loc.longitude else closest.longitude
                    )
                } else {
                    val detected = CampusLocationManager.detectCampusFromDevice(context)
                    _uiState.update {
                        it.copy(
                            currentCampus = detected,
                            locationNote = "Showing ${detected.shortName} deals."
                        )
                    }
                    loadWeatherForCampus(detected)
                    loadDealsForCampus(detected)
                }
            } catch (e: Exception) {
                Log.w(TAG, "GPS detection error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLocatingGps = false) }
            }
        }
    }

    fun loadWeatherForCampus(campus: CampusArea) {
        _uiState.update { it.copy(isWeatherLoading = true, weatherError = null) }
        viewModelScope.launch {
            try {
                val weather = WeatherService.fetchWeather(campus.latitude, campus.longitude)
                _uiState.update {
                    it.copy(
                        currentWeather = weather,
                        isWeatherLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load weather", e)
                _uiState.update {
                    it.copy(
                        isWeatherLoading = false,
                        weatherError = "Weather unavailable"
                    )
                }
            }
        }
    }

    fun loadDealsForCampus(
        campus: CampusArea,
        userLat: Double = campus.latitude,
        userLon: Double = campus.longitude
    ) {
        _uiState.update { it.copy(isDealsLoading = true, dealsError = null) }
        viewModelScope.launch {
            try {
                // Seed Firestore in the background so initial UI rendering never blocks on writes
                launch {
                    repository.restaurantService.seedRestaurantsIfEmpty()
                    repository.seedAreaRestaurantsAndFoods(campus)
                }

                // Query nearby restaurants within 10 km; if GPS coords are outside radius, fall back to campus coords
                var anchorLat = userLat
                var anchorLon = userLon
                var nearbyRestaurants = withTimeoutOrNull(3_000L) {
                    repository.getNearbyRestaurants(anchorLat, anchorLon, radiusKm = 10.0)
                }.orEmpty()

                if (nearbyRestaurants.isEmpty() && (anchorLat != campus.latitude || anchorLon != campus.longitude)) {
                    anchorLat = campus.latitude
                    anchorLon = campus.longitude
                    nearbyRestaurants = withTimeoutOrNull(3_000L) {
                        repository.getNearbyRestaurants(anchorLat, anchorLon, radiusKm = 10.0)
                    }.orEmpty()
                }

                val restaurantsToUse = nearbyRestaurants.ifEmpty {
                    restaurantService.DEFAULT_RESTAURANTS.map { rest ->
                        val dist = restaurantService.calculateDistanceKm(anchorLat, anchorLon, rest.latitude, rest.longitude)
                        com.example.nutrimate.data.model.RestaurantWithDistance(
                            restaurant = rest,
                            distanceKm = dist,
                            distanceFormatted = restaurantService.formatDistance(dist)
                        )
                    }.filter { it.distanceKm <= 10.0 }.sortedBy { it.distanceKm }
                }

                val dealsList = mutableListOf<DiscountedMealDeal>()
                val seenMealKeys = mutableSetOf<String>()

                for (item in restaurantsToUse) {
                    val rest = item.restaurant
                    val discountPercent = if (rest.isPartner && rest.discountPercentage > 0) rest.discountPercentage else 0
                    val discountTag = if (discountPercent > 0) "−$discountPercent%" else ""

                    for (m in rest.menu) {
                        val name = m["name"] as? String ?: continue
                        val price = (m["priceTk"] as? Number)?.toInt() ?: 50
                        val kcal = (m["kcal"] as? Number)?.toInt() ?: 500
                        val slot = m["slot"] as? String ?: "lunch"
                        val isVeg = (m["isVegetarian"] as? Boolean) ?: false
                        val id = m["id"] as? String ?: name.lowercase().replace(" ", "_")

                        val originalPrice = if (discountPercent > 0) {
                            (price / (1.0 - (discountPercent / 100.0))).roundToInt().coerceAtLeast(price + 5)
                        } else {
                            price
                        }

                        val normalizedKey = "${name.trim().lowercase()}_${slot.trim().lowercase()}"
                        seenMealKeys.add(normalizedKey)

                        dealsList.add(
                            DiscountedMealDeal(
                                key = "${rest.id}_$id",
                                mealName = name,
                                restaurantName = rest.name,
                                restaurantId = rest.id,
                                distanceKm = item.distanceKm,
                                distanceFormatted = item.distanceFormatted,
                                rating = rest.rating,
                                originalPriceTk = originalPrice,
                                discountedPriceTk = price,
                                discountPercent = discountPercent,
                                discountTag = discountTag,
                                slot = slot,
                                kcal = kcal,
                                isVegetarian = isVeg,
                                isPartner = rest.isPartner && discountPercent > 0,
                                latitude = rest.latitude,
                                longitude = rest.longitude,
                                address = rest.address
                            )
                        )
                    }
                }

                // Supplement with any campus.defaultMeals not already represented in restaurant menus
                val partnerLookup = campus.defaultRestaurants.associateBy { it.name.trim().lowercase() }
                for (meal in campus.defaultMeals) {
                    val normalizedKey = "${meal.name.trim().lowercase()}_${meal.slot.trim().lowercase()}"
                    if (normalizedKey in seenMealKeys) continue

                    val geo = MapDirectionsHelper.resolveCoordinates(
                        placeName = meal.place,
                        storedLat = meal.latitude,
                        storedLon = meal.longitude,
                        fallbackArea = campus
                    )
                    val dist = restaurantService.calculateDistanceKm(anchorLat, anchorLon, geo.latitude, geo.longitude)
                    if (dist > 10.0) continue

                    val partner = partnerLookup[meal.place.trim().lowercase()]
                    val pctFromTag = meal.discountTag.filter { it.isDigit() }.toIntOrNull() ?: 0
                    val discountPercent = if (meal.isPartner) {
                        (partner?.discountPercentage ?: pctFromTag).coerceAtLeast(if (pctFromTag > 0) pctFromTag else 10)
                    } else 0
                    val originalPrice = if (discountPercent > 0) {
                        (meal.priceTk / (1.0 - (discountPercent / 100.0))).roundToInt().coerceAtLeast(meal.priceTk + 5)
                    } else meal.priceTk

                    val lowerName = meal.name.lowercase()
                    val isVeg = lowerName.contains("veg") || lowerName.contains("dal") ||
                        lowerName.contains("shingara") || lowerName.contains("fuchka") ||
                        lowerName.contains("chotpoti") || lowerName.contains("ruti") ||
                        lowerName.contains("luchi") || lowerName.contains("coffee") || lowerName.contains("tea")

                    dealsList.add(
                        DiscountedMealDeal(
                            key = "campus_${meal.id}",
                            mealName = meal.name,
                            restaurantName = meal.place,
                            restaurantId = partner?.id ?: meal.id,
                            distanceKm = dist,
                            distanceFormatted = restaurantService.formatDistance(dist),
                            rating = partner?.rating ?: 4.6,
                            originalPriceTk = originalPrice,
                            discountedPriceTk = meal.priceTk,
                            discountPercent = discountPercent,
                            discountTag = if (discountPercent > 0) "−$discountPercent%" else "",
                            slot = meal.slot,
                            kcal = meal.kcal,
                            isVegetarian = isVeg,
                            isPartner = discountPercent > 0,
                            latitude = geo.latitude,
                            longitude = geo.longitude,
                            address = geo.address
                        )
                    )
                }

                _uiState.update {
                    it.copy(
                        rawDeals = dealsList,
                        isDealsLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load deals", e)
                _uiState.update {
                    it.copy(
                        isDealsLoading = false,
                        dealsError = "Unable to load deals near ${campus.shortName}"
                    )
                }
            }
        }
    }

    fun setSlotFilter(slot: String) {
        _uiState.update { it.copy(selectedSlotFilter = slot) }
    }

    fun toggleVegOnly() {
        _uiState.update { it.copy(isVegOnly = !it.isVegOnly) }
    }

    fun toggleDealsOnly() {
        _uiState.update { it.copy(isDealsOnly = !it.isDealsOnly) }
    }

    fun cycleSortOption() {
        _uiState.update { it.copy(sortOption = it.sortOption.next()) }
    }
}
