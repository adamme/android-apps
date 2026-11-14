package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.MealRepository
import com.example.model.MealImageState
import com.example.model.MealsUiState
import com.example.model.RateMealRequest
import com.example.model.ServiceFilter
import com.example.model.UnratedMeal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

private const val TAG = "MealViewModel"

class MealViewModel(
    private val repository: MealRepository
) : ViewModel() {

    private val aiGenSemaphore = Semaphore(1)

    private val _webAppUrl = MutableStateFlow(repository.getSavedWebAppUrl())
    val webAppUrl: StateFlow<String> = _webAppUrl.asStateFlow()

    private val _googleSheetUrl = MutableStateFlow(repository.getSavedGoogleSheetUrl())
    val googleSheetUrl: StateFlow<String> = _googleSheetUrl.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(repository.getSavedGeminiApiKey())
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _appsScriptOmitsColumnE = MutableStateFlow(false)
    val appsScriptOmitsColumnE: StateFlow<Boolean> = _appsScriptOmitsColumnE.asStateFlow()

    private val _mealsUiState = MutableStateFlow<MealsUiState>(MealsUiState.Loading)
    val mealsUiState: StateFlow<MealsUiState> = _mealsUiState.asStateFlow()

    private val _serviceFilter = MutableStateFlow(ServiceFilter.ALL)
    val serviceFilter: StateFlow<ServiceFilter> = _serviceFilter.asStateFlow()

    private val _imageStates = MutableStateFlow<Map<String, MealImageState>>(emptyMap())
    val imageStates: StateFlow<Map<String, MealImageState>> = _imageStates.asStateFlow()

    private val _selectedMeal = MutableStateFlow<UnratedMeal?>(null)
    val selectedMeal: StateFlow<UnratedMeal?> = _selectedMeal.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = _submitError.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()
    private var statusBannerDismissJob: Job? = null

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        val cachedMeals = repository.getCachedUnratedMeals()
        if (!cachedMeals.isNullOrEmpty()) {
            val initialImages = mutableMapOf<String, MealImageState>()
            cachedMeals.forEach { meal ->
                repository.getImmediateCachedImageState(meal)?.let { cachedImg ->
                    initialImages[meal.imageCacheKey] = cachedImg
                }
            }
            _imageStates.value = initialImages
            _mealsUiState.value = MealsUiState.Success(cachedMeals)
            cachedMeals.forEach { meal ->
                if (!initialImages.containsKey(meal.imageCacheKey)) {
                    resolveMealImage(meal)
                }
            }
            loadUnratedMeals(showLoadingSpinner = false)
        } else {
            loadUnratedMeals(showLoadingSpinner = true)
        }
    }

    /**
     * Called only while the app is open and visible in the foreground.
     */
    suspend fun syncSheetWhileForeground() {
        if (_isSubmitting.value) return
        try {
            val latestMeals = repository.fetchUnratedMeals(_webAppUrl.value)
            _appsScriptOmitsColumnE.value = !repository.lastAppsScriptIncludedColumnE
            applyMealsUpdate(latestMeals)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Foreground sheet sync skipped: ${e.localizedMessage}")
        }
    }

    private fun applyMealsUpdate(meals: List<UnratedMeal>) {
        val activeKeys = meals.map { it.imageCacheKey }.toSet()
        _imageStates.update { current ->
            current.filterKeys { it in activeKeys }
        }
        meals.forEach { meal ->
            val key = meal.imageCacheKey
            val currentImg = _imageStates.value[key]
            val immediate = repository.getImmediateCachedImageState(meal)
            if (immediate != null &&
                (currentImg == null ||
                    (immediate is MealImageState.OnlineImage && currentImg !is MealImageState.OnlineImage))
            ) {
                updateImageState(key, immediate)
            }
        }
        val currentState = _mealsUiState.value
        if (currentState !is MealsUiState.Success || currentState.meals != meals) {
            _mealsUiState.value = MealsUiState.Success(meals)
        }
        meals.forEach { meal ->
            val existing = _imageStates.value[meal.imageCacheKey]
            if (existing == null) {
                resolveMealImage(meal)
            }
        }
    }

    fun setServiceFilter(filter: ServiceFilter) {
        _serviceFilter.value = filter
    }

    fun selectMealForRating(meal: UnratedMeal?) {
        _submitError.value = null
        _selectedMeal.value = meal
        if (meal != null) {
            val currentImgState = _imageStates.value[meal.imageCacheKey]
            if (currentImgState == null) {
                resolveMealImage(meal)
            }
        }
    }

    fun dismissStatusBanner() {
        statusBannerDismissJob?.cancel()
        statusBannerDismissJob = null
        _statusBanner.value = null
    }

    private fun showTemporaryStatusBanner(message: String, durationMs: Long = 1_000L) {
        statusBannerDismissJob?.cancel()
        _statusBanner.value = message
        statusBannerDismissJob = viewModelScope.launch {
            delay(durationMs)
            _statusBanner.value = null
        }
    }

    fun updateUrls(
        newWebAppUrl: String,
        newGoogleSheetUrl: String,
        newGeminiApiKey: String = _geminiApiKey.value
    ) {
        val cleanedWebApp = newWebAppUrl.trim().ifBlank { MealRepository.DEFAULT_WEB_APP_URL }
        val cleanedSheet = newGoogleSheetUrl.trim()
        val cleanedKey = newGeminiApiKey.trim()
        repository.saveGoogleSheetUrl(cleanedSheet)
        repository.saveWebAppUrl(cleanedWebApp)
        repository.saveGeminiApiKey(cleanedKey)
        _webAppUrl.value = repository.getSavedWebAppUrl()
        _googleSheetUrl.value = repository.getSavedGoogleSheetUrl()
        _geminiApiKey.value = repository.getSavedGeminiApiKey()
        // Clear non-AI image states so YellowPicnic meals generate images with the new key
        _imageStates.update { current ->
            current.filterValues { it is MealImageState.AiGenerated || it is MealImageState.OnlineImage }
        }
        loadUnratedMeals(showLoadingSpinner = true)
    }

    fun resetWebAppUrl() {
        repository.clearLocallyCompletedMeals()
        updateUrls(MealRepository.DEFAULT_WEB_APP_URL, _googleSheetUrl.value)
    }

    fun refreshFromSheet() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.clearLocallyCompletedMeals()
                val meals = repository.fetchUnratedMeals(_webAppUrl.value)
                _appsScriptOmitsColumnE.value = !repository.lastAppsScriptIncludedColumnE
                applyMealsUpdate(meals)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh unrated meals from Google Sheet", e)
                if (_mealsUiState.value !is MealsUiState.Success) {
                    _mealsUiState.value = MealsUiState.Error(
                        e.localizedMessage ?: "Unable to load unrated meals from Google Sheet."
                    )
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadUnratedMeals(showLoadingSpinner: Boolean = true) {
        viewModelScope.launch {
            if (showLoadingSpinner || _mealsUiState.value !is MealsUiState.Success) {
                _mealsUiState.value = MealsUiState.Loading
            }
            try {
                repository.clearLocallyCompletedMeals()
                val meals = repository.fetchUnratedMeals(_webAppUrl.value)
                _appsScriptOmitsColumnE.value = !repository.lastAppsScriptIncludedColumnE
                applyMealsUpdate(meals)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch unrated meals from Google Sheet", e)
                if (_mealsUiState.value !is MealsUiState.Success) {
                    _mealsUiState.value = MealsUiState.Error(
                        e.localizedMessage ?: "Unable to load unrated meals from Google Sheet."
                    )
                }
            }
        }
    }

    fun resolveMealImage(meal: UnratedMeal) {
        viewModelScope.launch {
            val key = meal.imageCacheKey
            try {
                val immediate = repository.getImmediateCachedImageState(meal)
                if (immediate != null) {
                    updateImageState(key, immediate)
                    return@launch
                }

                if (meal.isYellowPicnic) {
                    val hasLocalKey = repository.getSavedGeminiApiKey().isNotBlank()
                    updateImageState(
                        key,
                        if (hasLocalKey) MealImageState.SearchingOnline else MealImageState.GenericPlated
                    )
                    generateYellowPicnicMealImage(meal)
                } else {
                    updateImageState(key, MealImageState.SearchingOnline)
                    val resolved = repository.resolveHelloFreshImage(meal)
                    updateImageState(key, resolved)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image resolution failed for ${meal.meal}", e)
                updateImageState(key, MealImageState.GenericPlated)
            }
        }
    }

    private suspend fun generateYellowPicnicMealImage(meal: UnratedMeal) {
        val key = meal.imageCacheKey
        val prompt = buildPlatedFoodPrompt(meal)

        aiGenSemaphore.withPermit {
            // Check disk cache again in case it was generated while waiting for the semaphore
            val alreadyCached = repository.getCachedAiBitmap(meal)
            if (alreadyCached != null) {
                updateImageState(key, MealImageState.AiGenerated(alreadyCached))
                return@withPermit
            }

            // 1. Try local GEMINI_API_KEY via direct REST API first
            val localApiKeyBitmap = runCatching {
                withTimeout(45_000L) {
                    repository.generatePlatedImageWithLocalApiKey(prompt)
                }
            }.getOrNull()

            if (localApiKeyBitmap != null) {
                repository.saveAiBitmapToCache(meal, localApiKeyBitmap)
                updateImageState(key, MealImageState.AiGenerated(localApiKeyBitmap))
                delay(1_200L)
                return@withPermit
            }

            // Keep clean fallback dish photo if AI image could not be generated
            val fallbackPhoto = runCatching { repository.resolveYellowPicnicFallbackImage(meal) }
                .getOrDefault(MealImageState.GenericPlated)
            updateImageState(key, fallbackPhoto)
        }
    }

    private fun buildPlatedFoodPrompt(meal: UnratedMeal): String {
        return "Overhead food photography of a single clean ceramic dinner plate containing " +
            "a freshly cooked plated meal of ${meal.displayMealName}. " +
            "Simple, beautiful, clean plated food dish on a warm wooden dining table, " +
            "natural daylight, photorealistic culinary presentation, no people, no text, no watermarks."
    }

    private fun updateImageState(key: String, state: MealImageState) {
        _imageStates.update { current ->
            current + (key to state)
        }
    }

    fun submitMealRating(
        meal: UnratedMeal,
        dateString: String,
        anissaChoice: String,
        adammeChoice: String
    ) {
        if (_isSubmitting.value) return
        val trimmedDate = dateString.trim()
        if (trimmedDate.isBlank()) {
            _submitError.value = "Please select a valid date."
            return
        }
        if (anissaChoice.isBlank() || adammeChoice.isBlank()) {
            _submitError.value = "Please select a rating (1–10 or '-') for both Anissa and Adamme."
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _submitError.value = null
            try {
                val request = RateMealRequest(
                    tab = meal.tab,
                    rowNumber = meal.rowNumber,
                    meal = meal.meal,
                    date = trimmedDate,
                    anissa = anissaChoice,
                    adamme = adammeChoice
                )
                repository.submitRating(_webAppUrl.value, request)

                repository.invalidateCacheAfterRating(meal)
                _imageStates.update { current -> current - meal.imageCacheKey }

                _selectedMeal.value = null
                _isSubmitting.value = false
                showTemporaryStatusBanner("Saved “${meal.displayMealName}” (${meal.tab})", 1_000L)

                val currentState = _mealsUiState.value
                if (currentState is MealsUiState.Success) {
                    val remaining = currentState.meals.filterNot {
                        it.tab == meal.tab && it.rowNumber == meal.rowNumber && it.meal == meal.meal
                    }
                    _mealsUiState.value = MealsUiState.Success(remaining)
                    repository.saveCachedUnratedMeals(remaining)
                }

                // Refresh immediately and after row movement settles
                syncSheetWhileForeground()
                delay(1_800L)
                syncSheetWhileForeground()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit meal rating", e)
                _isSubmitting.value = false
                _submitError.value = e.localizedMessage ?: "Failed to save rating to Google Sheet."
            }
        }
    }

    companion object {
        fun formatRelativeDate(daysOffset: Int): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, daysOffset)
            val sdf = SimpleDateFormat("MM/dd/yyyy", Locale.US)
            return sdf.format(cal.time)
        }

        fun formatUtcMillisToDate(utcMillis: Long): String {
            val sdf = SimpleDateFormat("MM/dd/yyyy", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return sdf.format(utcMillis)
        }

        fun provideFactory(repository: MealRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MealViewModel(repository)
                }
            }
    }
}
