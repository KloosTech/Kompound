package tech.kloos.kompound.textfield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

/**
 * How strong a password is: [level] of [segments] bars are lit, [description] names it for screen readers
 * ("Weak", "Strong"...). Compute it from your own policy; Kompound does not judge passwords.
 */
@Immutable
public class KPasswordStrength(public val level: Int, public val segments: Int = 4, public val description: String? = null)

/**
 * Text field for a password: the text is masked, a trailing eye button shows or hides it, and the keyboard is
 * asked not to autocorrect or capitalise. Pass [strength] to show a strength meter under the field.
 *
 * Whether the text is visible survives configuration changes. The password itself is never stored or logged
 * by the component. Known limitation: after the text is shown, screen readers still announce the field as a
 * password field (the framework only reads that flag when the field is created).
 *
 * @param value The password.
 * @param onValueChange Called with the new password on every edit.
 * @param modifier Modifier applied to the outermost node.
 * @param label Label above the field.
 * @param placeholder Hint while the field is empty.
 * @param supportingText Text under the field (and the strength meter); the error colour applies when [isError].
 * @param isError Draws the field in its error state.
 * @param enabled When false the field ignores input.
 * @param strength Computes the strength of the current text; `null` hides the meter.
 * @param showDescription Accessibility description of the eye button while the text is hidden.
 * @param hideDescription Accessibility description of the eye button while the text is visible.
 * @param keyboardOptions Keyboard options; defaults to a password keyboard.
 * @param keyboardActions IME actions such as Done.
 * @param style Overrides merged over the field's default style.
 */
@Composable
public fun KPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    strength: ((password: String) -> KPasswordStrength)? = null,
    showDescription: String = "Show password",
    hideDescription: String = "Hide password",
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    style: Style = Style,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label,
            placeholder = placeholder,
            isError = isError,
            enabled = enabled,
            trailing = {
                KIconButton(onClick = { visible = !visible }, contentDescription = if (visible) hideDescription else showDescription, enabled = enabled) {
                    KIcon(if (visible) KompoundIcons.EyeOff else KompoundIcons.Eye, contentDescription = null)
                }
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            style = style,
        )
        if (strength != null) {
            val current = remember(value, strength) { strength(value) }
            KStrengthMeter(current.level, current.segments, Modifier.fillMaxWidth().padding(horizontal = 16.dp), description = current.description)
        }
        if (supportingText != null) {
            KText(supportingText, Modifier.padding(horizontal = 16.dp), style = KTextFieldDefaults.supportingStyle(isError, enabled))
        }
    }
}

/**
 * A row of bars of which [level] are lit, for password strength or any small ordinal scale. The colour moves
 * from the error colour through warning to success as the level rises; unlit bars use the track colour.
 *
 * @param level Number of lit bars, from 0 to [segments].
 * @param segments Number of bars.
 * @param modifier Modifier applied to the meter.
 * @param description Accessibility description of the current level, such as "Weak". The level is also exposed
 * as progress.
 * @param style Overrides merged over a transparent base; use it for size and padding.
 */
@Composable
public fun KStrengthMeter(
    level: Int,
    segments: Int = 4,
    modifier: Modifier = Modifier,
    description: String? = null,
    style: Style = Style,
) {
    val lit = KStrengthMeterDefaults.color(level, segments)
    val track = MaterialTheme.colorScheme.outlineVariant
    val count = segments.coerceAtLeast(1)
    val filled = level.coerceIn(0, count)
    Column(
        modifier
            .height(KStrengthMeterDefaults.Height)
            .semantics {
                if (description != null) contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(filled.toFloat(), 0f..count.toFloat(), count - 1)
            }
            .drawBehind {
                val gap = 4.dp.toPx()
                val width = (size.width - gap * (count - 1)) / count
                for (i in 0 until count) {
                    drawRoundRect(
                        color = if (i < filled) lit else track,
                        topLeft = Offset(i * (width + gap), 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
            },
    ) {}
}

/** Defaults for [KStrengthMeter]. */
public object KStrengthMeterDefaults {
    /** Bar height. */
    public val Height: androidx.compose.ui.unit.Dp = 4.dp

    /** Lit colour for [level] of [segments]: error below a third, warning below two thirds, success above. */
    @Composable
    public fun color(level: Int, segments: Int): Color {
        val c = MaterialTheme.colorScheme
        val k = KompoundTheme.tokens.colors
        val ratio = level.toFloat() / segments.coerceAtLeast(1)
        return when {
            ratio < 0.34f -> c.error
            ratio < 0.67f -> k.warning
            else -> k.success
        }
    }
}
