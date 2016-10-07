package com.github.konfko.core.derived

import com.github.konfko.core.structured.StructuredSettings
import org.assertj.core.api.Assertions
import org.junit.Test

/**
 * @author markopi
 */
class DerivedSettingsCombinationTest {

    @Test
    fun prefixSubSettings() {
        val s = settings.subSettings("dataSource.first").prefixBy("dataSource.third")

        Assertions.assertThat(s.toFlatMap()).isEqualTo(mapOf(
                "dataSource.third.url" to "jdbc:test:localhost/first",
                "dataSource.third.username" to "firstUser",
                "dataSource.third.password" to "firstPassword"
        ))
    }

    @Test
    fun subFilteredSettings() {
        val s = settings.filterFlatKeys { it.endsWith(".url") }.subSettings("dataSource.first")

        Assertions.assertThat(s.toFlatMap()).isEqualTo(mapOf(
                "url" to "jdbc:test:localhost/first"
        ))
    }

    @Test
    fun prefixFilterSubSettings() {
        val s = settings.subSettings("dataSource").filterTopLevelKeys { it.equals("first") }.prefixBy("system.dataSource")

        Assertions.assertThat(s.toFlatMap()).isEqualTo(mapOf(
                "system.dataSource.first.url" to "jdbc:test:localhost/first",
                "system.dataSource.first.username" to "firstUser",
                "system.dataSource.first.password" to "firstPassword"
        ))
    }

    @Test
    fun prefixPrefixFilteredSettings() {
        val s = settings.filterFlatKeys { it.contains(".second.") }.prefixBy("external").prefixBy("system")

        Assertions.assertThat(s.toFlatMap()).isEqualTo(mapOf(
                "system.external.dataSource.second.url" to "jdbc:test:localhost/second",
                "system.external.dataSource.second.username" to "secondUser",
                "system.external.dataSource.second.password" to "secondPassword",
                "system.external.dataSource.second.connectionTimeout" to "20"
        ))
    }

    companion object {
        private val data = mapOf(
                "dataSource.first.url" to "jdbc:test:localhost/first",
                "dataSource.first.username" to "firstUser",
                "dataSource.first.password" to "firstPassword",
                "dataSource.second.url" to "jdbc:test:localhost/second",
                "dataSource.second.username" to "secondUser",
                "dataSource.second.password" to "secondPassword",
                "dataSource.second.connectionTimeout" to "20"
        )
        private val settings = StructuredSettings(data)
    }
}