package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TerracottaPrimaryDark,
    onPrimary = OnTerracottaPrimaryDark,
    primaryContainer = TerracottaContainerDark,
    onPrimaryContainer = OnTerracottaContainerDark,
    secondary = HerbSageSecondaryDark,
    onSecondary = OnHerbSageSecondaryDark,
    secondaryContainer = HerbSageContainerDark,
    onSecondaryContainer = OnHerbSageContainerDark,
    tertiary = SaffronTertiaryDark,
    onTertiary = OnSaffronTertiaryDark,
    tertiaryContainer = SaffronContainerDark,
    onTertiaryContainer = OnSaffronContainerDark,
    background = EspressoBackgroundDark,
    onBackground = OnEspressoBackgroundDark,
    surface = EspressoSurfaceDark,
    onSurface = OnEspressoSurfaceDark,
    surfaceVariant = EspressoSurfaceVariantDark,
    onSurfaceVariant = OnEspressoSurfaceVariantDark,
    outline = EspressoOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = OnTerracottaPrimary,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = HerbSageSecondary,
    onSecondary = OnHerbSageSecondary,
    secondaryContainer = HerbSageContainer,
    onSecondaryContainer = OnHerbSageContainer,
    tertiary = SaffronTertiary,
    onTertiary = OnSaffronTertiary,
    tertiaryContainer = SaffronContainer,
    onTertiaryContainer = OnSaffronContainer,
    background = AlabasterBackground,
    onBackground = OnAlabasterBackground,
    surface = WarmIvorySurface,
    onSurface = OnWarmIvorySurface,
    surfaceVariant = ParchmentSurfaceVariant,
    onSurfaceVariant = OnParchmentSurfaceVariant,
    outline = WarmOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
