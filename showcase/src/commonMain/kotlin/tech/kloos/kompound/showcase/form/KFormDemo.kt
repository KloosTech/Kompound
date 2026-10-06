package tech.kloos.kompound.showcase.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.form.KFieldGroup
import tech.kloos.kompound.form.KForm
import tech.kloos.kompound.form.KFormField
import tech.kloos.kompound.form.KFormTextField
import tech.kloos.kompound.form.KSubmitButton
import tech.kloos.kompound.form.KValidators
import tech.kloos.kompound.form.rememberKField
import tech.kloos.kompound.form.rememberKFormState
import tech.kloos.kompound.selection.KCheckbox
import tech.kloos.kompound.text.KText

private const val Usage_form = """import tech.kloos.kompound.form.*

val form = rememberKFormState()
val email = rememberKField(form, "email", "", KValidators.required(), KValidators.email())
val terms = rememberKField(form, "terms", false, KValidators.isTrue("Accept the terms"))

KForm(form) {
    KFieldGroup("Account") {
        KFormTextField(email, label = "E-mail")
    }
    KFormField(terms) { checked, onChange, _ -> KCheckbox(checked, onChange) { KText("I accept the terms") } }
    KSubmitButton(form, onSubmit = { api.signUp(email.value) })   // throw, or form.setServerError("email", "Taken")
}
// Errors show after a field was left or a submit was tried; a refused submit focuses the first problem."""

@KompoundDemo(
    id = "form.signup",
    title = "KForm",
    description = "Validation, field groups and a submit lifecycle: errors after touch or submit, focus on the first problem, a busy button and server messages.",
    category = KompoundCategory.Inputs,
    tags = ["form", "validation", "submit", "field", "error", "signup"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_form,
)
@Composable
fun DemoScope.KFormDemo() {
    val slow = boolControl("Slow server (1.5 s)", true)
    val failServer = boolControl("Server fails", false)
    val form = rememberKFormState()
    val name = rememberKField(form, "name", "", KValidators.required(), KValidators.minLength(2))
    val email = rememberKField(form, "email", "", KValidators.required(), KValidators.email())
    val age = rememberKField(form, "age", "", KValidators.intInRange(13..120))
    val terms = rememberKField(form, "terms", false, KValidators.isTrue("Please accept the terms"))
    Column(Modifier.padding(16.dp)) {
        KForm(form) {
            KFieldGroup("Account", description = "Try \"taken@example.com\" to see a message from the server.") {
                KFormTextField(name, Modifier.fillMaxWidth(), label = "Name")
                KFormTextField(email, Modifier.fillMaxWidth(), label = "E-mail", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                KFormTextField(age, Modifier.fillMaxWidth(), label = "Age (optional)", helperText = "13 to 120", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            KFormField(terms) { checked, onChange, _ -> KCheckbox(checked, onChange, label = { KText("I accept the terms") }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KSubmitButton(form, onSubmit = {
                    if (slow) delay(1500)
                    if (failServer) error("The server is unreachable")
                    if (email.value.equals("taken@example.com", ignoreCase = true)) it.setServerError("email", "This address is already registered")
                })
                KButton(onClick = { form.reset() }, variant = KButtonVariant.Text) { KText("Reset") }
            }
            if (form.submitted) KText("Welcome, ${name.value}!")
        }
    }
}
