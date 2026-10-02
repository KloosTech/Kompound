package sample

import androidx.compose.runtime.Composable
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.text.KText

@KompoundDemo(id = "my.button", title = "My button", category = KompoundCategory.Buttons, tags = ["sample"])
@Composable
fun MyButtonDemo() {
    KButton(onClick = {}) { KText("From a consumer project") }
}
