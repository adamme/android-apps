package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import com.example.BuildConfig
import com.example.model.MealImageState
import com.example.model.RateMealRequest
import com.example.model.UnratedMeal
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "MealRepository"

class MealRepository(private val context: Context) {

    companion object {
        const val DEFAULT_WEB_APP_URL =
            "https://script.google.com/macros/s/YOUR_DEPLOYMENT_ID/exec"
        const val DEFAULT_GOOGLE_SHEET_URL =
            "https://docs.google.com/spreadsheets/d/YOUR_SPREADSHEET_ID/edit?gid=0#gid=0"
        const val DEFAULT_GEMINI_API_KEY = ""

        private const val PREFS_NAME = "meal_rater_prefs"
        private const val KEY_WEB_APP_URL = "web_app_url"
        private const val KEY_GOOGLE_SHEET_URL = "google_sheet_url"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_CACHED_MEALS_JSON = "cached_unrated_meals_v6"
        private const val KEY_URL_CACHE_PREFIX = "hf_img_url_v5_"
        private const val KEY_LOCALLY_COMPLETED_MEALS = "locally_completed_meals_v1"

        private val AVERAGE_KEYS = setOf(
            "average", "avg", "columne", "cole", "col_e", "e", "average_rating", "mealaverage"
        )
        private val RATING_KEYS = setOf(
            "anissa", "adamme", "columnc", "colc", "col_c", "columnd", "cold", "col_d"
        )
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val aiImageCacheDir: File by lazy {
        File(context.filesDir, "yp_ai_images_v4").apply {
            if (!exists()) mkdirs()
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val postNoRedirectClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    @Volatile
    var lastAppsScriptIncludedColumnE: Boolean = false
        private set

    @Volatile
    private var imageApiBillingRequired: Boolean = false

    fun getSavedWebAppUrl(): String {
        return prefs.getString(KEY_WEB_APP_URL, DEFAULT_WEB_APP_URL)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_WEB_APP_URL
    }

    fun saveWebAppUrl(url: String) {
        val trimmed = url.trim()
        if (extractSpreadsheetId(trimmed) != null && !trimmed.contains("script.google.com")) {
            saveGoogleSheetUrl(trimmed)
            return
        }
        prefs.edit()
            .putString(KEY_WEB_APP_URL, trimmed.ifBlank { DEFAULT_WEB_APP_URL })
            .remove(KEY_CACHED_MEALS_JSON)
            .apply()
    }

    fun getSavedGoogleSheetUrl(): String {
        return prefs.getString(KEY_GOOGLE_SHEET_URL, DEFAULT_GOOGLE_SHEET_URL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_GOOGLE_SHEET_URL
    }

    fun saveGoogleSheetUrl(url: String) {
        prefs.edit()
            .putString(KEY_GOOGLE_SHEET_URL, url.trim())
            .remove(KEY_CACHED_MEALS_JSON)
            .apply()
    }

    fun getSavedGeminiApiKey(): String {
        val fromPrefs = prefs.getString(KEY_GEMINI_API_KEY, null)?.trim().orEmpty()
        if (fromPrefs.isNotBlank()) return fromPrefs
        val fromBuildConfig = runCatching { BuildConfig.GEMINI_API_KEY }.getOrNull()?.trim().orEmpty()
        if (fromBuildConfig.isNotBlank() && fromBuildConfig != "MY_GEMINI_API_KEY") {
            return fromBuildConfig
        }
        return DEFAULT_GEMINI_API_KEY
    }

    fun saveGeminiApiKey(apiKey: String) {
        imageApiBillingRequired = false
        prefs.edit()
            .putString(KEY_GEMINI_API_KEY, apiKey.trim())
            .apply()
    }

    fun clearLocallyCompletedMeals() {
        prefs.edit().remove(KEY_LOCALLY_COMPLETED_MEALS).apply()
    }

    internal fun normalizeTabMealKey(tab: String, mealName: String): String {
        val cleanTab = tab.replace(" ", "").lowercase(Locale.US)
        val cleanMeal = mealName.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
        return "${cleanTab}::$cleanMeal"
    }

    fun getCachedUnratedMeals(): List<UnratedMeal>? {
        val rawJson = prefs.getString(KEY_CACHED_MEALS_JSON, null) ?: return null
        return runCatching {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<UnratedMeal>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val mealName = obj.optString("meal", "").trim()
                if (mealName.isBlank()) continue
                if (isMealCompletedInJsonItem(obj)) continue
                val tab = obj.optString("tab", "HelloFresh")
                val isYellowPicnic = tab.replace(" ", "").equals("YellowPicnic", ignoreCase = true)
                val rawImageUrl = obj.optString("imageUrl", "").trim().takeIf { it.isNotBlank() }
                val rawRecipeUrl = obj.optString("recipeUrl", "").trim().takeIf { it.isNotBlank() }
                val resolvedImageUrl = if (isYellowPicnic) {
                    rawImageUrl?.takeIf { isYellowPicnicProductImageUrl(it) }
                        ?: rawRecipeUrl?.takeIf { isYellowPicnicProductImageUrl(it) }
                } else {
                    rawImageUrl
                }
                list.add(
                    UnratedMeal(
                        tab = tab,
                        rowNumber = obj.optInt("rowNumber", i + 2),
                        meal = mealName,
                        recipeUrl = rawRecipeUrl?.takeUnless { isYellowPicnic && isYellowPicnicProductImageUrl(it) },
                        imageUrl = resolvedImageUrl
                    )
                )
            }
            list
        }.getOrNull()
    }

    fun saveCachedUnratedMeals(meals: List<UnratedMeal>) {
        runCatching {
            val arr = JSONArray()
            meals.forEach { meal ->
                arr.put(
                    JSONObject().apply {
                        put("tab", meal.tab)
                        put("rowNumber", meal.rowNumber)
                        put("meal", meal.meal)
                        put("recipeUrl", meal.recipeUrl.orEmpty())
                        put(
                            "imageUrl",
                            if (meal.isYellowPicnic) {
                                meal.imageUrl?.takeIf { isYellowPicnicProductImageUrl(it) }.orEmpty()
                            } else {
                                meal.imageUrl.orEmpty()
                            }
                        )
                    }
                )
            }
            prefs.edit().putString(KEY_CACHED_MEALS_JSON, arr.toString()).apply()
            pruneStaleImageCaches(meals)
        }
    }

    fun invalidateCacheAfterRating(ratedMeal: UnratedMeal) {
        val key = ratedMeal.imageCacheKey
        val cachedUrl = prefs.getString("$KEY_URL_CACHE_PREFIX$key", null)
        prefs.edit()
            .remove(KEY_CACHED_MEALS_JSON)
            .remove(KEY_LOCALLY_COMPLETED_MEALS)
            .remove("$KEY_URL_CACHE_PREFIX$key")
            .apply()

        evictUrlFromCoilCache(cachedUrl)
        evictUrlFromCoilCache(ratedMeal.imageUrl)

        runCatching {
            val aiFile = File(aiImageCacheDir, "ai_$key.png")
            if (aiFile.exists()) aiFile.delete()
        }
    }

    fun pruneStaleImageCaches(activeUnratedMeals: List<UnratedMeal>) {
        val activeKeys = activeUnratedMeals.map { it.imageCacheKey }.toSet()

        runCatching {
            aiImageCacheDir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("ai_") && name.endsWith(".png")) {
                    val key = name.removePrefix("ai_").removeSuffix(".png")
                    if (key !in activeKeys) {
                        file.delete()
                    }
                }
            }
        }

        runCatching {
            val allPrefs = prefs.all
            val stalePrefKeys = mutableListOf<String>()
            val urlsToEvict = mutableListOf<String>()
            for ((prefKey, value) in allPrefs) {
                if (prefKey.startsWith(KEY_URL_CACHE_PREFIX)) {
                    val mealKey = prefKey.removePrefix(KEY_URL_CACHE_PREFIX)
                    if (mealKey !in activeKeys) {
                        stalePrefKeys.add(prefKey)
                        (value as? String)?.takeIf { it.isNotBlank() }?.let { urlsToEvict.add(it) }
                    }
                }
            }
            if (stalePrefKeys.isNotEmpty()) {
                val editor = prefs.edit()
                stalePrefKeys.forEach { editor.remove(it) }
                editor.apply()
            }
            urlsToEvict.forEach { evictUrlFromCoilCache(it) }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun evictUrlFromCoilCache(url: String?) {
        if (url.isNullOrBlank()) return
        runCatching {
            val loader = context.imageLoader
            loader.memoryCache?.remove(MemoryCache.Key(url))
            loader.diskCache?.remove(url)
        }
    }

    fun getCachedAiBitmap(meal: UnratedMeal): Bitmap? {
        val file = File(aiImageCacheDir, "ai_${meal.imageCacheKey}.png")
        if (file.exists()) {
            return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        }
        return null
    }

    fun saveAiBitmapToCache(meal: UnratedMeal, bitmap: Bitmap) {
        runCatching {
            val file = File(aiImageCacheDir, "ai_${meal.imageCacheKey}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to cache YellowPicnic AI bitmap", e)
        }
    }

    internal fun isYellowPicnicProductImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (!trimmed.startsWith("http", ignoreCase = true)) return false
        if (trimmed.contains("yellowpicnic.com/wp-content/uploads/", ignoreCase = true)) {
            return true
        }
        return Regex("""\.(?:jpe?g|png|webp|gif|avif)(?:[?#]|$)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(trimmed)
    }

    fun getImmediateCachedImageState(meal: UnratedMeal): MealImageState? {
        if (meal.isYellowPicnic && isYellowPicnicProductImageUrl(meal.imageUrl)) {
            val directUrl = meal.imageUrl!!.trim()
            prefs.edit().putString("$KEY_URL_CACHE_PREFIX${meal.imageCacheKey}", directUrl).apply()
            return MealImageState.OnlineImage(directUrl)
        }
        val cachedBitmap = getCachedAiBitmap(meal)
        if (cachedBitmap != null) {
            return MealImageState.AiGenerated(cachedBitmap)
        }
        val key = meal.imageCacheKey
        val cachedUrl = prefs.getString("$KEY_URL_CACHE_PREFIX$key", null)
        if (!cachedUrl.isNullOrBlank() && cachedUrl.startsWith("http")) {
            return MealImageState.OnlineImage(cachedUrl)
        }
        if (!meal.imageUrl.isNullOrBlank() && meal.imageUrl.startsWith("http")) {
            return MealImageState.OnlineImage(meal.imageUrl)
        }
        return null
    }

    suspend fun generatePlatedImageWithLocalApiKey(prompt: String): Bitmap? =
        withContext(Dispatchers.IO) {
            if (imageApiBillingRequired) {
                return@withContext null
            }
            val apiKey = getSavedGeminiApiKey()
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext null
            }

            val modelsToTry = listOf(
                "gemini-3.1-flash-lite-image",
                "gemini-nano-banana-2.1"
            )

            for (modelName in modelsToTry) {
                val bitmap = callGeminiRestImageApi(apiKey, modelName, prompt)
                if (bitmap != null) {
                    return@withContext bitmap
                }
                if (imageApiBillingRequired) {
                    break
                }
            }
            null
        }

    suspend fun resolveYellowPicnicFallbackImage(meal: UnratedMeal): MealImageState =
        withContext(Dispatchers.IO) {
            val cachedUrl = prefs.getString("$KEY_URL_CACHE_PREFIX${meal.imageCacheKey}", null)
            if (!cachedUrl.isNullOrBlank() && cachedUrl.startsWith("http")) {
                return@withContext MealImageState.OnlineImage(cachedUrl)
            }

            val apiKey = getSavedGeminiApiKey()
            val dishQuery = if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                generateDishPhotoQueryWithGemini(apiKey, meal.displayMealName)
            } else {
                null
            } ?: "${meal.displayMealName} cooked plated dish food photography"

            val encoded = URLEncoder.encode(dishQuery, "UTF-8")
            val photoUrl = "https://tse2.mm.bing.net/th?q=$encoded&w=800&h=500&c=7&rs=1&p=0"
            prefs.edit().putString("$KEY_URL_CACHE_PREFIX${meal.imageCacheKey}", photoUrl).apply()
            MealImageState.OnlineImage(photoUrl)
        }

    private fun generateDishPhotoQueryWithGemini(apiKey: String, mealName: String): String? {
        return runCatching {
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"
            val promptText =
                "Write a 5 to 7 word food photography search query for a plated restaurant dish of: \"$mealName\". " +
                    "Return ONLY the plain search words, no quotes, no punctuation."
            val bodyJson = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", promptText))
                        )
                    )
                )
            }
            val req = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = JSONObject(resp.body?.string() ?: return null)
                val text = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                    ?.trim()
                    ?.replace("\"", "")
                    ?.replace("\n", " ")
                text?.takeIf { it.length in 5..80 }
            }
        }.getOrNull()
    }

    private fun callGeminiRestImageApi(
        apiKey: String,
        modelName: String,
        prompt: String
    ): Bitmap? {
        return runCatching {
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val bodyJson = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt))
                        )
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().put(
                        "responseModalities",
                        JSONArray().put("TEXT").put("IMAGE")
                    )
                )
            }

            val req = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Local Gemini REST image call ($modelName) HTTP ${resp.code}")
                    if (resp.code == 402) {
                        imageApiBillingRequired = true
                    }
                    return null
                }
                val respStr = resp.body?.string() ?: return null
                val root = JSONObject(respStr)
                val candidates = root.optJSONArray("candidates") ?: return null
                for (i in 0 until candidates.length()) {
                    val parts = candidates.optJSONObject(i)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?: continue
                    for (j in 0 until parts.length()) {
                        val inlineData = parts.optJSONObject(j)?.optJSONObject("inlineData")
                            ?: parts.optJSONObject(j)?.optJSONObject("inline_data")
                            ?: continue
                        val b64 = inlineData.optString("data", "")
                        if (b64.isNotBlank()) {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (decoded != null) return decoded
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    suspend fun fetchUnratedMeals(webAppUrl: String): List<UnratedMeal> =
        withContext(Dispatchers.IO) {
            val baseUrl = webAppUrl.trim()
            val separator = if (baseUrl.contains("?")) "&" else "?"
            val requestUrl = "${baseUrl}${separator}_ts=${System.currentTimeMillis()}"

            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .get()
                .build()

            val appsScriptMeals = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server returned HTTP ${response.code}: ${response.message}")
                }
                val bodyString = response.body?.string()
                    ?: throw IOException("Empty response from Google Sheet Web App.")

                parseMealsResponse(bodyString)
            }

            // Google Sheet is the single source of truth; clear any legacy local blocklist
            clearLocallyCompletedMeals()

            val sheetUrl = getSavedGoogleSheetUrl()
            val spreadsheetId = extractSpreadsheetId(sheetUrl)
                ?: extractSpreadsheetId(baseUrl)

            val finalMeals = if (!spreadsheetId.isNullOrBlank()) {
                filterMealsUsingDirectSheetColumnE(spreadsheetId, appsScriptMeals)
            } else {
                appsScriptMeals
            }

            saveCachedUnratedMeals(finalMeals)
            finalMeals
        }

    internal fun extractSpreadsheetId(urlOrId: String): String? {
        val trimmed = urlOrId.trim()
        if (trimmed.isBlank()) return null
        val match = Regex("""/spreadsheets/d/([a-zA-Z0-9-_]+)""").find(trimmed)
        if (match != null) {
            return match.groupValues[1]
        }
        if (!trimmed.startsWith("http") && trimmed.length in 25..64 &&
            trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        ) {
            return trimmed
        }
        return null
    }

    private fun filterMealsUsingDirectSheetColumnE(
        spreadsheetId: String,
        candidates: List<UnratedMeal>
    ): List<UnratedMeal> {
        if (candidates.isEmpty()) return candidates
        val tabs = candidates.map { it.tab }.distinct()
        val gidByTab = fetchSheetTabGids(spreadsheetId)

        val csvRowsByTab = mutableMapOf<String, List<List<String>>>()
        for (tab in tabs) {
            val normTab = tab.replace(" ", "").lowercase(Locale.US)
            val gid = gidByTab[normTab]
            val csvText = fetchTabCsv(spreadsheetId, tab, gid)
            if (!csvText.isNullOrBlank()) {
                csvRowsByTab[normTab] = parseCsv(csvText)
            }
        }

        if (csvRowsByTab.isEmpty()) return candidates

        return candidates.filter { meal ->
            val normTab = meal.tab.replace(" ", "").lowercase(Locale.US)
            val rows = csvRowsByTab[normTab] ?: return@filter true
            !isMealCompletedInCsvRows(rows, meal.rowNumber, meal.meal)
        }
    }

    internal fun isMealCompletedInCsvRows(
        rows: List<List<String>>,
        rowNumber: Int,
        mealName: String
    ): Boolean {
        val targetMealNorm = mealName.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")

        val exactRow = rows.getOrNull(rowNumber - 1)
        if (exactRow != null) {
            val rowMeal = exactRow.getOrNull(1)?.trim().orEmpty()
                .lowercase(Locale.US).replace(Regex("\\s+"), " ")
            if (rowMeal.isNotEmpty() && targetMealNorm.isNotEmpty() &&
                (rowMeal == targetMealNorm || rowMeal.contains(targetMealNorm) || targetMealNorm.contains(rowMeal))
            ) {
                return isCsvRowRatedOrHasAverage(exactRow)
            }
        }

        val matchingRows = rows.filter { row ->
            val colB = row.getOrNull(1)?.trim().orEmpty()
                .lowercase(Locale.US).replace(Regex("\\s+"), " ")
            colB.isNotEmpty() && colB != "meal" && colB == targetMealNorm
        }

        if (matchingRows.isNotEmpty()) {
            return matchingRows.first().let { isCsvRowRatedOrHasAverage(it) }
        }

        return false
    }

    internal fun isCsvRowRatedOrHasAverage(row: List<String>): Boolean {
        val colCAnissa = row.getOrNull(2)?.trim().orEmpty()
        val colDAdamme = row.getOrNull(3)?.trim().orEmpty()
        val colEAverage = row.getOrNull(4)?.trim().orEmpty()

        if (hasAnyCellContent(colEAverage)) return true
        if (hasAnyCellContent(colCAnissa)) return true
        if (hasAnyCellContent(colDAdamme)) return true
        return false
    }

    private fun fetchSheetTabGids(spreadsheetId: String): Map<String, String> {
        return runCatching {
            val url = "https://docs.google.com/spreadsheets/d/$spreadsheetId/htmlview"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyMap()
                val html = resp.body?.string() ?: return emptyMap()
                val result = mutableMapOf<String, String>()
                val regex1 = Regex("""name\s*:\s*["']([^"']+)["'][^}]{0,200}?gid\s*:\s*["']?(\d+)["']?""")
                regex1.findAll(html).forEach { m ->
                    val name = m.groupValues[1].replace(" ", "").lowercase(Locale.US)
                    val gid = m.groupValues[2]
                    result[name] = gid
                }
                result
            }
        }.getOrDefault(emptyMap())
    }

    private fun fetchTabCsv(spreadsheetId: String, tabName: String, gid: String?): String? {
        if (!gid.isNullOrBlank()) {
            val exportUrl =
                "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv&gid=$gid&_ts=${System.currentTimeMillis()}"
            val csv = runCatching {
                val req = Request.Builder().url(exportUrl).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull()
            if (!csv.isNullOrBlank() && !csv.trimStart().startsWith("<!DOCTYPE", ignoreCase = true)) {
                return csv
            }
        }

        val encodedSheet = URLEncoder.encode(tabName, "UTF-8")
        val gvizUrl =
            "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&sheet=$encodedSheet&_ts=${System.currentTimeMillis()}"
        return runCatching {
            val req = Request.Builder().url(gvizUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()?.takeIf { !it.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) }
    }

    internal fun parseCsv(csvText: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = csvText.length

        while (i < len) {
            val ch = csvText[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && csvText[i + 1] == '"') {
                        currentField.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < len && csvText[i + 1] == '\n') {
                            i++
                        }
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    else -> currentField.append(ch)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            rows.add(currentRow)
        }
        return rows
    }

    internal fun parseMealsResponse(jsonString: String): List<UnratedMeal> {
        val trimmedBody = jsonString.trim()
        if (trimmedBody.startsWith("<!DOCTYPE", ignoreCase = true) || trimmedBody.startsWith("<html", ignoreCase = true)) {
            val scriptErr = Regex("""(?:Script function not found:[^<]+|Exception:[^<]+|ReferenceError:[^<]+|SyntaxError:[^<]+|TypeError:[^<]+)""", RegexOption.IGNORE_CASE)
                .find(trimmedBody)?.value?.trim()
            val detail = scriptErr ?: if (trimmedBody.contains("accounts.google.com") || trimmedBody.contains("Sign in")) {
                "Apps Script deployment requires 'Who has access: Anyone'."
            } else {
                "Apps Script returned an HTML error page instead of JSON. Make sure the full Code.gs (including doGet) is pasted and deployed."
            }
            throw IOException(detail)
        }
        val root = JSONObject(trimmedBody)
        if (root.has("error") && !root.isNull("error")) {
            val err = root.optString("error")
            if (err.isNotBlank()) {
                throw IOException(err)
            }
        }

        val mealsArray = root.optJSONArray("meals") ?: return emptyList()
        val result = mutableListOf<UnratedMeal>()
        var sawColumnEField = false

        for (i in 0 until mealsArray.length()) {
            val item = mealsArray.optJSONObject(i) ?: continue
            val mealName = item.optString("meal", "").trim()
            if (mealName.isBlank()) continue

            if (jsonItemContainsColumnEKey(item)) {
                sawColumnEField = true
            }

            if (isMealCompletedInJsonItem(item)) {
                continue
            }

            val tab = item.optString("tab", "HelloFresh").trim().ifBlank { "HelloFresh" }
            val isYellowPicnic = tab.replace(" ", "").equals("YellowPicnic", ignoreCase = true)
            val rowNumber = item.optInt("rowNumber", i + 2)
            val rawRecipeUrl = item.optString("recipeUrl", "").trim()
                .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            val rawImageUrl = item.optString("imageUrl", "").trim()
                .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

            val imageUrl = if (isYellowPicnic) {
                rawImageUrl?.takeIf { isYellowPicnicProductImageUrl(it) }
                    ?: rawRecipeUrl?.takeIf { isYellowPicnicProductImageUrl(it) }
            } else {
                rawImageUrl
            }
            val recipeUrl = rawRecipeUrl?.takeUnless { isYellowPicnic && isYellowPicnicProductImageUrl(it) }

            result.add(
                UnratedMeal(
                    tab = tab,
                    rowNumber = rowNumber,
                    meal = mealName,
                    recipeUrl = recipeUrl,
                    imageUrl = imageUrl
                )
            )
        }

        lastAppsScriptIncludedColumnE = sawColumnEField
        return result
    }

    private fun jsonItemContainsColumnEKey(item: JSONObject): Boolean {
        val keys = item.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val norm = k.trim().lowercase(Locale.US).replace(" ", "")
            if (norm in AVERAGE_KEYS || norm == "row" || norm == "values" || norm == "cells") {
                return true
            }
        }
        return false
    }

    internal fun isMealCompletedInJsonItem(item: JSONObject): Boolean {
        val keys = item.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (item.isNull(key)) continue
            val normKey = key.trim().lowercase(Locale.US).replace(" ", "")

            if (normKey in AVERAGE_KEYS || normKey in RATING_KEYS) {
                val rawVal = item.opt(key)?.toString()?.trim().orEmpty()
                if (hasAnyCellContent(rawVal)) {
                    return true
                }
            }

            if (normKey == "row" || normKey == "values" || normKey == "cells") {
                val arr = item.optJSONArray(key)
                if (arr != null) {
                    for (colIdx in listOf(2, 3, 4)) {
                        if (colIdx < arr.length() && !arr.isNull(colIdx)) {
                            val cellStr = arr.opt(colIdx)?.toString()?.trim().orEmpty()
                            if (hasAnyCellContent(cellStr)) {
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    private fun hasAnyCellContent(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.equals("null", ignoreCase = true)) return false
        if (trimmed.equals("undefined", ignoreCase = true)) return false
        return true
    }

    suspend fun submitRating(webAppUrl: String, ratingRequest: RateMealRequest) =
        withContext(Dispatchers.IO) {
            val anissaInt = ratingRequest.anissa.trim().toIntOrNull()
            val adammeInt = ratingRequest.adamme.trim().toIntOrNull()

            val payload = JSONObject().apply {
                put("tab", ratingRequest.tab)
                put("rowNumber", ratingRequest.rowNumber)
                put("meal", ratingRequest.meal)
                put("date", ratingRequest.date)
                if (anissaInt != null) {
                    put("anissa", anissaInt)
                } else {
                    put("anissa", ratingRequest.anissa.trim().ifBlank { "-" })
                }
                if (adammeInt != null) {
                    put("adamme", adammeInt)
                } else {
                    put("adamme", ratingRequest.adamme.trim().ifBlank { "-" })
                }
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)

            val postReq = Request.Builder()
                .url(webAppUrl.trim())
                .post(requestBody)
                .build()

            postNoRedirectClient.newCall(postReq).execute().use { initialResponse ->
                if (initialResponse.code in 300..399) {
                    val redirectUrl = initialResponse.header("Location")
                    if (!redirectUrl.isNullOrBlank()) {
                        val getRedirectReq = Request.Builder()
                            .url(redirectUrl)
                            .get()
                            .build()
                        httpClient.newCall(getRedirectReq).execute().use { redirectedResponse ->
                            validatePostResponse(
                                redirectedResponse.code,
                                redirectedResponse.isSuccessful,
                                redirectedResponse.body?.string()
                            )
                        }
                        return@withContext
                    }
                }
                validatePostResponse(
                    initialResponse.code,
                    initialResponse.isSuccessful,
                    initialResponse.body?.string()
                )
            }
        }

    private fun validatePostResponse(code: Int, isSuccessful: Boolean, body: String?) {
        if (!isSuccessful && code !in 200..399) {
            throw IOException("Failed to save rating (HTTP $code).")
        }
        if (!body.isNullOrBlank() && body.trim().startsWith("{")) {
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (json != null) {
                val status = json.optString("status", "")
                val error = json.optString("error", "")
                if (status.equals("error", ignoreCase = true) || error.isNotBlank()) {
                    throw IOException(error.ifBlank { "Apps Script returned an error while saving." })
                }
            }
        }
    }

    suspend fun resolveHelloFreshImage(meal: UnratedMeal): MealImageState =
        withContext(Dispatchers.IO) {
            if (!meal.imageUrl.isNullOrBlank() && meal.imageUrl.startsWith("http") &&
                !meal.imageUrl.contains("hellofresh-logo", ignoreCase = true)
            ) {
                prefs.edit().putString("$KEY_URL_CACHE_PREFIX${meal.imageCacheKey}", meal.imageUrl).apply()
                return@withContext MealImageState.OnlineImage(meal.imageUrl)
            }

            val cachedUrl = prefs.getString("$KEY_URL_CACHE_PREFIX${meal.imageCacheKey}", null)
            if (!cachedUrl.isNullOrBlank() && cachedUrl.startsWith("http")) {
                return@withContext MealImageState.OnlineImage(cachedUrl)
            }

            if (!meal.recipeUrl.isNullOrBlank() && meal.recipeUrl.startsWith("http")) {
                val ogImage = fetchOpenGraphImage(meal.recipeUrl)
                if (!ogImage.isNullOrBlank() && !ogImage.contains("hellofresh-logo", ignoreCase = true)) {
                    prefs.edit().putString("$KEY_URL_CACHE_PREFIX${meal.imageCacheKey}", ogImage).apply()
                    return@withContext MealImageState.OnlineImage(ogImage)
                }
            }

            MealImageState.GenericPlated
        }

    private fun fetchOpenGraphImage(url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string() ?: return null
                val ogRegex = Regex(
                    """<meta[^>]+(?:property|name)=["'](?:og:image|twitter:image)["'][^>]+content=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                )
                val reverseOgRegex = Regex(
                    """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["'](?:og:image|twitter:image)["']""",
                    RegexOption.IGNORE_CASE
                )
                val match = ogRegex.find(html)?.groupValues?.getOrNull(1)
                    ?: reverseOgRegex.find(html)?.groupValues?.getOrNull(1)
                match?.replace("&amp;", "&")?.takeIf { it.startsWith("http") }
            }
        }.getOrNull()
    }
}
