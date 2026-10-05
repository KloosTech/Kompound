package tech.kloos.kompound.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.kloos.kompound.i18n.KompoundStrings
import tech.kloos.kompound.theme.KompoundTheme

/** Checks one value and returns the message to show, or `null` when it is fine. */
public typealias KValidator<T> = (T) -> String?

/**
 * Ready-made [KValidator]s. They take the messages from the current [KompoundStrings] when you pass none, so they follow the language.
 * Combine several on one field; the first message wins.
 */
public object KValidators {
    /** The text must not be blank. */
    public fun required(message: String? = null): KValidator<String> = { if (it.isBlank()) message ?: DefaultStrings.current.required else null }

    /** At least [min] characters (an empty text passes: combine with [required]). */
    public fun minLength(min: Int, message: String? = null): KValidator<String> =
        { if (it.isNotEmpty() && it.length < min) message ?: DefaultStrings.current.tooShort(min) else null }

    /** At most [max] characters. */
    public fun maxLength(max: Int, message: String? = null): KValidator<String> = { if (it.length > max) message ?: DefaultStrings.current.invalidValue else null }

    /** A plain e-mail shape: something, `@`, something with a dot (an empty text passes). */
    public fun email(message: String? = null): KValidator<String> = {
        val at = it.indexOf('@')
        val ok = it.isEmpty() || (at > 0 && at == it.lastIndexOf('@') && it.indexOf('.', at) > at + 1 && !it.endsWith(".") && it.none { c -> c.isWhitespace() })
        if (ok) null else message ?: DefaultStrings.current.invalidEmail
    }

    /** The text must match [regex] entirely (an empty text passes). */
    public fun pattern(regex: Regex, message: String? = null): KValidator<String> =
        { if (it.isNotEmpty() && !regex.matches(it)) message ?: DefaultStrings.current.invalidValue else null }

    /** A number in [range] (an empty text passes; text that is not a number fails). */
    public fun intInRange(range: IntRange, message: String? = null): KValidator<String> = {
        val n = it.trim().toIntOrNull()
        if (it.isEmpty() || (n != null && n in range)) null else message ?: DefaultStrings.current.invalidValue
    }

    /** Any check on any value type. */
    public fun <T> check(message: String, ok: (T) -> Boolean): KValidator<T> = { if (ok(it)) null else message }

    /** The value must be `true` (a checkbox that has to be ticked). */
    public fun isTrue(message: String? = null): KValidator<Boolean> = { if (it) null else message ?: DefaultStrings.current.required }

    /** A value that must not be `null` (a dropdown that needs a choice). */
    public fun <T : Any> notNull(message: String? = null): KValidator<T?> = { if (it != null) null else message ?: DefaultStrings.current.required }

    /** Strings are resolved when a validator runs; [KFormState] sets this while it validates inside a composition. */
    internal object DefaultStrings {
        var current: KompoundStrings = KompoundStrings.English
    }
}

/**
 * The state of one form field: its [value], whether the user has been in it ([touched]), the message from [validators] and one from the
 * server ([serverError]).
 *
 * The [error] to show appears once the field was touched (the user left it) or a submit was tried, and then follows every edit; setting
 * [serverError] shows a message from your backend until the value changes.
 */
@Stable
public class KFieldState<T> internal constructor(
    /** Identity inside the form; use it to put a server message on the right field. */
    public val key: String,
    /** The value the field started with ([KFormState.reset] returns to it). */
    public val initial: T,
    private val validators: List<KValidator<T>>,
    private val form: KFormState?,
) {
    /** The current value. */
    public var value: T by mutableStateOf(initial)

    /** Whether the user has been in this field and left it. */
    public var touched: Boolean by mutableStateOf(false)

    /** A message from outside the form (a failed submit); cleared when the value changes. */
    public var serverError: String? by mutableStateOf(null)

    internal val focusRequester: FocusRequester = FocusRequester()

    /** The first message of the validators for the current value, regardless of whether it should be shown yet. */
    public val validationMessage: String? get() = validators.firstNotNullOfOrNull { it(value) }

    /** The message to show under the field now: the server's, or the validators' once [touched] or a submit was tried; otherwise `null`. */
    public val error: String? get() = serverError ?: if (touched || form?.submitAttempted == true) validationMessage else null

    /** Whether the value is acceptable (no validator complains; a server message does not count). */
    public val isValid: Boolean get() = validationMessage == null

    /** Whether the value differs from [initial]. */
    public val isDirty: Boolean get() = value != initial

    /** Changes the value and clears the server's message. */
    public fun onValueChange(new: T) {
        value = new
        serverError = null
    }

    /** Marks the field as visited (call it when focus leaves). */
    public fun markTouched() { touched = true }

    /** Moves focus to the field, if it is on screen. */
    public fun requestFocus() { runCatching { focusRequester.requestFocus() } }

    internal fun reset() {
        value = initial
        touched = false
        serverError = null
    }
}

/**
 * Collects the fields of a form and runs its lifecycle: edit, validate, submit, show the result.
 *
 * Create one with [rememberKFormState], create fields with [rememberKField] (or [KFormState.field]), put the controls in a [KForm] and
 * call [submit] from a button ([KSubmitButton] does). A submit validates every field first; when something is invalid nothing is sent,
 * every error becomes visible, focus moves to the first invalid field and a summary is announced. Otherwise [isSubmitting] is `true`
 * while your suspend block runs, and a thrown exception becomes [submitError].
 */
@Stable
public class KFormState internal constructor(private val scope: CoroutineScope?) {
    private val registered = mutableStateListOf<KFieldState<*>>()

    /** The fields in the order they were created. */
    public val fields: List<KFieldState<*>> get() = registered

    /** Whether a submit was tried (all errors show from then on). */
    public var submitAttempted: Boolean by mutableStateOf(false)
        internal set

    /** Whether a submit is running. */
    public var isSubmitting: Boolean by mutableStateOf(false)
        private set

    /** Whether the last submit finished without an exception and without being refused by validation. */
    public var submitted: Boolean by mutableStateOf(false)
        private set

    /** The message of the exception the last submit threw, or `null`. */
    public var submitError: String? by mutableStateOf(null)

    private var job: Job? = null

    /** Whether every field passes its validators. */
    public val isValid: Boolean get() = registered.all { it.isValid }

    /** Whether any field differs from its initial value. */
    public val isDirty: Boolean get() = registered.any { it.isDirty }

    /** How many fields currently show an error (what the summary announces). */
    public val errorCount: Int get() = registered.count { it.error != null }

    /** The field with [key], or `null`. Fields are typed by whoever made them; pass the type you used. */
    @Suppress("UNCHECKED_CAST")
    public fun <T> fieldOrNull(key: String): KFieldState<T>? = registered.firstOrNull { it.key == key } as KFieldState<T>?

    /** A new field registered with this form (unregister it with [remove]; [rememberKField] does both). */
    public fun <T> field(key: String, initial: T, vararg validators: KValidator<T>): KFieldState<T> =
        KFieldState(key, initial, validators.toList(), this).also { registered += it }

    /** Unregisters [field]. */
    public fun remove(field: KFieldState<*>) { registered.remove(field) }

    /** Puts a message from your backend on the field [key] (it shows until that field's value changes). Unknown keys are ignored. */
    public fun setServerError(key: String, message: String) { fieldOrNull<Any?>(key)?.serverError = message }

    /** Validates; when everything is fine, runs [onSubmit] and reports through [isSubmitting] and [submitError]. Returns `false` when validation refused. */
    public fun submit(onSubmit: suspend (KFormState) -> Unit): Boolean {
        if (isSubmitting) return false
        submitAttempted = true
        submitted = false
        submitError = null
        val firstInvalid = registered.firstOrNull { it.validationMessage != null || it.serverError != null }
        if (firstInvalid != null) {
            firstInvalid.requestFocus()
            return false
        }
        val launcher = scope ?: error("This KFormState was not made with rememberKFormState()")
        isSubmitting = true
        job = launcher.launch {
            try {
                onSubmit(this@KFormState)
                submitted = registered.none { it.serverError != null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                submitError = e.message ?: e.toString()
            } finally {
                isSubmitting = false
            }
        }
        return true
    }

    /** Back to the initial values, no errors, not submitted. */
    public fun reset() {
        job?.cancel()
        isSubmitting = false
        submitAttempted = false
        submitted = false
        submitError = null
        registered.forEach { it.reset() }
    }
}

/** Creates a [KFormState] that lives as long as the composition and runs submits in its coroutine scope. */
@Composable
public fun rememberKFormState(): KFormState {
    val scope = rememberCoroutineScope()
    return remember { KFormState(scope) }
}

/** A field of [form] that lives as long as the composition (created once, removed again when this leaves). */
@Composable
public fun <T> rememberKField(form: KFormState, key: String, initial: T, vararg validators: KValidator<T>): KFieldState<T> {
    val strings = KompoundTheme.strings
    KValidators.DefaultStrings.current = strings
    val field = remember(form, key) { form.field(key, initial, *validators) }
    DisposableEffect(form, field) { onDispose { form.remove(field) } }
    return field
}
