package com.example.nutrimate.data.feast

import android.util.Log

/**
 * Tier 1: On-Device Router & Classifier.
 * Handles Step 1 locally on-device: classifies user intent and extracts slot/item entities
 * in milliseconds to eliminate multi-turn cloud round-trips.
 */
class NutriMateOnDeviceRouter {

    data class RouterResult(
        val isPlanChange: Boolean,
        val slot: String = "lunch",
        val query: String = "",
        val maxPriceTk: Int? = null,
        val confidence: Float = 0.9f
    )

    /**
     * Inspects user prompt locally to identify whether it's a meal plan mutation
     * or a food/nutrition inquiry.
     */
    fun classify(prompt: String): RouterResult {
        val lower = prompt.trim().lowercase()

        // 1. Detect if it's explicitly a nutrition/advice inquiry (Food question)
        val isFoodQuestion = isNutritionalQuestion(lower)
        if (isFoodQuestion) {
            return RouterResult(isPlanChange = false)
        }

        // 2. Detect meal change indicators
        val changeKeywords = listOf(
            "swap", "change", "want", "eat", "have", "replace", "switch", "order",
            "get", "choose", "put", "take", "add", "craving", "kacchi", "biriyani",
            "khichuri", "burger", "paratha", "rice", "curry", "noodles", "roast",
            "roll", "pasta", "shingara", "beef", "chicken", "fish", "dal", "dim", "egg"
        )

        val hasChangeKeyword = changeKeywords.any { lower.contains(it) }

        // Detect meal slots
        val slot = when {
            lower.contains("breakfast") || lower.contains("shokal") || lower.contains("morning") -> "breakfast"
            lower.contains("dinner") || lower.contains("raat") || lower.contains("night") -> "dinner"
            lower.contains("snack") || lower.contains("snacks") || lower.contains("bikeler") -> "snacks"
            lower.contains("lunch") || lower.contains("dupur") || lower.contains("noon") -> "lunch"
            else -> "lunch" // Default slot
        }

        val hasSlotMention = lower.contains("breakfast") || lower.contains("lunch") ||
                lower.contains("snack") || lower.contains("dinner")

        if (!hasChangeKeyword && !hasSlotMention) {
            // General food question or conversational remark
            return RouterResult(isPlanChange = false)
        }

        // Extract food query by stripping stop words and slot words
        val extractedQuery = extractFoodQuery(lower)

        // Extract max price constraint if specified (e.g. "under 100", "below 80 tk", "within 120")
        val maxPrice = extractMaxPrice(lower)

        return RouterResult(
            isPlanChange = extractedQuery.isNotBlank() || lower.contains("cheaper") || lower.contains("alternative"),
            slot = slot,
            query = extractedQuery,
            maxPriceTk = maxPrice
        )
    }

    private fun isNutritionalQuestion(text: String): Boolean {
        val questionTriggers = listOf(
            "is a protein", "is protein", "worth it", "how much protein", "how many calories",
            "is it healthy", "nutrition", "benefit", "explain", "tell me about", "what is",
            "why should", "advice", "suggest general", "difference between", "gym", "creatine"
        )
        return questionTriggers.any { text.contains(it) }
    }

    private fun extractFoodQuery(text: String): String {
        var cleaned = text

        // Remove slot words
        val slots = listOf("breakfast", "lunch", "snacks", "snack", "dinner", "morning", "night", "noon")
        for (s in slots) {
            cleaned = cleaned.replace(Regex("\\b$s\\b"), " ")
        }

        // Remove filler / change command phrases
        val fillers = listOf(
            "i want to eat", "i want", "can i have", "can i get", "swap my", "swap", "change my", "change",
            "replace my", "replace with", "replace", "switch my", "switch to", "switch",
            "how about", "what about", "craving for", "craving", "order", "give me",
            "something cheaper for", "something cheaper", "cheaper", "for", "my", "to",
            "today", "please", "tk", "taka", "under", "within", "below"
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
