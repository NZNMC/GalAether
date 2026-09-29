package com.galstruo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
        // 跟随壁纸动态取色(安卓 12+ 官方 Material You,系统已按 HCT 色调体系生成)
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // 用户自定义主色,或关闭跟随壁纸时的默认色。
        // 按 Material 3 官方颜色科学(HCT 色调板,见 m3.material.io「Science of color」)
        // 用 material-color-utilities 预生成的色板表按色相取色
        val hue = if (seedHue in 0..359) seedHue.toFloat() else DEFAULT_SEED_HUE
        buildScheme(hue, darkTheme)
    }
    MaterialTheme(
        colorScheme = base,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}

/**
 * 查色板表取单个角色颜色(供设置页色环预览、预设圆点等使用)。
 * [hue] 为 HCT 色相 0~360,[role] 见 [M3PaletteRoles],[dark] 决定深浅两套。
 */
fun m3RoleColor(hue: Float, role: String, dark: Boolean): Color {
    val idx = M3PaletteRoles.indexOf(role)
    require(idx >= 0) { "未知的色彩角色: $role" }
    val scaled = ((hue % 360f + 360f) % 360f) / 10f
    val i = scaled.toInt() % 36
    val frac = scaled - scaled.toInt()
    val j = (i + 1) % 36
    val a = M3PaletteTable[i]
    val b = M3PaletteTable[j]
    val base = idx * 2
    fun argb(rgb: Int) = Color(0xFF000000.toInt() or rgb)
    return if (dark) lerp(argb(a[base + 1]), argb(b[base + 1]), frac)
    else lerp(argb(a[base]), argb(b[base]), frac)
}

/**
 * 从色相取整套 Material 3 配色:
 * 色板表 M3PaletteTable 存了 36 个色相(每 10° 一档)× 36 个角色 × 浅/深两套
 * (由官方 material-color-utilities 按 TonalSpot 方案生成),相邻两档线性插值。
 */
private fun buildScheme(hue: Float, dark: Boolean): ColorScheme {
    val scaled = ((hue % 360f + 360f) % 360f) / 10f
    val i = scaled.toInt() % 36
    val frac = scaled - scaled.toInt()
    val j = (i + 1) % 36
    val a = M3PaletteTable[i]
    val b = M3PaletteTable[j]

    // 表里存的是 24 位 RGB(生成时去掉了透明字节),转 Color 时补上不透明
    fun argb(rgb: Int) = Color(0xFF000000.toInt() or rgb)

    fun role(idx: Int): Color {
        val base = idx * 2
        return if (dark) {
            lerp(argb(a[base + 1]), argb(b[base + 1]), frac)
        } else {
            lerp(argb(a[base]), argb(b[base]), frac)
        }
    }

    return ColorScheme(
        primary = role(0), onPrimary = role(1),
        primaryContainer = role(2), onPrimaryContainer = role(3),
        inversePrimary = role(4),
        secondary = role(5), onSecondary = role(6),
        secondaryContainer = role(7), onSecondaryContainer = role(8),
        tertiary = role(9), onTertiary = role(10),
        tertiaryContainer = role(11), onTertiaryContainer = role(12),
        background = role(13), onBackground = role(14),
        surface = role(15), onSurface = role(16),
        surfaceVariant = role(17), onSurfaceVariant = role(18),
        surfaceTint = role(19),
        inverseSurface = role(20), inverseOnSurface = role(21),
        error = role(22), onError = role(23),
        errorContainer = role(24), onErrorContainer = role(25),
        outline = role(26), outlineVariant = role(27),
        scrim = role(28),
        surfaceBright = role(29), surfaceDim = role(30),
        surfaceContainer = role(31),
        surfaceContainerHigh = role(32),
        surfaceContainerHighest = role(33),
        surfaceContainerLow = role(34),
        surfaceContainerLowest = role(35),
    )
}
