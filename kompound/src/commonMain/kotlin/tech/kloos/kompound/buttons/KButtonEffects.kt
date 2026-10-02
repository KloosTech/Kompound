package tech.kloos.kompound.buttons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.theme.KompoundStateLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Optional feedback effects of a [KButton]. Every effect is off by default except [clickShadow]; turn them on
 * per button or build your own default with `copy`. They only add style blocks (and, for [sparkles], a
 * drawing layer), so a `style` passed to the button still wins.
 *
 * @property clickShadow A soft coloured shadow under the pressed button (filled and tonal looks only).
 * @property bounce The button shrinks while pressed and springs back with an overshoot.
 * @property fade The button dims while pressed.
 * @property colorMorph Colours animate smoothly to the tertiary accent while pressed and fade back on release.
 * @property shapeMorph The corners animate from fully round to a small radius while pressed.
 * @property sparkles A burst of small stars flies out from the click position.
 */
@Stable
public data class KButtonEffects(
    val clickShadow: Boolean = true,
    val bounce: Boolean = false,
    val fade: Boolean = false,
    val colorMorph: Boolean = false,
    val shapeMorph: Boolean = false,
    val sparkles: Boolean = false,
) {
    public companion object {
        /** Click shadow only. */
        public val Default: KButtonEffects = KButtonEffects()

        /** No effects at all. */
        public val None: KButtonEffects = KButtonEffects(clickShadow = false)
    }
}

/** Pressed-state style blocks for [effects]; merged between the base style and the consumer's style. */
internal fun effectsStyle(
    effects: KButtonEffects,
    variant: KButtonVariant,
    colors: ButtonColors,
    scheme: ColorScheme,
    layers: KompoundStateLayer,
): Style {
    if (effects == KButtonEffects.None) return Style
    val filled = variant == KButtonVariant.Filled || variant == KButtonVariant.Tonal
    val glow = if (variant == KButtonVariant.Filled) scheme.primary.copy(alpha = 0.5f) else scheme.onSurface.copy(alpha = 0.3f)
    val morphContainer = if (filled) scheme.tertiary else scheme.tertiaryContainer
    val morphContent = if (filled) scheme.onTertiary else scheme.onTertiaryContainer
    return Style {
        if (effects.colorMorph) {
            hovered { animate(tween(220)) { background(colors.content.copy(alpha = layers.hovered).compositeOver(colors.container)) } }
        }
        pressed {
            if (effects.clickShadow && filled) {
                animate(tween(120)) { dropShadow(Shadow(radius = 14.dp, color = glow, spread = 1.dp, offset = DpOffset(0.dp, 4.dp))) }
            }
            if (effects.bounce) animate(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { scale(0.9f) }
            if (effects.fade) animate(tween(140)) { alpha(0.6f) }
            if (effects.colorMorph) {
                animate(tween(260)) {
                    background(morphContainer)
                    contentColor(morphContent)
                }
            }
            if (effects.shapeMorph) animate(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { shape(RoundedCornerShape(10.dp)) }
        }
    }
}

/** Remembers the trigger of the [sparkles] layer. */
@Stable
internal class SparkleState {
    var bursts by mutableIntStateOf(0)
    var origin: Offset = Offset.Unspecified
    fun fire() { bursts++ }
}

/**
 * Draws a star burst each time [state] fires. The burst starts at the last pointer press (the button centre for
 * keyboard clicks) and may extend past the button's bounds. Pointer events are observed, never consumed.
 */
@Composable
internal fun Modifier.sparkles(state: SparkleState?, colors: List<Color>): Modifier {
    if (state == null) return this
    val progress = remember { Animatable(1f) }
    LaunchedEffect(state.bursts) {
        if (state.bursts > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(700, easing = LinearOutSlowInEasing))
        }
    }
    return this
        .pointerInput(state) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) state.origin = event.changes.first().position
                }
            }
        }
        .drawWithContent {
            drawContent()
            val p = progress.value
            if (p < 1f && state.bursts > 0) {
                val origin = if (state.origin.isSpecified) state.origin else center
                drawSparkles(origin, p, colors, seed = state.bursts)
            }
        }
}

private const val Particles = 14

private fun DrawScope.drawSparkles(origin: Offset, progress: Float, colors: List<Color>, seed: Int) {
    val random = Random(seed)
    val reach = 32.dp.toPx() + random.nextFloat() * 8.dp.toPx()
    repeat(Particles) { i ->
        val angle = (2 * PI * i / Particles + random.nextFloat() * 0.4).toFloat()
        val distance = reach * (0.55f + random.nextFloat() * 0.6f) * progress
        val centre = origin + Offset(cos(angle) * distance, sin(angle) * distance)
        val size = (2.5f + random.nextFloat() * 3f).dp.toPx() * (1f - progress * 0.7f)
        val color = colors[i % colors.size].copy(alpha = (1f - progress).coerceIn(0f, 1f))
        if (i % 3 == 0) {
            drawCircle(color, radius = size * 0.6f, center = centre)
        } else {
            rotate(degrees = progress * 120f, pivot = centre) { drawStar(centre, size, color) }
        }
    }
}

/** Four-pointed star. */
private fun DrawScope.drawStar(centre: Offset, radius: Float, color: Color) {
    val inner = radius * 0.35f
    val path = Path().apply {
        moveTo(centre.x, centre.y - radius)
        lineTo(centre.x + inner, centre.y - inner)
        lineTo(centre.x + radius, centre.y)
        lineTo(centre.x + inner, centre.y + inner)
        lineTo(centre.x, centre.y + radius)
        lineTo(centre.x - inner, centre.y + inner)
        lineTo(centre.x - radius, centre.y)
        lineTo(centre.x - inner, centre.y - inner)
        close()
    }
    drawPath(path, color)
}
