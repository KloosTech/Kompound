package tech.kloos.kompound.showcase.animation

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
import tech.kloos.kompound.animation.KShakeSpec
import tech.kloos.kompound.animation.rememberKShakeState
import tech.kloos.kompound.animation.shake
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField

private const val Usage_animation_shake = """import tech.kloos.kompound.animation.KShakeSpec
import tech.kloos.kompound.animation.rememberKShakeState
import tech.kloos.kompound.animation.shake
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField

val shake = rememberKShakeState()
var code by remember { mutableStateOf("") }

// Attach the modifier once; it only draws while a shake runs.
KTextField(value = code, onValueChange = { code = it }, label = "Code", modifier = Modifier.shake(shake))

KButton(onClick = { if (code != "1234") shake.shake() }) {   // KShakeSpec.Error by default
    KText("Check")
}

// Other motions, or your own: KShakeSpec(translateX = 12.dp, rotate = 3f, swings = 8)
shake.shake(KShakeSpec.Wobble)"""

@KompoundDemo(
    id = "animation.shake",
    title = "Shake",
    description = "Modifier that shakes, nods or wobbles the drawn element on demand, for rejected input or to draw attention.",
    category = KompoundCategory.Animation,
    tags = ["animation", "shake", "error", "feedback", "wobble", "modifier"],
    since = "0.1.0",
    usage = Usage_animation_shake,
)
@Composable
fun DemoScope.KShakeDemo() {
    val preset = choiceControl("Preset", listOf("Error", "Nod", "Wobble", "Custom"))
    val distance = floatControl("Distance (dp)", 0f..24f, 10f)
    val swings = floatControl("Swings", 1f..12f, 6f)
    val shake = rememberKShakeState()
    var text by remember { mutableStateOf("1234") }
    val spec = when (preset) {
        "Nod" -> KShakeSpec.Nod
        "Wobble" -> KShakeSpec.Wobble
        "Custom" -> KShakeSpec(translateX = distance.dp, rotate = distance / 4f, swings = swings.toInt())
        else -> KShakeSpec.Error
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KTextField(text, { text = it }, Modifier.fillMaxWidth().shake(shake), label = "Wrong code?")
        KButton(onClick = { shake.shake(spec) }) { KText("Shake") }
    }
}
