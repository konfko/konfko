package com.github.konfko.core.watcher

import com.github.konfko.core.structured.StructuredSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*

/**
 * @author markopi
 */
class ReloadableSettingsTest {
    @Test
    fun findUpdatedSettingsKey() {
        val first = StructuredSettings(mapOf(
                "dataSource.first.url" to "jdbc:test:localhost/first",
                "dataSource.first.username" to "firstUser",
                "dataSource.first.password" to "firstPassword",
                "dataSource.first.maxSize" to "30",
                "dataSource.second.url" to "jdbc:test:localhost/second",
                "dataSource.second.username" to "secondUser",
                "dataSource.second.password" to "secondPassword"
        ))
        val second = StructuredSettings(mapOf(
                "dataSource.first.url" to "jdbc:test:localhost/first",
                "dataSource.first.username" to "firstUser_Updated",
                "dataSource.first.password" to "firstPassword",
                "dataSource.second.url" to "jdbc:test:localhost/second",
                "dataSource.second.username" to "secondUser",
                "dataSource.second.password" to "secondPassword_Updated",
                "dataSource.second.connectionTimeout" to "20"
        ))

        assertThat(findUpdatedSettingKeys(first, second)).containsOnly(
                "dataSource.first.username",
                "dataSource.first.maxSize",
                "dataSource.second.password",
                "dataSource.second.connectionTimeout"
        )
    }

    @Test
    fun shouldListenerWithPrefixBeNotified() {
        val updatedSettings = TreeSet(setOf(
                "dataSource.first.username",
                "dataSource.first.maxSize",
                "dataSource.second.password",
                "dataSource.second.connectionTimeout",
                "someOther.changedSetting"))

        assertThat(shouldBeNotified(null, updatedSettings)).isTrue()
        assertThat(shouldBeNotified("dataSource", updatedSettings)).isTrue()
        assertThat(shouldBeNotified("dataSourceX", updatedSettings)).isFalse()
        assertThat(shouldBeNotified("dataSource.first", updatedSettings)).isTrue()
        assertThat(shouldBeNotified("dataSource.second", updatedSettings)).isTrue()
        assertThat(shouldBeNotified("dataSource.third", updatedSettings)).isFalse()
        assertThat(shouldBeNotified("dataSource.first.user", updatedSettings)).isFalse()
        assertThat(shouldBeNotified("dataSource.first.username", updatedSettings)).isTrue()
        assertThat(shouldBeNotified("dataSource.third.empty", updatedSettings)).isFalse()
    }
}