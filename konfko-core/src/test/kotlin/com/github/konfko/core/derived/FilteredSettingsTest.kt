package com.github.konfko.core.derived

import com.github.konfko.core.structured.StructuredSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author markopi
 */
class FilteredSettingsTest {
    @Test
    fun filterTopLevelKeys() {
        val filtered = settings.filterTopLevelKeys { it.endsWith("st") }

        assertThat(filtered.toNestedMap()).isEqualTo(mapOf(
                "first" to mapOf(
                        "url" to "jdbc:test:localhost/first",
                        "username" to "firstUser",
                        "password" to "firstPassword"
                )))

        assertThat(filtered.toFlatMap()).isEqualTo(mapOf(
                "first.url" to "jdbc:test:localhost/first",
                "first.username" to "firstUser",
                "first.password" to "firstPassword"
        ))
    }

    @Test
    fun filterFlatKeys() {
        val filtered = settings.filterFlatKeys { it.contains(".user") }

        assertThat(filtered.toFlatMap()).isEqualTo(mapOf(
                "first.username" to "firstUser",
                "second.username" to "secondUser"
        ))
        assertThat(filtered.toNestedMap()).isEqualTo(mapOf(
                "first" to mapOf(
                        "username" to "firstUser"),
                "second" to mapOf(
                        "username" to "secondUser")
        ))
    }


    companion object {
        private val data = mapOf(
                "first.url" to "jdbc:test:localhost/first",
                "first.username" to "firstUser",
                "first.password" to "firstPassword",
                "second.url" to "jdbc:test:localhost/second",
                "second.username" to "secondUser",
                "second.password" to "secondPassword",
                "second.connectionTimeout" to "20"
        )
        private val settings = StructuredSettings(data)
    }

}