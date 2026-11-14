package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.model.MealImageState
import com.example.model.UnratedMeal
import com.example.ui.theme.HelloFreshBgDark
import com.example.ui.theme.HelloFreshBgLight
import com.example.ui.theme.HelloFreshBorderDark
import com.example.ui.theme.HelloFreshBorderLight
import com.example.ui.theme.HelloFreshTextDark
import com.example.ui.theme.HelloFreshTextLight
import com.example.ui.theme.YellowPicnicBgDark
import com.example.ui.theme.YellowPicnicBgLight
import com.example.ui.theme.YellowPicnicBorderDark
import com.example.ui.theme.YellowPicnicBorderLight
import com.example.ui.theme.YellowPicnicTextDark
import com.example.ui.theme.YellowPicnicTextLight
import kotlin.math.abs

@Composable
fun ServiceBadge(
    tab: String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val isHelloFresh = tab.replace(" ", "").equals("HelloFresh", ignoreCase = true)

    val bgColor = when {
        isHelloFresh && isDark -> HelloFreshBgDark
        isHelloFresh && !isDark -> HelloFreshBgLight
        !isHelloFresh && isDark -> YellowPicnicBgDark
        else -> YellowPicnicBgLight
    }
    val borderColor = when {
        isHelloFresh && isDark -> HelloFreshBorderDark
        isHelloFresh && !isDark -> HelloFreshBorderLight
        !isHelloFresh && isDark -> YellowPicnicBorderDark
        else -> YellowPicnicBorderLight
    }
    val textColor = when {
        isHelloFresh && isDark -> HelloFreshTextDark
        isHelloFresh && !isDark -> HelloFreshTextLight
        !isHelloFresh && isDark -> YellowPicnicTextDark
        else -> YellowPicnicTextLight
    }

    val icon = if (isHelloFresh) Icons.Default.Eco else Icons.Default.WbSunny
    val label = if (isHelloFresh) {
        stringResource(R.string.badge_hellofresh)
    } else if (tab.replace(" ", "").equals("YellowPicnic", ignoreCase = true)) {
        stringResource(R.string.badge_yellowpicnic)
    } else {
        tab
    }

    Surface(
        modifier = modifier.testTag("service_badge_${tab.lowercase()}"),
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
fun MealPhotoBanner(
    meal: UnratedMeal,
    imageState: MealImageState?,
    height: Dp = 185.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        when (val state = imageState ?: MealImageState.GenericPlated) {
            is MealImageState.SearchingOnline -> {
                GenericPlatedMealBanner(
                    meal = meal,
                    isLoading = true
                )
            }

            is MealImageState.OnlineImage -> {
                SubcomposeAsyncImage(
                    model = state.url,
                    contentDescription = meal.displayMealName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        GenericPlatedMealBanner(
                            meal = meal,
                            isLoading = true
                        )
                    },
                    error = {
                        GenericPlatedMealBanner(
                            meal = meal,
                            isLoading = false
                        )
                    }
                )
            }

            is MealImageState.AiGenerated -> {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = meal.displayMealName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            is MealImageState.GenericPlated -> {
                GenericPlatedMealBanner(
                    meal = meal,
                    isLoading = false
                )
            }
        }
    }
}

private data class CulinaryCardPalette(
    val startColor: Color,
    val endColor: Color,
    val plateColor: Color,
    val iconTint: Color
)

private val curatedCulinaryPalettes = listOf(
    CulinaryCardPalette(
        startColor = Color(0xFFF6D365),
        endColor = Color(0xFFFDA085),
        plateColor = Color(0xFFFFFBF5),
        iconTint = Color(0xFFB45309)
    ),
    CulinaryCardPalette(
        startColor = Color(0xFFE07A5F),
        endColor = Color(0xFFF2CC8F),
        plateColor = Color(0xFFFFFBF7),
        iconTint = Color(0xFF9A3412)
    ),
    CulinaryCardPalette(
        startColor = Color(0xFF81B29A),
        endColor = Color(0xFFB7E4C7),
        plateColor = Color(0xFFF8FFF9),
        iconTint = Color(0xFF1B4332)
    ),
    CulinaryCardPalette(
        startColor = Color(0xFFD4A373),
        endColor = Color(0xFFE9EDC9),
        plateColor = Color(0xFFFFFDF9),
        iconTint = Color(0xFF78350F)
    ),
    CulinaryCardPalette(
        startColor = Color(0xFFE5989B),
        endColor = Color(0xFFFFCDB2),
        plateColor = Color(0xFFFFFAFA),
        iconTint = Color(0xFF881337)
    ),
    CulinaryCardPalette(
        startColor = Color(0xFF52B788),
        endColor = Color(0xFF95D5B2),
        plateColor = Color(0xFFF7FFF9),
        iconTint = Color(0xFF14532D)
    )
)

private fun pickDishIcon(mealName: String): ImageVector {
    val lower = mealName.lowercase()
    return when {
        lower.contains("soup") || lower.contains("khao soi") || lower.contains("chowder") || lower.contains("broth") ->
            Icons.Default.SoupKitchen
        lower.contains("noodle") || lower.contains("ramen") || lower.contains("linguine") ||
            lower.contains("spaghetti") || lower.contains("rigatoni") || lower.contains("pasta") ||
            lower.contains("yakisoba") ->
            Icons.Default.RamenDining
        lower.contains("bowl") || lower.contains("rice") || lower.contains("risotto") ||
            lower.contains("orzotto") || lower.contains("bibimbap") ->
            Icons.Default.RiceBowl
        lower.contains("sandwich") || lower.contains("burger") || lower.contains("sando") ||
            lower.contains("taco") || lower.contains("banh mi") || lower.contains("roll") ->
            Icons.Default.DinnerDining
        else ->
            Icons.Default.LocalDining
    }
}

@Composable
fun GenericPlatedMealBanner(
    meal: UnratedMeal,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val paletteIndex = abs(meal.displayMealName.hashCode()) % curatedCulinaryPalettes.size
    val palette = curatedCulinaryPalettes[paletteIndex]
    val dishIcon = pickDishIcon(meal.displayMealName)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.startColor, palette.endColor)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(palette.plateColor.copy(alpha = 0.28f))
                .border(
                    width = 1.5.dp,
                    color = palette.plateColor.copy(alpha = 0.55f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = palette.plateColor,
                shadowElevation = 4.dp,
                border = BorderStroke(2.dp, palette.iconTint.copy(alpha = 0.16f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 2.dp,
                            color = palette.iconTint.copy(alpha = 0.35f)
                        )
                    }
                    Icon(
                        imageVector = dishIcon,
                        contentDescription = meal.displayMealName,
                        tint = palette.iconTint,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UnratedMealCard(
    meal: UnratedMeal,
    imageState: MealImageState?,
    onCardClick: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onCardClick)
            .testTag("meal_card_${meal.uniqueId}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MealPhotoBanner(
                meal = meal,
                imageState = imageState,
                height = 185.dp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ServiceBadge(tab = meal.tab)

                Text(
                    text = meal.displayMealName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!meal.recipeUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onOpenRecipe(meal.recipeUrl) },
                            modifier = Modifier.testTag("open_recipe_btn_${meal.uniqueId}"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.action_open_recipe),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_open_recipe),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = onCardClick,
                        modifier = Modifier.testTag("rate_meal_button_${meal.uniqueId}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_rate_meal),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
