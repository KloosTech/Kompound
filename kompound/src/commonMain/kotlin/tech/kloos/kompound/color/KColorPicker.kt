package tech.kloos.kompound.color

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.textfield.PickerField
import tech.kloos.kompound.theme.KompoundTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Colour text helpers. */
public object KColors {
    /** `#RRGGBB`, or `#RRGGBBAA` with [withAlpha]; upper case. */
    public fun toHex(color: Color, withAlpha: Boolean = false): String {
        fun b(v: Float) = (v * 255f).roundToInt().coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')
        return "#" + b(color.red) + b(color.green) + b(color.blue) + if (withAlpha) b(color.alpha) else ""
    }

    /** Reads `#RGB`, `#RRGGBB` and `#RRGGBBAA` (the `#` is optional), or `null`. */
    public fun parseHex(text: String): Color? {
        val t = text.trim().removePrefix("#")
        if (t.isEmpty() || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val full = when (t.length) { 3 -> t.map { "$it$it" }.joinToString(""); 6, 8 -> t; else -> return null }
        val v = full.chunked(2).map { it.toInt(16) / 255f }
        return Color(v[0], v[1], v[2], if (v.size == 4) v[3] else 1f)
    }
}

/** Hue 0..360, saturation and brightness (value) and opacity, each 0..1 (hue in degrees). */
internal data class Hsv(val h: Float, val s: Float, val v: Float, val a: Float = 1f) {
    fun toColor(): Color {
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when ((h.coerceIn(0f, 359.999f) / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m, a)
    }

    companion object {
        fun of(color: Color): Hsv {
            val mx = max(color.red, max(color.green, color.blue))
            val mn = min(color.red, min(color.green, color.blue))
            val d = mx - mn
            val h = when {
                d == 0f -> 0f
                mx == color.red -> 60f * (((color.green - color.blue) / d) % 6f)
                mx == color.green -> 60f * ((color.blue - color.red) / d + 2f)
                else -> 60f * ((color.red - color.green) / d + 4f)
            }
            return Hsv((h + 360f) % 360f, if (mx == 0f) 0f else d / mx, mx, color.alpha)
        }
    }
}

/**
 * Picks a colour: a square for saturation and brightness, a hue slider, an optional opacity slider, a hex field and optional swatches. Everything
 * works with the keyboard (arrow keys on the square and the sliders, Page Up and Page Down for bigger steps, Home and End) and is announced
 * ("Hue, 210 degrees"). The hue is kept when the colour is grey or black, so dragging back does not jump.
 *
 * @param color The chosen colour.
 * @param onColorChange Called with the new colour while the user drags or types a valid hex value.
 * @param showAlpha Show the opacity slider and `#RRGGBBAA` in the hex field.
 * @param showHex Show the hex field.
 * @param swatches Quick choices shown as dots under the sliders.
 * @param enabled When false nothing can be changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showAlpha: Boolean = false,
    showHex: Boolean = true,
    swatches: List<Color> = emptyList(),
    enabled: Boolean = true,
) {
    remember { KompoundStyles.ensureEnabled() }
    val strings = KompoundTheme.strings
    val own = remember { mutableStateOf(Hsv.of(color)) }
    // Keep our own hue and saturation while the colour is one we produced; take over when the app sets a different colour.
    val hsv = if (own.value.toColor() == color) own.value else Hsv.of(color)
    fun change(next: Hsv) {
        val n = if (showAlpha) next else next.copy(a = 1f)
        own.value = n
        onColorChange(n.toColor())
    }
    val hex = KColors.toHex(color, showAlpha)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SaturationBrightness(hsv, enabled, strings.saturation, strings.brightness) { s, v -> change(hsv.copy(s = s, v = v)) }
        GradientSlider(
            fraction = hsv.h / 360f, label = strings.hue, valueText = "${hsv.h.roundToInt()}°", enabled = enabled, onFraction = { change(hsv.copy(h = it * 359.99f)) },
            drawTrack = { drawRect(Brush.horizontalGradient((0..6).map { Hsv(it * 60f, 1f, 1f).toColor() })) },
        )
        if (showAlpha) {
            GradientSlider(
                fraction = hsv.a, label = strings.opacity, valueText = "${(hsv.a * 100).roundToInt()}%", enabled = enabled, onFraction = { change(hsv.copy(a = it)) },
                drawTrack = { checkerboard(); drawRect(Brush.horizontalGradient(listOf(hsv.copy(a = 0f).toColor(), hsv.copy(a = 1f).toColor()))) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).semantics { contentDescription = hex }) {
                checkerboard(); drawRect(color)
            }
            if (showHex) {
                var draft by remember(hex) { mutableStateOf(hex) }
                val parsed = KColors.parseHex(draft)
                val acceptable = parsed != null && draft.removePrefix("#").length != 3
                KTextField(
                    value = draft,
                    onValueChange = { text ->
                        draft = text
                        val c = KColors.parseHex(text)
                        if (c != null && text.removePrefix("#").length in listOf(6, 8)) change(Hsv.of(if (showAlpha) c else c.copy(alpha = 1f)))
                    },
                    modifier = Modifier.weight(1f), label = strings.hexColor, enabled = enabled,
                    isError = !acceptable && draft.isNotEmpty() && draft.removePrefix("#").length >= 6,
                    supportingText = null, maxLength = 9,
                )
            }
        }
        if (swatches.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (swatch in swatches) {
                    val selected = swatch.copy(alpha = 1f) == color.copy(alpha = 1f)
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable(enabled = enabled, role = Role.Button) { change(Hsv.of(swatch).let { if (showAlpha) it.copy(a = color.alpha) else it }) }
                            .semantics { contentDescription = KColors.toHex(swatch) },
                    ) { Canvas(Modifier.size(28.dp)) { drawRect(swatch) } }
                }
            }
        }
    }
}

private fun DrawScope.checkerboard() {
    val cell = 6.dp.toPx()
    drawRect(Color.White)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) { drawRect(Color(0xFFCCCCCC), Offset(x, y), Size(min(cell, size.width - x), min(cell, size.height - y))); x += cell * 2 }
        y += cell; row++
    }
}

@Composable
private fun SaturationBrightness(hsv: Hsv, enabled: Boolean, saturationLabel: String, brightnessLabel: String, onChange: (s: Float, v: Float) -> Unit) {
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    fun at(p: Offset) {
        if (size.width == 0 || size.height == 0) return
        onChange((p.x / size.width).coerceIn(0f, 1f), 1f - (p.y / size.height).coerceIn(0f, 1f))
    }
    val text = "$saturationLabel ${(hsv.s * 100).roundToInt()}%, $brightnessLabel ${(hsv.v * 100).roundToInt()}%"
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { size = it }
            .pointerInput(enabled) { if (enabled) detectTapGestures { at(it) } }
            .pointerInput(enabled) { if (enabled) detectDragGestures(onDragStart = { at(it) }) { change, _ -> at(change.position); change.consume() } }
            .focusable(enabled, source)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || !enabled) return@onKeyEvent false
                val d = 0.02f
                when (e.key) {
                    Key.DirectionRight -> onChange((hsv.s + d).coerceAtMost(1f), hsv.v)
                    Key.DirectionLeft -> onChange((hsv.s - d).coerceAtLeast(0f), hsv.v)
                    Key.DirectionUp -> onChange(hsv.s, (hsv.v + d).coerceAtMost(1f))
                    Key.DirectionDown -> onChange(hsv.s, (hsv.v - d).coerceAtLeast(0f))
                    Key.PageUp -> onChange(hsv.s, (hsv.v + 0.1f).coerceAtMost(1f))
                    Key.PageDown -> onChange(hsv.s, (hsv.v - 0.1f).coerceAtLeast(0f))
                    else -> return@onKeyEvent false
                }
                true
            }
            .semantics { contentDescription = "$saturationLabel, $brightnessLabel"; stateDescription = text; role = Role.Image },
    ) {
        drawRect(Hsv(hsv.h, 1f, 1f).toColor())
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val c = Offset(hsv.s * this.size.width, (1f - hsv.v) * this.size.height)
        drawCircle(Color.White, 9.dp.toPx(), c, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), 10.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun GradientSlider(
    fraction: Float,
    label: String,
    valueText: String,
    enabled: Boolean,
    onFraction: (Float) -> Unit,
    drawTrack: DrawScope.() -> Unit,
) {
    var width by remember { mutableStateOf(0) }
    val source = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    fun at(x: Float) { if (width > 0) onFraction((x / width).coerceIn(0f, 1f)) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { width = it.width }
            .pointerInput(enabled) { if (enabled) detectTapGestures { at(it.x) } }
            .pointerInput(enabled) { if (enabled) detectDragGestures(onDragStart = { at(it.x) }) { change, _ -> at(change.position.x); change.consume() } }
            .focusable(enabled, source)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || !enabled) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.DirectionUp -> onFraction((fraction + 0.01f).coerceAtMost(1f))
                    Key.DirectionLeft, Key.DirectionDown -> onFraction((fraction - 0.01f).coerceAtLeast(0f))
                    Key.PageUp -> onFraction((fraction + 0.1f).coerceAtMost(1f))
                    Key.PageDown -> onFraction((fraction - 0.1f).coerceAtLeast(0f))
                    Key.MoveHome -> onFraction(0f)
                    Key.MoveEnd -> onFraction(1f)
                    else -> return@onKeyEvent false
                }
                true
            }
            .semantics {
                contentDescription = label
                stateDescription = valueText
                role = Role.Image
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f, 99)
                setProgress { target -> onFraction(target.coerceIn(0f, 1f)); true }
            },
    ) {
        val trackH = 14.dp.toPx()
        val top = (size.height - trackH) / 2f
        // The track fills the whole canvas; clipping it to a rounded strip leaves room for the thumb above and below.
        clipPath(Path().apply { addRoundRect(RoundRect(0f, top, size.width, top + trackH, CornerRadius(trackH / 2f))) }) { drawTrack() }
        val x = fraction * size.width
        drawCircle(Color.White, 11.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), 12.5.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(1.dp.toPx()))
    }
}

/**
 * A field that shows a colour swatch and its hex value and opens a dialog with a [KColorPicker].
 *
 * @param value The colour, or `null` for none.
 * @param onValueChange Called with the confirmed colour.
 */
@Composable
public fun KColorField(
    value: Color?,
    onValueChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    showAlpha: Boolean = false,
    swatches: List<Color> = emptyList(),
    confirmText: String = KompoundTheme.strings.ok,
    dismissText: String = KompoundTheme.strings.cancel,
    style: Style = Style,
) {
    var open by remember { mutableStateOf(false) }
    PickerField(
        modifier = modifier, label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, enabled = enabled, style = style,
        displayText = value?.let { KColors.toHex(it, showAlpha) }.orEmpty(),
        open = open, onClick = { open = true }, icon = KompoundIcons.Palette, role = Role.Button, rotateIconWhenOpen = false,
    ) {
        if (open) {
            var draft by remember { mutableStateOf(value ?: Color(0xFF6750A4)) }
            KDialog(
                onDismissRequest = { open = false },
                title = label,
                actions = {
                    KButton(onClick = { open = false }, variant = KButtonVariant.Text) { KText(dismissText) }
                    KButton(onClick = { onValueChange(draft); open = false }, variant = KButtonVariant.Text) { KText(confirmText) }
                },
            ) {
                KColorPicker(draft, { draft = it }, Modifier.padding(vertical = 8.dp), showAlpha = showAlpha, swatches = swatches)
            }
        }
    }
}
