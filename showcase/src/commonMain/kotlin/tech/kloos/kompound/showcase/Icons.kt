package tech.kloos.kompound.showcase

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small vector icons used by the demos (Kompound does not depend on material-icons). */
internal object DemoIcons {
    val Star: ImageVector by lazy {
        ImageVector.Builder("star", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f); lineTo(15.1f, 8.6f); lineTo(22f, 9.3f); lineTo(16.8f, 14f)
                lineTo(18.2f, 21f); lineTo(12f, 17.5f); lineTo(5.8f, 21f); lineTo(7.2f, 14f)
                lineTo(2f, 9.3f); lineTo(8.9f, 8.6f); close()
            }
        }.build()
    }
    val Check: ImageVector by lazy {
        ImageVector.Builder("check", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 16.2f); lineTo(4.8f, 12f); lineTo(3.4f, 13.4f); lineTo(9f, 19f)
                lineTo(21f, 7f); lineTo(19.6f, 5.6f); close()
            }
        }.build()
    }
}
