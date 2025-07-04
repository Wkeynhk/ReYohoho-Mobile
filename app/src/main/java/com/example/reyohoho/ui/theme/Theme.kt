package com.example.reyohoho.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = AppBackgroundColor
)

// Даже если используется светлая тема, все равно используем темные цвета
private val LightColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = AppBackgroundColor
)

@Composable
fun ReYohohoTheme(
    darkTheme: Boolean = true, // Всегда используем темную тему по умолчанию
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Отключаем динамические цвета для сохранения темной темы
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // Даже при динамических цветах используем темную версию
            dynamicDarkColorScheme(context)
        }
        else -> DarkColorScheme // Всегда используем темную цветовую схему
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            
            // Устанавливаем цвет навигационной панели в чёрный, независимо от темы
            window.navigationBarColor = Color.Black.toArgb()
            
            // Устанавливаем цвет иконок на навигационной панели для темной темы
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false // Светлые иконки на темной строке состояния
                isAppearanceLightNavigationBars = false // Светлые иконки на темной навигационной панели
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}