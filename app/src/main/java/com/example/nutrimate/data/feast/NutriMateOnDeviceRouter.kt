package com.example.nutrimate.data.feast

import android.util.Log

/**
 * Tier 1: On-Device Router & Classifier.
 * Handles Step 1 locally on-device: classifies user intent and extracts slot/item entities
 * in milliseconds (<15ms) to eliminate multi-turn cloud round-trips.
 */
class NutriMateOnDeviceRouter {

    data class RouterResult(
        val isPlanChange: Boolean,
        val slot: String = "lunch",
        val query: String = "",
        val maxPriceTk: Int? = null,
        val confidence: Float = 0.95f,
        val detectedKeywords: List<String> = emptyList(),
        val classificationTimeMs: Long = 0L
    )

    /**
     * Inspects user prompt locally to identify whether it's a meal plan mutation
     * or a general food/nutrition inquiry.
     */
    fun classify(prompt: String): RouterResult {
        val startTime = System.currentTimeMillis()
        val lower = prompt.trim().lowercase()

        // 1. Detect if it's explicitly a nutrition/advice inquiry (Food question)
        val isFoodQuestion = isNutritionalQuestion(lower)
        if (isFoodQuestion) {
            val elapsed = System.currentTimeMillis() - startTime
            return RouterResult(
                isPlanChange = false,
                classificationTimeMs = elapsed.coerceAtLeast(1L)
            )
        }

        // 2. Detect meal change indicators & popular Bangladeshi food items
        val changeKeywords = listOf(
            "swap", "change", "want", "eat", "have", "replace", "switch", "order",
            "get", "choose", "put", "take", "add", "craving", "kacchi", "biriyani",
            "khichuri", "burger", "paratha", "rice", "curry", "noodles", "roast",
            "roll", "pasta", "shingara", "beef", "chicken", "fish", "dal", "dim", "egg",
            "polao", "tehari", "morog", "kabab", "naan", "grill", "haleem", "fuchka",
            "chotpoti", "somosa", "cha", "tea", "lassi", "borhani", "fruit"
        )

        val matchedKeywords = changeKeywords.filter { lower.contains(it) }
        val hasChangeKeyword = matchedKeywords.isNotEmpty()

        // Detect meal slots (including Bengali terms)
        val slot = when {
            lower.contains("breakfast") || lower.contains("shokal") || lower.contains("morning") || lower.contains("nashta") -> "breakfast"
            lower.contains("dinner") || lower.contains("raat") || lower.contains("night") || lower.contains("ranna") -> "dinner"
            lower.contains("snack") || lower.contains("snacks") || lower.contains("bikeler") || lower.contains("bikel") || lower.contains("cha") -> "snacks"
            lower.contains("lunch") || lower.contains("dupur") || lower.contains("noon") -> "lunch"
            else -> "lunch" // Default slot
        }

        val hasSlotMention = lower.contains("breakfast") || lower.contains("lunch") ||
                lower.contains("snack") || lower.contains("dinner") ||
                lower.contains("shokal") || lower.contains("dupur") ||
                lower.contains("bikel") || lower.contains("raat")

        if (!hasChangeKeyword && !hasSlotMention && !lower.contains("cheaper") && !lower.contains("alternative")) {
            // General food question or conversational remark
            val elapsed = System.currentTimeMillis() - startTime
            return RouterResult(
                isPlanChange = false,
                classificationTimeMs = elapsed.coerceAtLeast(1L)
            )
        }

        // Extract food query by stripping stop words and slot words
        val extractedQuery = extractFoodQuery(lower)

        // Extract max price constraint if specified (e.g. "under 100", "below 80 tk", "within 120", "max 75")
        val maxPrice = extractMaxPrice(lower)

        val isPlanMutation = extractedQuery.isNotBlank() ||
                lower.contains("cheaper") ||
                lower.contains("alternative") ||
                hasChangeKeyword

        val elapsed = System.currentTimeMillis() - startTime
        return RouterResult(
            isPlanChange = isPlanMutation,
            slot = slot,
            query = extractedQuery,
            maxPriceTk = maxPrice,
            confidence = if (hasSlotMention && hasChangeKeyword) 0.98f else 0.90f,
            detectedKeywords = matchedKeywords,
            classificationTimeMs = elapsed.coerceAtLeast(1L)
        )
    }

    private fun isNutritionalQuestion(text: String): Boolean {
        val questionTriggers = listOf(
            "is a protein", "is protein", "worth it", "how much protein", "how many calories",
            "is it healthy", "nutrition", "benefit", "explain", "tell me about", "what is",
            "why should", "advice", "suggest general", "difference between", "gym", "creatine",
            "how to gain", "how to lose", "macro", "macros", "diet plan"
        )
        return questionTriggers.any { text.contains(it) }
    }

    private fun extractFoodQuery(text: String): String {
        var cleaned = text

        // Remove slot words
        val slots = listOf(
            "breakfast", "lunch", "snacks", "snack", "dinner", "morning", "night", "noon",
            "shokal", "dupur", "bikel", "raat", "bikeler"
        )
        for (s in slots) {
            cleaned = cleaned.replace(Regex("\\b$s\\b"), " ")
        }

        // Remove filler / change command phrases
        val fillers = listOf(
            "i want to eat", "i want to have", "i want", "can i have", "can i get", "can i order",
            "swap my", "swap with", "swap to", "swap", "change my", "change to", "change",
            "replace my", "replace with", "replace to", "replace",
            "switch my", "switch to", "switch",
            "how about", "what about", "craving for", "craving", "order", "give me",
            "something cheaper for", "something cheaper", "cheaper", "for", "my", "to",
            "today", "please", "tk", "taka", "under", "within", "below", "max", "less than"
        )
        for (f in fillers) {
            cleaned = cleaned.replace(Regex("\\b$f\\b"), " ")
        }

        // Remove numbers and punctuation
        cleaned = cleaned.replace(Regex("[0-9.,?!]"), " ")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        return cleaned
    }

    private fun extractMaxPrice(text: String): Int? {
        val priceRegex = Regex("(?:under|below|within|max|less than)\\s*(?:tk|৳)?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val match = priceRegex.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}
