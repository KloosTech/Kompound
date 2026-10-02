package tech.kloos.kompound.showcase.textfield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KPasswordField
import tech.kloos.kompound.textfield.KPasswordStrength
import tech.kloos.kompound.textfield.KStrengthMeter

private const val Usage_textfield_password = """import tech.kloos.kompound.textfield.KPasswordField
import tech.kloos.kompound.textfield.KPasswordStrength

var password by remember { mutableStateOf("") }

// Your own policy decides the level; Kompound only draws it.
fun strengthOf(text: String): KPasswordStrength {
    val level = listOf(text.length >= 8, text.any { it.isDigit() }, text.any { it.isUpperCase() }, text.length >= 12).count { it }
    return KPasswordStrength(level = level, segments = 4, description = listOf("Very weak", "Weak", "Fair", "Good", "Strong")[level])
}

KPasswordField(
    value = password,
    onValueChange = { password = it },
    label = "Password",
    supportingText = "At least 8 characters",
    strength = ::strengthOf,
    modifier = Modifier.fillMaxWidth(),
)"""

private fun strengthOf(text: String): KPasswordStrength {
    val level = listOf(text.length >= 8, text.any { it.isDigit() }, text.any { it.isUpperCase() }, text.length >= 12).count { it }
    return KPasswordStrength(level, 4, listOf("Very weak", "Weak", "Fair", "Good", "Strong")[level])
}

@KompoundDemo(
    id = "textfield.password",
    title = "KPasswordField",
    description = "Password field with a show/hide eye button and an optional strength meter; KStrengthMeter works on its own too.",
    category = KompoundCategory.Inputs,
    tags = ["password", "text", "field", "input", "security", "strength", "meter"],
    since = "0.1.0",
    usage = Usage_textfield_password,
)
@Composable
fun DemoScope.KPasswordFieldDemo() {
    val meter = boolControl("Strength meter", true)
    val error = boolControl("Error", false)
    val enabled = boolControl("Enabled", true)
    val level = floatControl("Standalone meter level", 0f..4f, 2f)
    var password by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        KPasswordField(
            password, { password = it }, Modifier.fillMaxWidth(), label = "Password",
            supportingText = if (error) "Passwords do not match" else "At least 8 characters",
            isError = error, enabled = enabled, strength = if (meter) ::strengthOf else null,
        )
        KText("KStrengthMeter on its own")
        KStrengthMeter(level.toInt(), 4, Modifier.fillMaxWidth(), description = "Level ${level.toInt()} of 4")
    }
}
