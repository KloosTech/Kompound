package tech.kloos.kompound.showcase.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.avatar.KAvatar
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.skeleton.KSkeleton
import tech.kloos.kompound.skeleton.KSkeletonCircle
import tech.kloos.kompound.skeleton.KSkeletonGroup
import tech.kloos.kompound.skeleton.KSkeletonText
import tech.kloos.kompound.text.KText

private const val Usage_skeleton = """import tech.kloos.kompound.skeleton.KSkeleton
import tech.kloos.kompound.skeleton.KSkeletonCircle
import tech.kloos.kompound.skeleton.KSkeletonGroup
import tech.kloos.kompound.skeleton.KSkeletonText

if (loading) {
    // Screen readers announce "Loading" once for the whole group.
    KSkeletonGroup {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KSkeletonCircle(40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KSkeleton(Modifier.fillMaxWidth(0.5f).height(16.dp))
                KSkeletonText(lines = 2)
            }
        }
    }
} else {
    Profile(user)
}

// Any content can shimmer: Modifier.background(grey).kShimmer(enabled = loading)"""

@KompoundDemo(
    id = "skeleton.loading",
    title = "KSkeleton",
    description = "Shimmering placeholders for content that is loading: blocks, circles and lines of text, announced once as Loading.",
    category = KompoundCategory.Feedback,
    tags = ["skeleton", "shimmer", "loading", "placeholder", "progress"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_skeleton,
)
@Composable
fun DemoScope.KSkeletonDemo() {
    val loading = boolControl("Loading", true)
    val animated = boolControl("Animated", true)
    val lines = floatControl("Text lines", 1f..6f, 3f).toInt()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (loading) {
            KSkeletonGroup(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    KSkeletonCircle(48.dp, animated = animated)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KSkeleton(Modifier.fillMaxWidth(0.4f).height(18.dp), animated = animated)
                        KSkeletonText(Modifier.fillMaxWidth(), lines = lines, animated = animated)
                        KSkeleton(Modifier.fillMaxWidth().height(120.dp), animated = animated)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KAvatar("Ada Lovelace")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KText("Ada Lovelace")
                    KText("Wrote the first published algorithm intended for a machine, in notes on Babbage's analytical engine.")
                }
            }
        }
    }
}
