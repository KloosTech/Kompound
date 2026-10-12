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
package tech.kloos.kompound.dnd

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.dnd.scroll.KDragAutoScrollConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class DragAutoScrollConfigTest {

    private val density = Density(density = 2f)

    @Test
    fun maxScrollSpeed_isConvertedWithDensity() {
        val config = KDragAutoScrollConfig(maxScrollSpeed = 300.dp)

        assertEquals(600f, config.maxScrollSpeedPx(density))
    }

    @Test
    fun equality_isStructural() {
        assertEquals(KDragAutoScrollConfig(), KDragAutoScrollConfig())
        assertEquals(KDragAutoScrollConfig().hashCode(), KDragAutoScrollConfig().hashCode())
    }
}
