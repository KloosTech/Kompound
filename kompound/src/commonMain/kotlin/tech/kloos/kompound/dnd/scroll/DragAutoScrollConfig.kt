/*
 * Copyright 2023, Mohamed Ben Rejeb and the Compose Dnd project contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tech.kloos.kompound.dnd.scroll

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified

/**
 * Configuration for auto-scroll behavior during drag.
 *
 * @param minScrollThreshold Minimum distance from the edge of the scrollable container
 *   within which auto-scroll activates. The actual threshold is the larger of this value
 *   and the size of the first/last visible item (when available), capped by [maxScrollThreshold].
 * @param maxScrollThreshold Maximum distance from the edge for auto-scroll activation.
 *   Prevents the threshold from becoming too large for tall items or containers.
 *   The effective threshold is: `clamp(itemSize, minScrollThreshold, maxScrollThreshold)`.
 * @param maxScrollSpeed Distance scrolled per second when the dragged item is at the very
 *   edge. The speed eases in quadratically from zero at the threshold.
 */
@Immutable
public class KDragAutoScrollConfig(
    public val minScrollThreshold: Dp = DefaultMinScrollThreshold,
    public val maxScrollThreshold: Dp = DefaultMaxScrollThreshold,
    public val maxScrollSpeed: Dp = DefaultMaxScrollSpeed,
) {
    /** Speed in px per second, set only by the deprecated px-based factory. */
    internal var maxScrollSpeedPxOverride: Float? = null

    internal fun maxScrollSpeedPx(density: Density): Float =
        maxScrollSpeedPxOverride ?: with(density) { maxScrollSpeed.toPx() }

    public fun copy(
        minScrollThreshold: Dp = this.minScrollThreshold,
        maxScrollThreshold: Dp = this.maxScrollThreshold,
        maxScrollSpeed: Dp = this.maxScrollSpeed,
    ): KDragAutoScrollConfig =
        KDragAutoScrollConfig(
            minScrollThreshold = minScrollThreshold,
            maxScrollThreshold = maxScrollThreshold,
            maxScrollSpeed = maxScrollSpeed,
        ).also {
            // Keep a px speed from the deprecated factory unless a Dp speed replaces it
            if (maxScrollSpeed.isUnspecified) it.maxScrollSpeedPxOverride = maxScrollSpeedPxOverride
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KDragAutoScrollConfig) return false

        return minScrollThreshold == other.minScrollThreshold &&
            maxScrollThreshold == other.maxScrollThreshold &&
            maxScrollSpeed == other.maxScrollSpeed &&
            maxScrollSpeedPxOverride == other.maxScrollSpeedPxOverride
    }

    override fun hashCode(): Int {
        var result = minScrollThreshold.hashCode()
        result = 31 * result + maxScrollThreshold.hashCode()
        result = 31 * result + maxScrollSpeed.hashCode()
        result = 31 * result + maxScrollSpeedPxOverride.hashCode()
        return result
    }

    override fun toString(): String =
        "KDragAutoScrollConfig(minScrollThreshold=$minScrollThreshold, " +
            "maxScrollThreshold=$maxScrollThreshold, maxScrollSpeed=$maxScrollSpeed)"

    public companion object {
        /** Default [minScrollThreshold]. */
        public val DefaultMinScrollThreshold: Dp = 48.dp

        /** Default [maxScrollThreshold]. */
        public val DefaultMaxScrollThreshold: Dp = 160.dp

        /** Default [maxScrollSpeed]: distance scrolled per second at the very edge. */
        public val DefaultMaxScrollSpeed: Dp = 600.dp
    }
}
