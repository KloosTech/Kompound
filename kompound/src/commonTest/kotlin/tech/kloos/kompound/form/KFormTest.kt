package tech.kloos.kompound.form

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class KFormTest {
    @Test
    fun validatorsGiveMessagesOrNull() {
        assertEquals("Required", KValidators.required()(" "))
        assertNull(KValidators.required()("x"))
        assertEquals("Use at least 3 characters", KValidators.minLength(3)("ab"))
        assertNull(KValidators.minLength(3)(""), "empty is the job of required")
        assertNull(KValidators.email()("a@b.co"))
        assertNull(KValidators.email()(""))
        for (bad in listOf("a", "a@", "@b.c", "a@b", "a@@b.c", "a b@c.de", "a@b.c.")) assertEquals("Enter a valid e-mail address", KValidators.email()(bad), bad)
        assertEquals("custom", KValidators.pattern(Regex("[0-9]+"), "custom")("12a"))
        assertNull(KValidators.intInRange(1..5)("3"))
        assertEquals("Invalid value", KValidators.intInRange(1..5)("9"))
        assertEquals("nope", KValidators.isTrue("nope")(false))
        assertEquals("Required", KValidators.notNull<String>()(null))
    }

    @Test
    fun errorsAppearAfterTouchOrASubmitAttemptThenFollowEdits() = runTest {
        val form = KFormState(this)
        val name = form.field("name", "", KValidators.required())
        assertNull(name.error, "untouched: no error yet")
        assertFalse(name.isValid)
        name.markTouched()
        assertEquals("Required", name.error)
        name.onValueChange("Ann")
        assertNull(name.error)
        assertTrue(name.isDirty)
        name.onValueChange("")
        assertEquals("Required", name.error, "follows the edit once shown")
    }

    @Test
    fun aRefusedSubmitSendsNothingAndShowsEveryError() = runTest {
        val form = KFormState(this)
        val a = form.field("a", "", KValidators.required())
        val b = form.field("b", "x", KValidators.required())
        var sent = false
        assertFalse(form.submit { sent = true })
        advanceUntilIdle()
        assertFalse(sent)
        assertEquals("Required", a.error)
        assertEquals(1, form.errorCount)
        assertTrue(form.submitAttempted)
        assertNull(b.error)
    }

    @Test
    fun aValidSubmitRunsTheBlockAndReportsTheLifecycle() = runTest {
        val form = KFormState(this)
        form.field("a", "ok", KValidators.required())
        val gate = CompletableDeferred<Unit>()
        assertTrue(form.submit { gate.await() })
        assertTrue(form.isSubmitting)
        assertFalse(form.submit { error("a second submit while one runs is refused") })
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(form.isSubmitting)
        assertTrue(form.submitted)
        assertNull(form.submitError)
    }

    @Test
    fun anExceptionBecomesTheSubmitErrorAndServerMessagesLandOnTheirField() = runTest {
        val form = KFormState(this)
        val email = form.field("email", "a@b.co", KValidators.email())
        form.submit { throw IllegalStateException("server down") }
        advanceUntilIdle()
        assertEquals("server down", form.submitError)
        assertFalse(form.submitted)
        form.submit { it.setServerError("email", "Already taken") }
        advanceUntilIdle()
        assertEquals("Already taken", email.error)
        assertFalse(form.submitted, "a server message means it did not go through")
        email.onValueChange("c@d.co")
        assertNull(email.error, "editing clears the server's message")
    }

    @Test
    fun resetReturnsToTheInitialState() = runTest {
        val form = KFormState(this)
        val f = form.field("f", "start", KValidators.required())
        f.onValueChange("")
        form.submit { }
        form.reset()
        assertEquals("start", f.value)
        assertFalse(form.submitAttempted)
        assertNull(f.error)
        assertFalse(form.isDirty)
    }

    @Test
    fun theFormUiRefusesAnInvalidSubmitAndSendsAValidOne() = runComposeUiTest {
        var sent by mutableStateOf<String?>(null)
        setContent {
            MaterialTheme(lightColorScheme()) {
                val form = rememberKFormState()
                val email = rememberKField(form, "email", "", KValidators.required(), KValidators.email())
                KForm(form) {
                    KFormTextField(email, label = "Email")
                    KSubmitButton(form, onSubmit = { sent = email.value })
                }
            }
        }
        onNodeWithText("Submit").performClick()
        waitForIdle()
        assertEquals(null, sent)
        onNodeWithText("Required").assertIsDisplayed()
        onNodeWithText("1 field needs attention").assertIsDisplayed()
        onNode(hasSetTextAction()).performTextInput("nope")
        waitForIdle()
        onNodeWithText("Enter a valid e-mail address").assertIsDisplayed()
        onNode(hasSetTextAction()).performTextInput("@x.org")
        waitForIdle()
        onNodeWithText("Submit").performClick()
        waitForIdle()
        assertEquals("nope@x.org", sent)
        onAllNodesWithText("1 field needs attention").assertCountEquals(0)
    }

    @Test
    fun theSubmitButtonIsDisabledWhileSubmitting() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        setContent {
            MaterialTheme(lightColorScheme()) {
                val form = rememberKFormState()
                KForm(form) { KSubmitButton(form, onSubmit = { gate.await() }) }
            }
        }
        onNodeWithText("Submit").assertIsEnabled()
        onNodeWithText("Submit").performClick()
        mainClock.advanceTimeByFrame()
        waitForIdle()
        onNodeWithText("Submitting").assertIsNotEnabled()
        gate.complete(Unit)
        waitForIdle()
        onNodeWithText("Submit").assertIsEnabled()
    }

    @Test
    fun aGenericFieldShowsItsErrorUnderTheControl() = runComposeUiTest {
        setContent {
            MaterialTheme(lightColorScheme()) {
                val form = rememberKFormState()
                val terms = rememberKField(form, "terms", false, KValidators.isTrue("Accept the terms"))
                KForm(form) {
                    KFormField(terms) { checked, change, _ -> KText(if (checked) "accepted" else "not accepted", androidx.compose.ui.Modifier.also { }) }
                    KSubmitButton(form, onSubmit = {})
                }
            }
        }
        onNodeWithText("Submit").performClick()
        waitForIdle()
        onNodeWithText("Accept the terms").assertIsDisplayed()
    }
}
