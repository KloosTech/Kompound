package tech.kloos.kompound.graph

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Which part of the infinite canvas is visible. World coordinates are what nodes are positioned in; screen
 * coordinates are pixels inside the canvas. `screen = world * zoom + offset`.
 *
 * @param offset Screen position of the world origin.
 * @param zoom Scale, between [minZoom] and [maxZoom].
 */
@Stable
public class KViewportState(
    offset: Offset = Offset.Zero,
    zoom: Float = 1f,
    public val minZoom: Float = 0.1f,
    public val maxZoom: Float = 4f,
) {
    /** Screen position of the world origin. */
    public var offset: Offset by mutableStateOf(offset)
        private set

    /** Current scale. */
    public var zoom: Float by mutableFloatStateOf(zoom.coerceIn(minZoom, maxZoom))
        private set

    /** Converts a screen point to world coordinates. */
    public fun screenToWorld(point: Offset): Offset = (point - offset) / zoom

    /** Converts a world point to screen coordinates. */
    public fun worldToScreen(point: Offset): Offset = point * zoom + offset

    /** Moves the canvas by [delta] screen pixels. */
    public fun panBy(delta: Offset) {
        offset += delta
    }

    /** Multiplies the zoom by [factor] (clamped) keeping the world point under [focus] (screen coordinates) where it is. */
    public fun zoomBy(factor: Float, focus: Offset) {
        val target = (zoom * factor).coerceIn(minZoom, maxZoom)
        val applied = target / zoom
        if (applied == 1f) return
        offset = focus - (focus - offset) * applied
        zoom = target
    }

    /** Pans and zooms so that [world] fills a canvas of [canvas] pixels with [padding] pixels to spare; zoom is not raised above 1. */
    public fun fit(world: Rect, canvas: Size, padding: Float = 48f) {
        if (world.width <= 0f && world.height <= 0f || canvas.width <= 0f || canvas.height <= 0f) return
        val availableW = (canvas.width - 2 * padding).coerceAtLeast(1f)
        val availableH = (canvas.height - 2 * padding).coerceAtLeast(1f)
        val z = minOf(availableW / world.width.coerceAtLeast(1f), availableH / world.height.coerceAtLeast(1f), 1f).coerceIn(minZoom, maxZoom)
        zoom = z
        offset = Offset(canvas.width / 2f, canvas.height / 2f) - world.center * z
    }

    /** Pans so that the world point [world] is in the middle of a canvas of [canvas] pixels, keeping the zoom. */
    public fun centerOn(world: Offset, canvas: Size) {
        offset = Offset(canvas.width / 2f, canvas.height / 2f) - world * zoom
    }

    /** The part of the world that is visible in a canvas of [canvas] pixels. */
    public fun visibleWorld(canvas: Size): Rect = Rect(screenToWorld(Offset.Zero), screenToWorld(Offset(canvas.width, canvas.height)))

    /** Sets the viewport directly. */
    public fun set(offset: Offset, zoom: Float) {
        this.offset = offset
        this.zoom = zoom.coerceIn(minZoom, maxZoom)
    }

    public companion object {
        /** Saves offset and zoom across configuration changes. */
        public val Saver: Saver<KViewportState, Any> = listSaver(
            save = { listOf(it.offset.x, it.offset.y, it.zoom, it.minZoom, it.maxZoom) },
            restore = { KViewportState(Offset(it[0], it[1]), it[2], it[3], it[4]) },
        )
    }
}
