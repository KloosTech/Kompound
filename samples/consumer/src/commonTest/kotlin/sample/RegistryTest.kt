package sample

import tech.kloos.kompound.registry.KompoundRegistry_kompound_consumer_sample
import kotlin.test.Test
import kotlin.test.assertEquals

class RegistryTest {
    @Test
    fun consumerDemoIsDiscovered() {
        assertEquals(listOf("my.button"), KompoundRegistry_kompound_consumer_sample.entries.map { it.meta.id })
    }
}
