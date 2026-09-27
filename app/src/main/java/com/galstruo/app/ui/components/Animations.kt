package com.galstruo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 全局统一的"活泼"手感工具:
 * - pressScale:按压缩放(手指按下凹一下,松开弹回)
 * - staggeredIn:列表项交错入场(淡入+上滑,按位置错开)
 * - BounceIcon:选中弹跳图标(底部导航用)
 */

/** 界面动画总开关(设置里可关):pressScale / staggeredIn / BounceIcon / 页面切换动画全部联动 */
val LocalMotionEnabled = staticCompositionLocalOf { true }

/** 日文原名显示开关(设置里可开):开启后列表/卡片的主标题显示日文原名 */
val LocalShowJapaneseNames = staticCompositionLocalOf { false }

/**
 * 按压缩放:手指按下时组件轻微缩小,松开弹回。
 * 只处理视觉缩放,不消费事件,组件自带的点击/水波纹不受影响;列表滚动会自然取消。
 */
fun Modifier.pressScale(scale: Float = 0.96f): Modifier = composed {
    // 动画总开关关闭时不做按压缩放
    if (!LocalMotionEnabled.current) return@composed this
    var pressed by remember { mutableStateOf(false) }
    val anim by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressScale",
    )
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pressed = true
            waitForUpOrCancellation()
            pressed = false
        }
    }.graphicsLayer {
        scaleX = anim
        scaleY = anim
    }
}

/** 列表项交错入场:淡入 + 轻微上滑,按 index 错开(最多错开前 12 项,后面的同时进入) */
@Composable
fun staggeredIn(index: Int, visible: Boolean, content: @Composable () -> Unit) {
    // 动画总开关关闭时直接显示,不做入场动画
    if (!LocalMotionEnabled.current) {
        if (visible) content()
        return
    }
    val delay = (index % 12) * 28
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, delayMillis = delay)) +
            slideInVertically(tween(300, delayMillis = delay), initialOffsetY = { it / 5 }),
    ) {
        content()
    }
}

/** 选中时弹跳的图标(底部导航):选中的瞬间缩小再弹回,像果冻 */
@Composable
fun BounceIcon(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // 动画总开关关闭时直接显示静态图标
    if (!LocalMotionEnabled.current) {
        Icon(icon, contentDescription, modifier)
        return
    }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(0.7f)
            scale.animateTo(
                1.18f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        } else {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    Icon(
        icon,
        contentDescription,
        modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}
