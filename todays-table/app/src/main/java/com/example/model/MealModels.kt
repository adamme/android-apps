package com.example.model

import android.graphics.Bitmap
import java.security.MessageDigest

enum class ServiceFilter {
    ALL,
    HELLO_FRESH,
    YELLOW_PICNIC
}

data class UnratedMeal(
    val tab: String,
    val rowNumber: Int,
    val meal: String,
    val recipeUrl: String? = null,
    val imageUrl: String? = null
) {
    val uniqueId: String
        get() = "${tab}_${rowNumber}_${meal.hashCode()}"

    val imageCacheKey: String
        get() {
            val raw = "${tab.lowercase().trim()}::${meal.lowercase().trim()}"
            val bytes = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

    val isHelloFresh: Boolean
        get() = tab.replace(" ", "").equals("HelloFresh", ignoreCase = true)

    val isYellowPicnic: Boolean
        get() = tab.replace(" ", "").equals("YellowPicnic", ignoreCase = true)

    /**
     * Cleans up accidental duplicated trailing phrases in sheet titles for display,
     * while preserving [meal] untouched for POST submission.
     */
    val displayMealName: String
        get() {
            val trimmed = meal.trim()
            val withIndex = trimmed.indexOf("with ", ignoreCase = true)
            if (withIndex > 0) {
                val suffix = trimmed.substring(withIndex)
                val halfLen = suffix.length / 2
                if (halfLen >= 8) {
                    val firstHalf = suffix.substring(0, halfLen).trim()
                    val secondHalf = suffix.substring(halfLen).trim()
                    if (firstHalf.equals(secondHalf, ignoreCase = true)) {
                        return trimmed.substring(0, withIndex) + firstHalf
                    }
                }
            }
            return trimmed
        }
}

data class RateMealRequest(
    val tab: String,
    val rowNumber: Int,
    val meal: String,
    val date: String,
    val anissa: String,
    val adamme: String
)

sealed interface MealImageState {
    data object GenericPlated : MealImageState
    data object SearchingOnline : MealImageState
    data class OnlineImage(val url: String) : MealImageState
    data class AiGenerated(val bitmap: Bitmap) : MealImageState
}

sealed interface MealsUiState {
    data object Loading : MealsUiState
    data class Success(val meals: List<UnratedMeal>) : MealsUiState
    data class Error(val message: String) : MealsUiState
}
