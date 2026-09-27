package com.galstruo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pixel 风格色轮:拖动或点击选择主色色相 */
@Composable
fun ColorWheel(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val wheelColors = remember {
        (0..12).map { i -> Color.hsv(i * 30f, 1f, 1f) }
    }
    // 画布绘制块不是 Composable 环境,颜色需提前取出
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier
            .size(240.dp)
            .pointerInput(Unit) {
                detectTapGestures { pos -> setHue(pos, canvasSize, onHueChange) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> setHue(change.position, canvasSize, onHueChange) }
            },
    ) {
        canvasSize = size
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        drawCircle(brush = Brush.sweepGradient(wheelColors), radius = radius, center = center)
        drawCircle(color = surfaceColor, radius = radius * 0.52f, center = center)
        drawCircle(
            color = outlineVariant,
            radius = radius - 1.dp.toPx(),
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        // 当前选中的颜色指示器
        val angle = hue * PI.toFloat() / 180f
        val indicator = center + Offset(cos(angle), sin(angle)) * radius * 0.76f
        drawCircle(color = Color.Black.copy(alpha = 0.3f), radius = 17.dp.toPx(), center = indicator)
        drawCircle(color = Color.White, radius = 16.dp.toPx(), center = indicator, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.hsv(hue, 1f, 1f), radius = 14.dp.toPx(), center = indicator)
    }
}

private fun setHue(pos: Offset, canvasSize: Size, onHueChange: (Float) -> Unit) {
    if (canvasSize == Size.Zero) return
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val v = pos - center
    val distance = sqrt(v.x * v.x + v.y * v.y)
    val radius = canvasSize.minDimension / 2f
    // 只响应色环区域(内圈空心区域忽略)
    if (distance > radius || distance < radius * 0.35f) return
    val degrees = ((atan2(v.y, v.x) * 180f / PI.toFloat()) + 360f) % 360f
    onHueChange(degrees)
}
