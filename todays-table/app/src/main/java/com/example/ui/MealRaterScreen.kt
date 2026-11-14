package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.model.MealImageState
import com.example.model.MealsUiState
import com.example.model.ServiceFilter
import com.example.model.UnratedMeal
import com.example.ui.components.RatingPromptSheet
import com.example.ui.components.UnratedMealCard
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val FOREGROUND_REFRESH_INTERVAL_MS = 8_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealRaterScreen(
    viewModel: MealViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.mealsUiState.collectAsStateWithLifecycle()
    val serviceFilter by viewModel.serviceFilter.collectAsStateWithLifecycle()
    val imageStates by viewModel.imageStates.collectAsStateWithLifecycle()
    val selectedMeal by viewModel.selectedMeal.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val webAppUrl by viewModel.webAppUrl.collectAsStateWithLifecycle()
    val googleSheetUrl by viewModel.googleSheetUrl.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val appsScriptOmitsColumnE by viewModel.appsScriptOmitsColumnE.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    // Auto-dismiss the green status notification banner after 1 second
    LaunchedEffect(statusBanner) {
        if (!statusBanner.isNullOrBlank()) {
            delay(1_000L)
            viewModel.dismissStatusBanner()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    // Automatic sheet refresh strictly runs ONLY while the app is open in the foreground (RESUMED)
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.syncSheetWhileForeground()
            while (isActive) {
                delay(FOREGROUND_REFRESH_INTERVAL_MS)
                viewModel.syncSheetWhileForeground()
            }
        }
    }

    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val openRecipeSafe: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }
    }

    if (showSettingsSheet) {
        WebAppSettingsSheet(
            currentWebAppUrl = webAppUrl,
            currentGoogleSheetUrl = googleSheetUrl,
            currentGeminiApiKey = geminiApiKey,
            onDismiss = { showSettingsSheet = false },
            onSaveUrls = { updatedWebApp, updatedSheet, updatedGeminiKey ->
                viewModel.updateUrls(updatedWebApp, updatedSheet, updatedGeminiKey)
                showSettingsSheet = false
            },
            onResetDefault = {
                viewModel.resetWebAppUrl()
                showSettingsSheet = false
            }
        )
    }

    selectedMeal?.let { meal ->
        RatingPromptSheet(
            meal = meal,
            imageState = imageStates[meal.imageCacheKey],
            isSubmitting = isSubmitting,
            submitError = submitError,
            onDismiss = { viewModel.selectMealForRating(null) },
            onOpenRecipe = openRecipeSafe,
            onSubmitRating = { date, anissa, adamme ->
                viewModel.submitMealRating(meal, date, anissa, adamme)
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EditorialHeader(
                uiState = uiState,
                serviceFilter = serviceFilter,
                onFilterChange = viewModel::setServiceFilter,
                onRefreshClick = { viewModel.refreshFromSheet() },
                onSettingsClick = { showSettingsSheet = true }
            )

            AnimatedVisibility(
                visible = appsScriptOmitsColumnE && googleSheetUrl.isBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showSettingsSheet = true }
                        .testTag("link_sheet_url_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Apps Script omits Column E (\"-\"). Tap here to paste your Google Sheet URL so \"-\" rows are hidden.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Link Sheet",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !statusBanner.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("status_banner"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = statusBanner.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        IconButton(onClick = viewModel::dismissStatusBanner) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshFromSheet() },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("pull_to_refresh_box")
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val isWideScreen = maxWidth >= 680.dp

                    when (val state = uiState) {
                        is MealsUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("loading_indicator"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Syncing unrated meals from Google Sheet…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is MealsUiState.Error -> {
                            ErrorStateView(
                                message = state.message,
                                onRetry = { viewModel.refreshFromSheet() },
                                onOpenSettings = { showSettingsSheet = true }
                            )
                        }

                        is MealsUiState.Success -> {
                            val filteredMeals by remember(state.meals, serviceFilter) {
                                derivedStateOf {
                                    state.meals.filter { meal ->
                                        when (serviceFilter) {
                                            ServiceFilter.ALL -> true
                                            ServiceFilter.HELLO_FRESH -> meal.isHelloFresh
                                            ServiceFilter.YELLOW_PICNIC -> meal.isYellowPicnic
                                        }
                                    }
                                }
                            }

                            if (filteredMeals.isEmpty()) {
                                EmptyQueueView(
                                    hasActiveFilters = serviceFilter != ServiceFilter.ALL,
                                    onClearFilters = {
                                        viewModel.setServiceFilter(ServiceFilter.ALL)
                                    },
                                    onRefresh = { viewModel.refreshFromSheet() }
                                )
                            } else if (isWideScreen) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("unrated_meals_grid")
                                ) {
                                    items(
                                        items = filteredMeals,
                                        key = { it.uniqueId }
                                    ) { meal ->
                                        UnratedMealCard(
                                            meal = meal,
                                            imageState = imageStates[meal.imageCacheKey],
                                            onCardClick = { viewModel.selectMealForRating(meal) },
                                            onOpenRecipe = openRecipeSafe
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 28.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("unrated_meals_list")
                                ) {
                                    items(
                                        items = filteredMeals,
                                        key = { it.uniqueId }
                                    ) { meal ->
                                        UnratedMealCard(
                                            meal = meal,
                                            imageState = imageStates[meal.imageCacheKey],
                                            onCardClick = { viewModel.selectMealForRating(meal) },
                                            onOpenRecipe = openRecipeSafe,
                                            modifier = Modifier.widthIn(max = 600.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialHeader(
    uiState: MealsUiState,
    serviceFilter: ServiceFilter,
    onFilterChange: (ServiceFilter) -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val meals = (uiState as? MealsUiState.Success)?.meals.orEmpty()
    val totalCount = meals.size
    val helloFreshCount = meals.count { it.isHelloFresh }
    val yellowPicnicCount = meals.count { it.isYellowPicnic }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.header_subtitle).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.header_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.testTag("refresh_meals_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SingleLineFilterPill(
                label = "${stringResource(R.string.filter_all)} ($totalCount)",
                selected = serviceFilter == ServiceFilter.ALL,
                onClick = { onFilterChange(ServiceFilter.ALL) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_chip_all")
            )

            SingleLineFilterPill(
                label = "${stringResource(R.string.filter_hellofresh)} ($helloFreshCount)",
                selected = serviceFilter == ServiceFilter.HELLO_FRESH,
                onClick = { onFilterChange(ServiceFilter.HELLO_FRESH) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_chip_hellofresh")
            )

            SingleLineFilterPill(
                label = "${stringResource(R.string.filter_yellowpicnic)} ($yellowPicnicCount)",
                selected = serviceFilter == ServiceFilter.YELLOW_PICNIC,
                onClick = { onFilterChange(ServiceFilter.YELLOW_PICNIC) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_chip_yellowpicnic")
            )
        }
    }
}

@Composable
private fun SingleLineFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (selected) {
        null
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }

    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = containerColor,
        border = border
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyQueueView(
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
            .testTag("empty_queue_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasActiveFilters) {
                "No Matching Unrated Meals"
            } else {
                stringResource(R.string.empty_queue_title)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasActiveFilters) {
                "Switch back to All to see unrated meals from the other service."
            } else {
                stringResource(R.string.empty_queue_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (hasActiveFilters) {
            Button(
                onClick = onClearFilters,
                modifier = Modifier.testTag("clear_filters_button")
            ) {
                Text("Show All Unrated Meals")
            }
        } else {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("empty_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_refresh))
            }
        }
    }
}

@Composable
private fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
            .testTag("error_state_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.error_loading_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("error_settings_button")
            ) {
                Text(stringResource(R.string.action_settings))
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag("error_retry_button")
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebAppSettingsSheet(
    currentWebAppUrl: String,
    currentGoogleSheetUrl: String,
    currentGeminiApiKey: String,
    onDismiss: () -> Unit,
    onSaveUrls: (webAppUrl: String, googleSheetUrl: String, geminiApiKey: String) -> Unit,
    onResetDefault: () -> Unit
) {
    BackHandler { onDismiss() }

    var webAppUrlText by rememberSaveable(currentWebAppUrl) { mutableStateOf(currentWebAppUrl) }
    var sheetUrlText by rememberSaveable(currentGoogleSheetUrl) { mutableStateOf(currentGoogleSheetUrl) }
    var geminiApiKeyText by rememberSaveable(currentGeminiApiKey) { mutableStateOf(currentGeminiApiKey) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_sheet_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Configure your Google Sheet, Apps Script Web App URL, and Gemini API key (used to generate AI food photos for YellowPicnic meals).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = geminiApiKeyText,
                onValueChange = { geminiApiKeyText = it },
                label = { Text("Gemini API Key (for YellowPicnic AI Food Photos)") },
                placeholder = { Text("AIzaSy...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_api_key_input")
            )

            OutlinedTextField(
                value = sheetUrlText,
                onValueChange = { sheetUrlText = it },
                label = { Text(stringResource(R.string.settings_google_sheet_url_label)) },
                placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
                maxLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_sheet_url_input")
            )

            OutlinedTextField(
                value = webAppUrlText,
                onValueChange = { webAppUrlText = it },
                label = { Text(stringResource(R.string.settings_url_label)) },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("webapp_url_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onResetDefault,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_url_button")
                ) {
                    Text(stringResource(R.string.settings_reset))
                }
                Button(
                    onClick = { onSaveUrls(webAppUrlText, sheetUrlText, geminiApiKeyText) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_url_button")
                ) {
                    Text(stringResource(R.string.settings_save))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Preview(
    name = "Today's Table — Unrated Queue (7 Meals)",
    showBackground = true,
    widthDp = 412,
    heightDp = 1150
)
@Composable
fun TodaysTablePreview() {
    val unratedMeals = listOf(
        UnratedMeal(
            tab = "HelloFresh",
            rowNumber = 3,
            meal = "Slammin’ Secret Sauce Chicken Sandwiches with Scallion Cabbage Slaw",
            recipeUrl = "https://www.hellofresh.com/recipes/slammin-secret-sauce-chicken-sandwiches-6847d71d4b128cef4ed186a1"
        ),
        UnratedMeal(
            tab = "HelloFresh",
            rowNumber = 4,
            meal = "One-Pot Thai Chicken Khao Soi Soup with Chicken Thighs, Ramen, Coconut Curry Broth & Crispy Wontons",
            recipeUrl = "https://www.hellofresh.com/recipes/search?q=One-Pot%20Thai%20Chicken%20Khao%20Soi%20Soup"
        ),
        UnratedMeal(
            tab = "HelloFresh",
            rowNumber = 5,
            meal = "Seared Salmon & Creamy Tuscan Linguine with Spinach & Tomato",
            recipeUrl = "https://www.hellofresh.com/recipes/seared-salmon-and-creamy-tuscan-linguine-685a58e0d12c14cd4da5f564"
        ),
        UnratedMeal(
            tab = "YellowPicnic",
            rowNumber = 3,
            meal = "Cilantro Lime Chicken Tacos"
        ),
        UnratedMeal(
            tab = "YellowPicnic",
            rowNumber = 4,
            meal = "Penne Alla Vodka with Chicken"
        ),
        UnratedMeal(
            tab = "YellowPicnic",
            rowNumber = 5,
            meal = "Pepper Steak Stir Fry"
        ),
        UnratedMeal(
            tab = "YellowPicnic",
            rowNumber = 6,
            meal = "Butter White Wine Shrimp"
        )
    )

    var selectedFilter by remember { mutableStateOf(ServiceFilter.ALL) }
    val filtered = unratedMeals.filter { meal ->
        when (selectedFilter) {
            ServiceFilter.ALL -> true
            ServiceFilter.HELLO_FRESH -> meal.isHelloFresh
            ServiceFilter.YELLOW_PICNIC -> meal.isYellowPicnic
        }
    }

    MyApplicationTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                EditorialHeader(
                    uiState = MealsUiState.Success(unratedMeals),
                    serviceFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it },
                    onRefreshClick = {},
                    onSettingsClick = {}
                )

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filtered,
                        key = { it.uniqueId }
                    ) { meal ->
                        UnratedMealCard(
                            meal = meal,
                            imageState = MealImageState.GenericPlated,
                            onCardClick = {},
                            onOpenRecipe = {}
                        )
                    }
                }
            }
        }
    }
}


