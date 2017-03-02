package com.github.konfko.core.structured

import com.github.konfko.core.NoSuchSettingException
import com.github.konfko.core.getIfPresent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Test

/**
 * @author markopi
 */
class StructuredSettingsTest {


    @Test
    fun getRequired() {
        assertThat(settings.get<String>("dataSource.first.url")).isEqualTo("jdbc:test:localhost/first")
        assertThat(settings.get<String>("dataSource.second.username")).isEqualTo("secondUser")

        try {
            settings.get<String>("dataSource.second.something")
            fail("Property should not exist")
        } catch(e: NoSuchSettingException) {
            assertThat(e).hasMessageContaining("dataSource.second.something")
        }
    }

    @Test
    fun getAndConvert() {
        assertThat(settings.get<Int>("dataSource.second.connectionTimeout")).isEqualTo(20)
    }

    @Test
    fun contains() {
        assertThat(settings.contains("dataSource.first.url")).isTrue()
        assertThat(settings.contains("dataSource.first.namespace")).isFalse()
    }

    @Test
    fun toFlatMap() {
        assertThat(settings.toFlatMap()).isEqualTo(data)
    }

    @Test
    fun getIfPresent() {
        assertThat(settings.getIfPresent<String>("dataSource.first.password")).isEqualTo("firstPassword")
        assertThat(settings.getIfPresent<String>("dataSource.something.password")).isNull()
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
