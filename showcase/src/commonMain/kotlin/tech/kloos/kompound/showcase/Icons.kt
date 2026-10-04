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

    private fun icon(name: String, build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply { path(fill = SolidColor(Color.Black), pathBuilder = build) }.build()

    val Home: ImageVector by lazy { icon("home") { moveTo(12f, 3f); lineTo(2f, 12f); lineTo(5f, 12f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 12f); lineTo(22f, 12f); close() } }
    val Inbox: ImageVector by lazy { icon("inbox") { moveTo(19f, 3f); lineTo(5f, 3f); lineTo(2f, 12f); lineTo(2f, 21f); lineTo(22f, 21f); lineTo(22f, 12f); close(); moveTo(8f, 13f); lineTo(8f, 12f); lineTo(16f, 12f); lineTo(16f, 13f); lineTo(19f, 15f); lineTo(5f, 15f); close() } }
    val Settings: ImageVector by lazy { icon("settings") { moveTo(12f, 2f); lineTo(14.5f, 5f); lineTo(18.5f, 4.5f); lineTo(19f, 8.5f); lineTo(22f, 11f); lineTo(20f, 14.5f); lineTo(21f, 18.5f); lineTo(17f, 19f); lineTo(14.5f, 22f); lineTo(11f, 20f); lineTo(7f, 21f); lineTo(6.5f, 17f); lineTo(3f, 14.5f); lineTo(5f, 11f); lineTo(4f, 7f); lineTo(8f, 6.5f); close() } }
    val Menu: ImageVector by lazy { icon("menu") { moveTo(3f, 6f); lineTo(21f, 6f); lineTo(21f, 8f); lineTo(3f, 8f); close(); moveTo(3f, 11f); lineTo(21f, 11f); lineTo(21f, 13f); lineTo(3f, 13f); close(); moveTo(3f, 16f); lineTo(21f, 16f); lineTo(21f, 18f); lineTo(3f, 18f); close() } }
    val Person: ImageVector by lazy { icon("person") { moveTo(12f, 4f); lineTo(15f, 7f); lineTo(15f, 10f); lineTo(12f, 13f); lineTo(9f, 10f); lineTo(9f, 7f); close(); moveTo(4f, 20f); lineTo(5f, 16f); lineTo(12f, 14f); lineTo(19f, 16f); lineTo(20f, 20f); close() } }
}
