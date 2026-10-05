package tech.kloos.kompound.time

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.buttons.KIconButtonSize
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.segmented.KSegmentedControl
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.PickerField
import tech.kloos.kompound.theme.KompoundTheme

/** A time of day: [hour] 0..23 and [minute] 0..59. */
@Immutable
public data class KTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23) { "hour must be 0..23, was $hour" }
        require(minute in 0..59) { "minute must be 0..59, was $minute" }
    }

    /** Minutes since midnight. */
    val minutesOfDay: Int get() = hour * 60 + minute

    /** `09:05` (24 hour clock). */
    override fun toString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    public companion object {
        /** The time [minutes] after midnight (wraps around a day). */
        public fun ofMinutes(minutes: Int): KTime = ((minutes % 1440) + 1440).let { it % 1440 }.let { KTime(it / 60, it % 60) }

        /** Reads `H:mm` or `HH:mm` (24 hour), or `null`. */
        public fun parse(text: String): KTime? {
            val parts = text.trim().split(':')
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return if (h in 0..23 && m in 0..59) KTime(h, m) else null
        }
    }
}

/** How a [KTime] reads in a field: `14:05`, or `2:05 PM` for [is24Hour] = `false`. */
public fun KTime.format(is24Hour: Boolean = true, am: String = "AM", pm: String = "PM"): String =
    if (is24Hour) toString() else "${if (hour % 12 == 0) 12 else hour % 12}:${minute.toString().padStart(2, '0')} ${if (hour < 12) am else pm}"

/**
 * Picks a time of day with two spinners (hours and minutes) and, on a 12 hour clock, an AM/PM switch. Each spinner has up and down buttons; with
 * a spinner focused the Up and Down arrow keys change it by one ([minuteStep] for minutes), Page Up and Page Down by more, Home and End jump to
 * the first and last value, and typed digits set it (`1`, `4` makes 14). Screen readers hear "Hour, 14" and can step the value.
 *
 * @param time The chosen time.
 * @param onTimeChange Called with the new time.
 * @param is24Hour Show 0..23 hours instead of 1..12 with AM/PM.
 * @param minuteStep Minutes move in steps of this size (5 for a calendar app); typed minutes are rounded to it.
 * @param enabled When false nothing can be changed.
 */
@Composable
public fun KTimePicker(
    time: KTime,
    onTimeChange: (KTime) -> Unit,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    minuteStep: Int = 1,
    enabled: Boolean = true,
) {
    remember { KompoundStyles.ensureEnabled() }
    val strings = KompoundTheme.strings
    val step = minuteStep.coerceIn(1, 30)
    val shownHour = if (is24Hour) time.hour else (if (time.hour % 12 == 0) 12 else time.hour % 12)

    fun withHour(h24: Int) = onTimeChange(KTime(h24.coerceIn(0, 23), time.minute))
    fun withMinute(m: Int) = onTimeChange(KTime(time.hour, ((m % 60) + 60) % 60))
    // Hours wrap inside their half day on a 12 hour clock, so stepping never flips AM and PM by accident.
    fun hourBy(delta: Int): Int = if (is24Hour) ((time.hour + delta) % 24 + 24) % 24
        else (time.hour / 12) * 12 + (((time.hour % 12) + delta) % 12 + 12) % 12
    fun hourFromShown(value: Int): Int = if (is24Hour) value else (time.hour / 12) * 12 + (value % 12)

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TimeSegment(
            label = strings.hour, text = shownHour.toString().padStart(2, '0'), value = shownHour, range = if (is24Hour) 0..23 else 1..12, enabled = enabled,
            onStep = { d -> withHour(hourBy(d)) }, onSet = { withHour(hourFromShown(it)) },
        )
        KText(":")
        TimeSegment(
            label = strings.minute, text = time.minute.toString().padStart(2, '0'), value = time.minute, range = 0..59, enabled = enabled, bigStep = 10,
            onStep = { d -> withMinute(if (step == 1) time.minute + d else (roundToStep(time.minute, step) + d * step)) },
            onSet = { withMinute(roundToStep(it, step)) },
        )
        if (!is24Hour) {
            KSegmentedControl(
                options = listOf(strings.am, strings.pm), selectedIndex = if (time.hour < 12) 0 else 1,
                onSelectedIndexChange = { i -> if (i == 0 && time.hour >= 12) withHour(time.hour - 12) else if (i == 1 && time.hour < 12) withHour(time.hour + 12) },
                enabled = enabled,
            )
        }
    }
}

private fun roundToStep(value: Int, step: Int): Int = ((value + step / 2) / step * step).coerceAtMost(60 - step).coerceAtLeast(0)

@Composable
private fun TimeSegment(
    label: String,
    text: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onStep: (Int) -> Unit,
    onSet: (Int) -> Unit,
    bigStep: Int = 3,
) {
    val source = remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    val focus = remember { FocusRequester() }
    var typed by remember { mutableIntStateOf(-1) }   // first digit of a two digit entry, or -1
    val scheme = MaterialTheme.colorScheme
    val look = remember(scheme) {
        Style {
            background(scheme.surfaceContainerHighest)
            contentColor(scheme.onSurface)
            shape(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            borderWidth(2.dp)
            borderColor(Color.Transparent)
            contentPadding(horizontal = 12.dp, vertical = 12.dp)
            minWidth(72.dp)
            textStyle(androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(36f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.SemiBold, color = scheme.onSurface))
            focused { borderColor(scheme.primary) }
        }
    }
    val strings = KompoundTheme.strings
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        KIconButton(onClick = { onStep(1) }, contentDescription = "$label +", enabled = enabled, size = KIconButtonSize.Small) { KIcon(KompoundIcons.ChevronUp, contentDescription = null) }
        Box(
            Modifier
                .focusRequester(focus)
                .onFocusChanged { if (!it.isFocused) typed = -1 }
                .pointerInput(enabled) { if (enabled) detectTapGestures { focus.requestFocus() } }
                .focusable(enabled, source)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || !enabled) return@onKeyEvent false
                    val digit = when (e.key) {
                        Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3
                        Key.Four, Key.NumPad4 -> 4; Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7
                        Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9; else -> -1
                    }
                    when {
                        digit >= 0 -> {
                            val two = if (typed >= 0) typed * 10 + digit else -1
                            if (two in range) { onSet(two); typed = -1 } else if (digit in range || (range.first > 0 && digit == 0)) {
                                if (digit in range) onSet(digit)
                                typed = if (digit * 10 <= range.last) digit else -1
                            }
                            true
                        }
                        e.key == Key.DirectionUp -> { onStep(1); true }
                        e.key == Key.DirectionDown -> { onStep(-1); true }
                        e.key == Key.PageUp -> { onStep(bigStep); true }
                        e.key == Key.PageDown -> { onStep(-bigStep); true }
                        e.key == Key.MoveHome -> { onSet(range.first); true }
                        e.key == Key.MoveEnd -> { onSet(range.last); true }
                        else -> false
                    }
                }
                .semantics {
                    contentDescription = label
                    stateDescription = text
                    role = Role.ValuePicker
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat(), (range.last - range.first - 1).coerceAtLeast(0))
                    setProgress { target -> onSet(target.toInt().coerceIn(range.first, range.last)); true }
                }
                .styleable(state, look),
            contentAlignment = Alignment.Center,
        ) { KText(text) }
        KIconButton(onClick = { onStep(-1) }, contentDescription = "$label -", enabled = enabled, size = KIconButtonSize.Small) { KIcon(KompoundIcons.ChevronDown, contentDescription = null) }
    }
}

/**
 * A field that shows a time and opens a dialog with a [KTimePicker], like [tech.kloos.kompound.date.KDateField] does for dates.
 *
 * @param value The time, or `null` for none.
 * @param onValueChange Called with the confirmed time.
 * @param is24Hour 24 hour or 12 hour clock in the field and the picker.
 * @param formatTime How the time reads in the field; default: [format] for the clock chosen by [is24Hour].
 * @param confirmText Label of the confirm button.
 * @param dismissText Label of the cancel button.
 */
@Composable
public fun KTimeField(
    value: KTime?,
    onValueChange: (KTime?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    is24Hour: Boolean = true,
    minuteStep: Int = 1,
    formatTime: ((KTime) -> String)? = null,
    confirmText: String = KompoundTheme.strings.ok,
    dismissText: String = KompoundTheme.strings.cancel,
    style: Style = Style,
) {
    var open by remember { mutableStateOf(false) }
    val strings = KompoundTheme.strings
    val format = formatTime ?: { t: KTime -> t.format(is24Hour, strings.am, strings.pm) }
    PickerField(
        modifier = modifier, label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, enabled = enabled, style = style,
        displayText = value?.let(format).orEmpty(),
        open = open, onClick = { open = true }, icon = KompoundIcons.Clock, role = androidx.compose.ui.semantics.Role.Button, rotateIconWhenOpen = false,
    ) {
        if (open) {
            var draft by remember { mutableStateOf(value ?: KTime(12, 0)) }
            KDialog(
                onDismissRequest = { open = false },
                title = label,
                actions = {
                    KButton(onClick = { open = false }, variant = KButtonVariant.Text) { KText(dismissText) }
                    KButton(onClick = { onValueChange(draft); open = false }, variant = KButtonVariant.Text) { KText(confirmText) }
                },
            ) {
                KTimePicker(draft, { draft = it }, Modifier.padding(vertical = 8.dp), is24Hour = is24Hour, minuteStep = minuteStep)
            }
        }
    }
}
