package tech.kloos.kompound.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Receiver of demo functions (`@Composable fun DemoScope.MyDemo()`). Each call declares an interactive
 * control the catalog shows next to the demo and returns its current value, so a visitor can try a
 * component's states without code. Calls must be stable per composition (same names, same order).
 */
public interface DemoScope {
    /** A text field. */
    @Composable
    public fun textControl(name: String, initial: String = ""): String

    /** A switch. */
    @Composable
    public fun boolControl(name: String, initial: Boolean = false): Boolean

    /** A single choice among [options], shown as chips. [label] renders an option's text. */
    @Composable
    public fun <T> choiceControl(name: String, options: List<T>, initial: T = options.first(), label: (T) -> String = { it.toString() }): T

    /** A slider over [range]. */
    @Composable
    public fun floatControl(name: String, range: ClosedFloatingPointRange<Float>, initial: Float): Float
}

/** A control declared by a demo. The catalog renders one UI element per control and edits its state. */
public sealed class DemoControl(public val name: String)

public class TextControl internal constructor(name: String, initial: String) : DemoControl(name) {
    public var value: String by mutableStateOf(initial)
}

public class BoolControl internal constructor(name: String, initial: Boolean) : DemoControl(name) {
    public var value: Boolean by mutableStateOf(initial)
}

public class ChoiceControl internal constructor(
    name: String,
    public val options: List<String>,
    initialIndex: Int,
) : DemoControl(name) {
    public var selectedIndex: Int by mutableIntStateOf(initialIndex)
}

public class FloatControl internal constructor(
    name: String,
    public val range: ClosedFloatingPointRange<Float>,
    initial: Float,
) : DemoControl(name) {
    public var value: Float by mutableFloatStateOf(initial.coerceIn(range))
}

/**
 * [DemoScope] implementation that records declared controls in [controls] (observable) while the demo
 * is composed. Create one per displayed demo.
 */
public class DemoControls : DemoScope {
    private val _controls = mutableStateListOf<DemoControl>()

    /** Controls of the demo currently composed, in declaration order. */
    public val controls: List<DemoControl> get() = _controls

    @Composable
    private fun Register(control: DemoControl) {
        DisposableEffect(control) {
            _controls.add(control)
            onDispose { _controls.remove(control) }
        }
    }

    @Composable
    override fun textControl(name: String, initial: String): String {
        val c = remember(name) { TextControl(name, initial) }
        Register(c)
        return c.value
    }

    @Composable
    override fun boolControl(name: String, initial: Boolean): Boolean {
        val c = remember(name) { BoolControl(name, initial) }
        Register(c)
        return c.value
    }

    @Composable
    override fun <T> choiceControl(name: String, options: List<T>, initial: T, label: (T) -> String): T {
        val c = remember(name) { ChoiceControl(name, options.map(label), options.indexOf(initial).coerceAtLeast(0)) }
        Register(c)
        return options[c.selectedIndex.coerceIn(options.indices)]
    }

    @Composable
    override fun floatControl(name: String, range: ClosedFloatingPointRange<Float>, initial: Float): Float {
        val c = remember(name) { FloatControl(name, range, initial) }
        Register(c)
        return c.value
    }
}
