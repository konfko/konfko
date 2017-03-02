package com.github.konfko.core.derived

import com.github.konfko.core.NoSuchSettingException
import com.github.konfko.core.structured.StructuredSettings
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author markopi
 */
class SubSettingsTest {
    @Test
    fun getRequired() {
        val sub = settings.subSettings("dataSource.first")

        assertThat(sub.get<String>("url")).isEqualTo("jdbc:test:localhost/first")
        assertThat(sub.get<String>("username")).isEqualTo("firstUser")
        assertThat(sub.get<String>("password")).isEqualTo("firstPassword")


        try {
            sub.get<String>("something")
            Assertions.fail("Property should not exist")
        } catch(e: NoSuchSettingException) {
            assertThat(e).hasMessageContaining("dataSource.first.something")
        }
    }

    @Test
    fun contains() {
        val sub = settings.subSettings("dataSource.first")

        assertThat(sub.contains("url")).isTrue()
        assertThat(sub.contains("connectionTimeout")).isFalse()
    }

    @Test
    fun toFlatMap() {
        val sub = settings.subSettings("dataSource.second")

        assertThat(sub.toFlatMap()).isEqualTo(mapOf(
                "url" to "jdbc:test:localhost/second",
                "username" to "secondUser",
                "password" to "secondPassword",
                "connectionTimeout" to "20"
        ))
    }

    @Test
    fun presentSingleValueSubSetting() {
        val sub = settings.subSettings("dataSource.second.connectionTimeout") as SubSettings
        assertThat(sub.makeAbsoluteKey("")).isEqualTo("dataSource.second.connectionTimeout")
        assertThat(sub.get<Int>("")).isEqualTo(20)

        assertThat(sub.toFlatMap()).isEqualTo(mapOf(
                "" to "20"
        ))
        assertThat(sub.empty).isFalse()
    }

    @Test
    fun absentSingleValueSubSetting() {
        val sub = settings.subSettings("dataSource.second.something") as SubSettings
        assertThat(sub.makeAbsoluteKey("")).isEqualTo("dataSource.second.something")

        assertThat(sub.toFlatMap()).isEqualTo(mapOf<String, Any>())
        assertThat(sub.find<Int>("")).isNull()
        assertThat(sub.empty).isTrue()
        try {
            sub.get<String>("")
            Assertions.fail("Property should not exist")
        } catch (e: NoSuchSettingException) {
            assertThat(e).hasMessageContaining("dataSource.second.something")
        }
    }

    @Test
    fun multilevel() {
        val dataSource = settings.subSettings("dataSource") as SubSettings
        assertThat(dataSource.makeAbsoluteKey("first.url")).isEqualTo("dataSource.first.url")

        val first = dataSource.subSettings("first") as SubSettings
        assertThat(first.makeAbsoluteKey("url")).isEqualTo("dataSource.first.url")
        assertThat(first.contains("username")).isTrue()
        assertThat(first.get<String>("password")).isEqualTo("firstPassword")
        assertThat(first.toFlatMap()).isEqualTo(mapOf(
                "url" to "jdbc:test:localhost/first",
                "username" to "firstUser",
                "password" to "firstPassword"
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