package com.github.konfko.core.derived

import com.github.konfko.core.structured.StructuredSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author markopi
 */
class PrefixedSettingsTest {
    @Test
    fun prefixEmptyKey() {
        val prefixed = settings.prefixBy("")

        assertThat(prefixed.get<String>("first.url")).isEqualTo("jdbc:test:localhost/first")
        assertThat(prefixed.toFlatMap()).isEqualTo(data)
    }

    @Test
    fun prefixSingleLevel() {
        val prefixed = settings.prefixBy("dataSource")

        assertThat(prefixed.get<String>("dataSource.first.url")).isEqualTo("jdbc:test:localhost/first")
        assertThat(prefixed.toFlatMap()).isEqualTo(mapOf(
                "dataSource.first.url" to "jdbc:test:localhost/first",
                "dataSource.second.url" to "jdbc:test:localhost/second",
                "dataSource.second.connectionTimeout" to "20"
        ))

        assertThat(settings.find<String>("badDataSource.first.url")).isNull()
        assertThat(settings.find<String>("dataSource.missing.url")).isNull()
    }

    @Test
    fun prefixMoreLevels() {
        val prefixed = settings.prefixBy("system.external.dataSource")

        assertThat(prefixed.get<String>("system.external.dataSource.first.url")).isEqualTo("jdbc:test:localhost/first")
        assertThat(prefixed.toFlatMap()).isEqualTo(mapOf(
                "system.external.dataSource.first.url" to "jdbc:test:localhost/first",
                "system.external.dataSource.second.url" to "jdbc:test:localhost/second",
                "system.external.dataSource.second.connectionTimeout" to "20"
        ))

        assertThat(settings.find<String>("badPrefix.external.dataSource.first.url")).isNull()
    }

    companion object {
        private val data = mapOf(
                "first.url" to "jdbc:test:localhost/first",
                "second.url" to "jdbc:test:localhost/second",
                "second.connectionTimeout" to "20"
        )
        private val settings = StructuredSettings(data)
    }

}