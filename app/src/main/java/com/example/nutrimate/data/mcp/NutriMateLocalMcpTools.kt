package com.example.nutrimate.data.mcp

import com.example.nutrimate.data.feast.FeastBudgetGatekeeper
import com.example.nutrimate.data.feast.NutriMateAiToolsGatekeeper
import com.example.nutrimate.data.location.CampusArea
import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.location.MapDirectionsHelper
import com.example.nutrimate.data.model.RestaurantEntity
import com.example.nutrimate.data.model.RestaurantWithDistance
import com.example.nutrimate.model.MealSlot
import com.example.nutrimate.model.NutriMateUserContext
import com.google.firebase.ai.type.Schema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Per-turn live snapshot supplied to local NutriMate MCP tools so Gemini always inspects
 * authoritative app state without allowing unconfirmed mutations.
 */
class NutriMateTurnToolContext(
    val userContext: NutriMateUserContext,
    val currentSlots: List<MealSlot>,
    val nearbyRestaurants: List<RestaurantWithDistance>,
    val campusArea: CampusArea,
    @Volatile var pendingSwapProposal: PendingChatSwapProposal? = null
)

private fun Map<String, JsonElement>.strArg(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun Map<String, JsonElement>.intArg(key: String): Int? =
    (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

private fun Map<String, JsonElement>.doubleArg(key: String): Double? =
    (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

private fun Map<String, JsonElement>.boolArg(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

fun buildNutriMateLocalMcpTools(
    toolsGatekeeper: NutriMateAiToolsGatekeeper,
    turnContextProvider: () -> NutriMateTurnToolContext?
): List<McpTool> = listOf(
    GetUserContextTool(turnContextProvider),
    SearchCampusMenuTool(toolsGatekeeper),
    GetNearbyRestaurantsTool(turnContextProvider),
    CheckMealBudgetTool(toolsGatekeeper),
    FindFundingOptionsTool(toolsGatekeeper),
    ProposeMealSwapTool(toolsGatekeeper, turnContextProvider),
    LogManualMealTool(toolsGatekeeper, turnContextProvider)
)

private class GetUserContextTool(
    private val turnContextProvider: () -> NutriMateTurnToolContext?
) : McpTool {
    override val name = "get_user_context"
    override val description =
        "Returns the student's live calorie target, remaining calories today, monthly & meal-slot budgets (in BDT), unallocated reserve, active upcoming feast, dietary preferences, current campus area, and today's 4 planned meals."
    override val parameters: Map<String, Schema> = emptyMap()
    override val optionalParameters: List<String> = emptyList()
    override val source = McpToolSource.LOCAL

    override suspend fun execute(args: Map<String, JsonElement>): McpToolResult {
        val ctx = turnContextProvider() ?: return McpToolResult(
            payload = buildJsonObject { put("error", "No active user context") },
            displaySummary = "Context unavailable",
            isError = true
        )
        val u = ctx.userContext
        val payload = buildJsonObject {
            put("date", u.dateString)
            put("user_name", u.userName)
            put("goal", u.userGoal)
            put("campus_area", u.campusArea)
            put("university", u.university)
            put("daily_calorie_target", u.dailyCalorieTarget)
            put("planned_calories_today", u.plannedCaloriesToday)
            put("remaining_calories_today", u.remainingCaloriesToday)
            put("monthly_budget_tk", u.monthlyBudgetTk)
            put("monthly_spent_tk", u.monthlySpentTk)
            put("remaining_monthly_budget_tk", u.remainingMonthlyBudgetTk)
            put("today_allowance_tk", u.todaySlotAllowanceTotalTk)
            put("unallocated_reserve_tk", u.unallocatedReserveTk)
            put("on_mess_plan", u.onMessPlan)
            put("active_feast", u.activeFeast ?: "none")
            put("dietary_preferences", buildJsonArray { u.dietaryPreferences.forEach { add(JsonPrimitive(it)) } })
            put("slot_allowances_tk", buildJsonObject { u.slotAllowancesTk.forEach { (k, v) -> put(k, v) } })
            put("planned_meals_today", buildJsonArray {
                ctx.currentSlots.forEach { slot ->
                    val opt = slot.activeOption
                    add(buildJsonObject {
                        put("slot_id", slot.id)
                        put("slot_name", slot.name)
                        put("dish", opt.title)
                        put("place", opt.place)
                        put("price_tk", opt.priceNum)
                        put("kcal", slot.targetKcal)
                        put("allowance_tk", u.slotAllowancesTk[slot.id] ?: slot.budget)
                    })
                }
            })
        }
        return McpToolResult(
            payload = payload,
            displaySummary = "${u.remainingCaloriesToday} kcal left · Reserve ৳${u.unallocatedReserveTk} · ${u.campusArea}"
        )
    }
}

private class SearchCampusMenuTool(
    private val toolsGatekeeper: NutriMateAiToolsGatekeeper
) : McpTool {
    override val name = "search_campus_menu"
    override val description =
        "Searches the active campus meal catalog by food query (optional), meal slot (breakfast, lunch, snacks, dinner), max price in BDT, and vegetarian filter. Returns up to 5 matching items with item_id, name, place, price_tk, kcal, and slot."
    override val parameters = mapOf(
        "query" to Schema.string("Dish, ingredient, or restaurant keyword (leave empty to browse slot options)"),
        "slot" to Schema.enumeration(
            values = listOf("breakfast", "lunch", "snacks", "dinner"),
            description = "Optional meal slot filter"
        ),
        "max_price_tk" to Schema.integer("Maximum price in Bangladeshi Taka (BDT)"),
        "vegetarian_only" to Schema.boolean("Set true to return vegetarian dishes only")
    )
    override val optionalParameters = listOf("query", "slot", "max_price_tk", "vegetarian_only")
    override val source = McpToolSource.LOCAL

    override suspend fun execute(args: Map<String, JsonElement>): McpToolResult {
        val query = args.strArg("query").orEmpty()
        val slot = args.strArg("slot")
        val maxPrice = args.intArg("max_price_tk")
        val vegOnly = args.boolArg("vegetarian_only") ?: false

        var results = toolsGatekeeper.searchMenu(
            query = query,
            slot = slot,
            maxPriceTk = maxPrice
        )
        if (vegOnly) {
            results = results.filter { it.isVegetarian }
        }
        // Broaden search across all slots if strict slot filter returned nothing for a specific dish query
        if (results.isEmpty() && query.isNotBlank() && !slot.isNullOrBlank()) {
            results = toolsGatekeeper.searchMenu(
                query = query,
                slot = null,
                maxPriceTk = maxPrice
            ).let { if (vegOnly) it.filter { item -> item.isVegetarian } else it }
        }

        val payload = buildJsonObject {
            put("count", results.size)
            put("items", buildJsonArray {
                results.forEach { item ->
                    add(buildJsonObject {
                        put("item_id", item.itemId)
                        put("name", item.name)
                        put("place", item.place)
                        put("price_tk", item.priceTk)
                        put("kcal", item.kcal)
                        put("slot", item.slot)
                        put("is_vegetarian", item.isVegetarian)
                        put("is_partner", item.isPartner)
                    })
                }
            })
        }
        val summary = if (results.isNotEmpty()) {
            "Found ${results.size}: ${results.first().name} (৳${results.first().priceTk}, ${results.first().kcal} kcal)"
        } else {
            "No matching menu items found"
        }
        return McpToolResult(payload = payload, displaySummary = summary)
    }
}

private class GetNearbyRestaurantsTool(
    private val turnContextProvider: () -> NutriMateTurnToolContext?
) : McpTool {
    override val name = "get_nearby_restaurants"
    override val description =
        "Lists restaurants near the student's active campus within 10 km, sorted by distance, including student discount tags, addresses, and coordinates."
    override val parameters = mapOf(
        "max_distance_km" to Schema.double("Maximum distance in km (max 10.0)"),
        "partners_only" to Schema.boolean("If true, return only partner restaurants with student discounts")
    )
    override val optionalParameters = listOf("max_distance_km", "partners_only")
    override val source = McpToolSource.LOCAL

    override suspend fun execute(args: Map<String, JsonElement>): McpToolResult {
        val ctx = turnContextProvider() ?: return McpToolResult(
            payload = buildJsonObject { put("error", "Campus context unavailable") },
            displaySummary = "Context unavailable",
            isError = true
        )
        val maxKm = (args.doubleArg("max_distance_km") ?: 10.0).coerceIn(0.5, 10.0)
        val partnersOnly = args.boolArg("partners_only") ?: false

        val list = if (ctx.nearbyRestaurants.isNotEmpty()) {
            ctx.nearbyRestaurants
        } else {
            ctx.campusArea.defaultRestaurants.map { r ->
                val coords = MapDirectionsHelper.resolveCoordinates(
                    placeName = r.name,
                    storedLat = r.latitude,
                    storedLon = r.longitude,
                    fallbackArea = ctx.campusArea
                )
                val distKm = CampusLocationManager.calculateDistanceKm(
                    ctx.campusArea.latitude,
                    ctx.campusArea.longitude,
                    coords.latitude,
                    coords.longitude
                )
                val formatted = if (distKm < 1.0) {
                    "${(distKm * 1000).roundToInt()} m"
                } else {
                    String.format(Locale.US, "%.1f km", distKm)
                }
                val entity = RestaurantEntity(
                    docId = r.docId,
                    id = r.id,
                    name = r.name,
                    latitude = coords.latitude,
                    longitude = coords.longitude,
                    address = r.address.ifBlank { coords.address },
                    cuisine = r.cuisine,
                    rating = r.rating,
                    isPartner = true,
                    discountPercentage = r.discountPercentage,
                    discountTag = r.discountTag
                )
                RestaurantWithDistance(
                    restaurant = entity,
                    distanceKm = distKm,
                    distanceFormatted = formatted
                )
            }
        }
            .filter { it.distanceKm <= maxKm }
            .filter { !partnersOnly || it.restaurant.isPartner }
            .sortedBy { it.distanceKm }
            .take(8)

        val payload = buildJsonObject {
            put("campus", ctx.campusArea.shortName)
            put("count", list.size)
            put("restaurants", buildJsonArray {
                list.forEach { item ->
                    val r = item.restaurant
                    add(buildJsonObject {
                        put("name", r.name)
                        put("distance_km", ((item.distanceKm * 10.0).toInt() / 10.0))
                        put("distance_label", item.distanceFormatted)
                        put("is_partner", r.isPartner)
                        put("discount_tag", r.discountTag)
                        put("address", r.address)
                        put("latitude", r.latitude)
                        put("longitude", r.longitude)
                    })
                }
            })
        }
        val summary = if (list.isNotEmpty()) {
            "${list.size} places within ${maxKm.toInt()} km (closest: ${list.first().restaurant.name}, ${list.first().distanceFormatted})"
        } else {
            "No restaurants found within $maxKm km"
        }
        return McpToolResult(payload = payload, displaySummary = summary)
    }
}

private class CheckMealBudgetTool(
    private val toolsGatekeeper: NutriMateAiToolsGatekeeper
) : McpTool {
    override val name = "check_meal_budget"
    override val description =
        "Performs a read-only dry-run check of a dish (by item_id) against the target meal slot allowance. Returns ok=true and a signed check_token if affordable, or ok=false with short_by_tk if over budget."
    override val parameters = mapOf(
        "slot" to Schema.enumeration(
            values = listOf("breakfast", "lunch", "snacks", "dinner"),
            description = "Target meal slot"
        ),
        "item_id" to Schema.string("Menu item_id or exact dish name returned by search_campus_menu")
    )
    override val optionalParameters = emptyList<String>()
    override val source = McpToolSource.LOCAL

    override suspend fun execute(args: Map<String, JsonElement>): McpToolResult {
        val slot = args.strArg("slot") ?: "lunch"
        val itemId = args.strArg("item_id").orEmpty()
        if (itemId.isBlank()) {
            return McpToolResult(
                payload = buildJsonObject { put("error", "item_id is required") },
                displaySummary = "Missing item_id",
                isError = true
            )
        }

        val res = toolsGatekeeper.check(slot, itemId)
        val payload = buildJsonObject {
            put("ok", res.ok)
            put("slot", slot)
            put("item_id", itemId)
            put("price_tk", res.priceTk)
            put("slot_budget_tk", res.slotBudgetTk)
            put("kcal", res.kcal)
            res.checkToken?.let { put("check_token", it) }
            res.reason?.let { put("reason", it) }
            res.shortByTk?.let { put("short_by_tk", it) }
        }
        val summary = when {
            res.ok -> "Fits ৳${res.slotBudgetTk} $slot allowance (৳${res.priceTk}, token ready)"
            res.reason == "item_not_found" -> "Item '$itemId' not found on menu"
            else -> "Over $slot allowance by ৳${String.format("%.0f", res.shortByTk ?: 0.0)}"
        }
        return McpToolResult(
            payload = payload,
            displaySummary = summary,
            isError = res.reason == "item_not_found"
        )
    }
}

private class FindFundingOptionsTool(
    private val toolsGatekeeper: NutriMateAiToolsGatekeeper
) : McpTool {
    override val name = "find_funding_options"
    override val description =
        "Finds budget funding paths when check_meal_budget returns over_budget. Evaluates unallocated reserve first, then cancelling an upcoming feast, returning signed funding tokens."
    override val parameters = mapOf(
        "slot" to Schema.enumeration(
            values = listOf("breakfast", "lunch", "snacks", "dinner"),
            description = "Target meal slot"
        ),
        "item_id" to Schema.string("Menu item_id or exact dish name")
    )
    override val optionalParameters = emptyList<String>()
    override val source = McpToolSource.LOCAL

    override suspend fun execute(args: Map<String, JsonElement>): McpToolResult {
        val slot = args.strArg("slot") ?: "lunch"
        val itemId = args.strArg("item_id").orEmpty()
        val res = toolsGatekeeper.findFunding(slot, itemId)

        val payload = buildJsonObject {
            put("count", res.options.size)
            put("options", buildJsonArray {
                res.options.forEach { opt ->
                    add(buildJsonObject {
                        put("option_id", opt.optionId)
                        put("summary", opt.summary)
                        put("token", opt.token)
                        put("requires_feast_cancellation", opt.requiresConfirmation)
                        put("shortfall_tk", opt.shortfallTk)
                        opt.feastTitle?.let { put("feast_title", it) }
                    })
                }
            })
        }
        val summary = if (res.options.isNotEmpty()) {
            res.options.first().summary.take(90)
        } else {
            "No unallocated reserve or feast funding available"
        }
        return McpToolResult(payload = payload, displaySummary = summary)
    }
}

private class ProposeMealSwapTool(
    private val toolsGatekeeper: NutriMateAiToolsGatekeeper,
    private val turnContextProvider: () -> NutriMateTurnToolContext?
) : McpTool {
    override val name = "propose_meal_swap"
    override val description =
        "Stages a meal swap proposal for user confirmation in chat using a token from check_meal_budget or find_funding_options (or auto-validates slot + item_id). NEVER modifies the meal plan until the student taps Confirm."
    override val parameters = mapOf(
        "slot" to Schema.enumeration(
            values = listOf("breakfast", "lunch", "snacks", "dinner"),
            description = "Target meal slot"
        ),
        "item_id" to Schema.string("Menu item_id or dish name to swap in"),
        "token" to Schema.string("Optional signed token from check_meal_budget or find_funding_options")
    )
    override val optionalParameters = listOf("token")
    override val source = McpToolSource.LOCAL

    override suspend fun execute(args: Map<String, JsonElement>): McpToolResult {
        val turnCtx = turnContextProvider() ?: return McpToolResult(
            payload = buildJsonObject { put("error", "No active context") },
            displaySummary = "Context unavailable",
            isError = true
        )
        val slot = (args.strArg("slot") ?: "lunch").lowercase()
        val itemId = args.strArg("item_id").orEmpty()
        var token = args.strArg("token")

        var stored = token?.let { toolsGatekeeper.getStoredToken(it) }
        // If Gemini jumped directly to propose_meal_swap or token was omitted, resolve deterministically
        if (stored == null && itemId.isNotBlank()) {
            val checkRes = toolsGatekeeper.check(slot, itemId)
            if (checkRes.ok && checkRes.checkToken != null) {
                token = checkRes.checkToken
                stored = toolsGatekeeper.getStoredToken(token)
            } else if (checkRes.reason == "over_budget") {
                val funding = toolsGatekeeper.findFunding(slot, itemId)
                val best = funding.options.firstOrNull()
                if (best != null) {
                    token = best.token
                    stored = toolsGatekeeper.getStoredToken(token)
                } else {
                    return McpToolResult(
                        payload = buildJsonObject {
                            put("status", "cannot_fund")
                            put("reason", "Over budget by ৳${checkRes.shortByTk ?: 0.0} and no reserve or feast covers it.")
                        },
                        displaySummary = "Cannot fund swap (insufficient reserve/feasts)",
                        isError = false
                    )
                }
            }
        }

        if (stored == null || token == null) {
            return McpToolResult(
                payload = buildJsonObject {
                    put("status", "rejected")
                    put("reason", "Valid item_id or token not found. Search menu first.")
                },
                displaySummary = "Invalid swap item or token",
                isError = true
            )
        }

        val slotObj = turnCtx.currentSlots.find { it.id.equals(stored.slotId, ignoreCase = true) }
        val slotName = slotObj?.name ?: stored.slotId.replaceFirstChar { it.uppercase() }
        val shortfallTk = stored.shortfallPaisa.toDouble() / 100.0
        val remainderTk = if (stored.feastToCancel != null) {
            ((stored.feastToCancel.budgetPaisa - stored.shortfallPaisa).coerceAtLeast(0L)).toDouble() / 100.0
        } else 0.0

        val fundingType = when {
            stored.feastToCancel != null -> "FEAST_SACRIFICE"
            stored.unallocatedUsedPaisa > 0L -> "RESERVE"
            else -> "ALLOWANCE"
        }

        val pending = PendingChatSwapProposal(
            token = token,
            slotId = stored.slotId,
            slotName = slotName,
            itemName = stored.item.name,
            placeName = stored.item.place,
            priceTk = stored.item.priceTk,
            kcal = stored.item.kcal,
            fundingType = fundingType,
            shortfallTk = shortfallTk,
            feastTitle = stored.feastToCancel?.title,
            remainderTk = remainderTk,
            summary = stored.summary
        )
        turnCtx.pendingSwapProposal = pending

        val payload = buildJsonObject {
            put("status", "pending_user_confirmation")
            put("slot", pending.slotId)
            put("item_name", pending.itemName)
            put("place", pending.placeName)
            put("price_tk", pending.priceTk)
            put("kcal", pending.kcal)
            put("funding_type", pending.fundingType)
            put("shortfall_tk", pending.shortfallTk)
            pending.feastTitle?.let { put("feast_to_cancel", it) }
            put("remainder_to_savings_tk", pending.remainderTk)
            put("instruction", "Tell the student clearly about this swap & funding impact and ask them to tap Confirm below to apply it.")
        }
        return McpToolResult(
            payload = payload,
            displaySummary = "Staged ${pending.itemName} (৳${pending.priceTk}) · awaiting user Confirm"
        )
    }
}

private class LogManualMealTool(
    private val toolsGatekeeper: NutriMateAiToolsGatekeeper,
    private val turnContextProvider: () -> NutriMateTurnToolContext?
) : McpTool {
    override val name = "log_manual_meal"
    override val description =
        "Stages an off-catalog custom meal log for a meal slot so the student can confirm before saving it to their daily plan and community staging."
    override val parameters = mapOf(
        "slot" to Schema.enumeration(
            values = listOf("breakfast", "lunch", "snacks", "dinner"),
            description = "Meal slot to log under"
        ),
        "dish_name" to Schema.string("Name of the dish eaten"),
        "restaurant_name" to Schema.string("Name of the restaurant or stall"),
        "price_tk" to Schema.integer("Amount spent in BDT"),
        "kcal" to Schema.integer("Estimated calories in kcal")
    )
    override val optionalParameters = emptyList<String>()
    override val source = McpToolSource.LOCAL

    override suspend fun execute(args: Map<String, JsonElement>): McpToolResult {
        val turnCtx = turnContextProvider() ?: return McpToolResult(
            payload = buildJsonObject { put("error", "No active context") },
            displaySummary = "Context unavailable",
            isError = true
        )
        val slot = (args.strArg("slot") ?: "lunch").lowercase()
        val dish = args.strArg("dish_name").orEmpty()
        val place = args.strArg("restaurant_name") ?: "Local Stall"
        val priceTk = (args.intArg("price_tk") ?: 0).coerceAtLeast(0)
        val kcal = (args.intArg("kcal") ?: 450).coerceIn(50, 3500)

        if (dish.isBlank() || priceTk <= 0) {
            return McpToolResult(
                payload = buildJsonObject { put("error", "dish_name and positive price_tk are required") },
                displaySummary = "Missing dish name or price",
                isError = true
            )
        }

        val staged = toolsGatekeeper.stageCustomMealProposal(
            slot = slot,
            dishName = dish,
            placeName = place,
            priceTk = priceTk,
            kcal = kcal
        )
        val slotObj = turnCtx.currentSlots.find { it.id.equals(slot, ignoreCase = true) }
        val slotName = slotObj?.name ?: slot.replaceFirstChar { it.uppercase() }

        turnCtx.pendingSwapProposal = PendingChatSwapProposal(
            token = staged.token,
            slotId = staged.slotId,
            slotName = slotName,
            itemName = dish,
            placeName = place,
            priceTk = priceTk,
            kcal = kcal,
            fundingType = "MANUAL_LOG",
            shortfallTk = 0.0,
            feastTitle = null,
            remainderTk = 0.0,
            summary = staged.summary
        )

        val payload = buildJsonObject {
            put("status", "pending_user_confirmation")
            put("slot", slot)
            put("dish_name", dish)
            put("restaurant_name", place)
            put("price_tk", priceTk)
            put("kcal", kcal)
            put("instruction", "Ask the student to tap Confirm below to save this logged meal.")
        }
        return McpToolResult(
            payload = payload,
            displaySummary = "Staged log: $dish (৳$priceTk) · awaiting user Confirm"
        )
    }
}
