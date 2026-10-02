package tech.kloos.kompound.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape of a shake: how far the element moves, tilts or pulses, and how often it changes direction.
 *
 * @property translateX Horizontal travel in each direction.
 * @property translateY Vertical travel in each direction.
 * @property rotate Tilt in degrees in each direction.
 * @property scale Growth and shrink as a fraction of the size (0.05 = 5 %).
 * @property swings How many times the element swings out and back before it rests.
 * @property swingMillis Duration of one swing.
 */
@Stable
public data class KShakeSpec(
    val translateX: Dp = 0.dp,
    val translateY: Dp = 0.dp,
    val rotate: Float = 0f,
    val scale: Float = 0f,
    val swings: Int = 6,
    val swingMillis: Int = 50,
) {
    public companion object {
        /** Side to side, for rejected input such as a wrong password. */
        public val Error: KShakeSpec = KShakeSpec(translateX = 10.dp)

        /** Up and down, a "yes" nod. */
        public val Nod: KShakeSpec = KShakeSpec(translateY = 6.dp, swings = 4, swingMillis = 70)

        /** A playful tilt for drawing attention. */
        public val Wobble: KShakeSpec = KShakeSpec(rotate = 6f, scale = 0.04f, swings = 6, swingMillis = 60)
    }
}

/** Starts shakes of the element it is attached to with [shake]. Create it with [rememberKShakeState]. */
@Stable
public class KShakeState {
    internal var trigger: Int by mutableIntStateOf(0)
    internal var spec: KShakeSpec = KShakeSpec.Error

    /** Shakes now with [spec]; a shake in progress restarts. Call it from an event, not during composition. */
    public fun shake(spec: KShakeSpec = KShakeSpec.Error) {
        this.spec = spec
        trigger++
    }
}

/** Remembers a [KShakeState]. */
@Composable
public fun rememberKShakeState(): KShakeState = remember { KShakeState() }

/**
 * Lets the element shake whenever [state] is told to: movement, tilt and pulse are applied to the drawn
 * element only, so layout and the touch area stay where they are. Nothing moves until [KShakeState.shake].
 */
@Composable
public fun Modifier.shake(state: KShakeState): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state.trigger) {
        if (state.trigger == 0) return@LaunchedEffect
        val spec = state.spec
        repeat(spec.swings) { i -> progress.animateTo(if (i % 2 == 0) 1f else -1f, tween(spec.swingMillis, easing = LinearEasing)) }
        progress.animateTo(0f, tween(spec.swingMillis / 2, easing = LinearEasing))   // settle; a restart simply continues from here
    }
    return graphicsLayer {
        val p = progress.value
        if (p != 0f) {
            val spec = state.spec
            translationX = p * spec.translateX.toPx()
            translationY = p * spec.translateY.toPx()
            rotationZ = p * spec.rotate
            val s = 1f + p * spec.scale
            scaleX = s
            scaleY = s
        }
    }
}
