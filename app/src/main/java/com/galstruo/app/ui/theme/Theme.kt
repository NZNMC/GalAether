package com.galstruo.app.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Pixel 风格的默认主色(谷歌蓝) */
private val DefaultSeed = Color(0xFF4285F4)

/** Pixel 风格圆角:整体比 Material 默认值更圆润 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun GalStoreTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    seedHue: Int,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        dynamicColor && seedHue < 0
    ) {
        // 跟随壁纸动态取色(安卓 12+ 官方 Material You)
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // 用户自定义主色,或关闭跟随壁纸时的默认色
        val seed = if (seedHue in 0..359) {
            Color.hsv(seedHue.toFloat(), 0.45f, 0.95f)
        } else {
            DefaultSeed
        }
        buildScheme(seed, darkTheme)
    }
    MaterialTheme(
        colorScheme = base,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}

/** 从一颗种子色生成整套 Material 3 配色(全部颜色随色相联动) */
private fun buildScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seed.toArgb(), hsv)
    val h = hsv[0]
    val h2 = (h + 60f) % 360f   // 辅助色相:与主色相错开 60 度

    fun c(s: Float, v: Float) = Color.hsv(h, s, v)
    fun c2(s: Float, v: Float) = Color.hsv(h2, s, v)

    return if (dark) {
        ColorScheme(
            primary = c(0.26f, 1f), onPrimary = c(0.45f, 0.45f),
            primaryContainer = c(0.45f, 0.55f), onPrimaryContainer = c(0.14f, 1f),
            inversePrimary = c(0.45f, 0.55f),
            secondary = c(0.19f, 0.86f), onSecondary = c(0.25f, 0.35f),
            secondaryContainer = c(0.25f, 0.40f), onSecondaryContainer = c(0.10f, 0.94f),
            tertiary = c2(0.23f, 0.94f), onTertiary = c2(0.30f, 0.35f),
            tertiaryContainer = c2(0.30f, 0.42f), onTertiaryContainer = c2(0.14f, 1f),
            background = c(0.17f, 0.09f), onBackground = c(0.06f, 0.91f),
            surface = c(0.17f, 0.09f), onSurface = c(0.06f, 0.91f),
            surfaceVariant = c(0.10f, 0.31f), onSurfaceVariant = c(0.08f, 0.75f),
            surfaceTint = c(0.26f, 1f),
            inverseSurface = c(0.06f, 0.91f), inverseOnSurface = c(0.17f, 0.16f),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
            outline = c(0.06f, 0.60f), outlineVariant = c(0.08f, 0.35f),
            scrim = Color.Black,
            surfaceBright = c(0.14f, 0.24f), surfaceDim = c(0.17f, 0.06f),
            surfaceContainer = c(0.15f, 0.10f),
            surfaceContainerHigh = c(0.16f, 0.12f),
            surfaceContainerHighest = c(0.17f, 0.15f),
            surfaceContainerLow = c(0.14f, 0.08f),
            surfaceContainerLowest = c(0.12f, 0.06f),
        )
    } else {
        ColorScheme(
            primary = c(0.50f, 0.64f), onPrimary = c(0.25f, 1f),
            primaryContainer = c(0.14f, 1f), onPrimaryContainer = c(0.55f, 0.36f),
            inversePrimary = c(0.30f, 0.80f),
            secondary = c(0.20f, 0.44f), onSecondary = Color.White,
            secondaryContainer = c(0.10f, 0.94f), onSecondaryContainer = c(0.30f, 0.30f),
            tertiary = c2(0.34f, 0.49f), onTertiary = Color.White,
            tertiaryContainer = c2(0.15f, 0.94f), onTertiaryContainer = c2(0.45f, 0.30f),
            background = c(0.04f, 0.99f), onBackground = c(0.16f, 0.12f),
            surface = c(0.04f, 0.99f), onSurface = c(0.16f, 0.12f),
            surfaceVariant = c(0.06f, 0.93f), onSurfaceVariant = c(0.10f, 0.30f),
            surfaceTint = c(0.50f, 0.64f),
            inverseSurface = c(0.10f, 0.20f), inverseOnSurface = c(0.06f, 0.95f),
            error = Color(0xFFBA1A1A), onError = Color.White,
            errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
            outline = c(0.08f, 0.50f), outlineVariant = c(0.06f, 0.80f),
            scrim = Color.Black,
            surfaceBright = c(0.04f, 0.98f), surfaceDim = c(0.04f, 0.94f),
            surfaceContainer = c(0.05f, 0.96f),
            surfaceContainerHigh = c(0.06f, 0.94f),
            surfaceContainerHighest = c(0.06f, 0.92f),
            surfaceContainerLow = c(0.05f, 0.98f),
            surfaceContainerLowest = c(0.05f, 1f),
        )
    }
}
