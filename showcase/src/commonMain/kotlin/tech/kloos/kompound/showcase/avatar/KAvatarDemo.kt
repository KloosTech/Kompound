package tech.kloos.kompound.showcase.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.avatar.KAvatar
import tech.kloos.kompound.avatar.KAvatarSize
import tech.kloos.kompound.avatar.KAvatarStatus
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText

private const val Usage_avatar_basic = """import tech.kloos.kompound.avatar.KAvatar
import tech.kloos.kompound.avatar.KAvatarSize
import tech.kloos.kompound.avatar.KAvatarStatus

// Initials from the name; pass image = { ... } to draw a picture instead.
KAvatar(name = "Ada Lovelace")

KAvatar(name = "Grace Hopper", size = KAvatarSize.Large, status = KAvatarStatus.Online)"""

@KompoundDemo(
    id = "avatar.basic",
    title = "KAvatar",
    description = "Round avatar with initials on a name-derived colour, an image slot and a presence dot.",
    category = KompoundCategory.Display,
    tags = ["avatar", "user", "profile", "presence", "initials"],
    since = "0.1.0",
    usage = Usage_avatar_basic,
)
@Composable
fun DemoScope.KAvatarDemo() {
    val name = textControl("Name", "Ada Lovelace")
    val size = choiceControl("Size", KAvatarSize.entries, initial = KAvatarSize.Medium)
    val status = choiceControl("Status", listOf<KAvatarStatus?>(null) + KAvatarStatus.entries, label = { it?.name ?: "None" })
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KAvatar(name, size = size, status = status)
        KText("Names get stable colours")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Grace Hopper", "Alan Turing", "Linus Torvalds", "Margaret Hamilton", "Dennis Ritchie").forEach { KAvatar(it, size = size) }
        }
    }
}
