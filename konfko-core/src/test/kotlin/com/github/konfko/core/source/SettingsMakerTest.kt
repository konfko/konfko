package com.github.konfko.core.source

import com.github.konfko.core.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author markopi
 */
class SettingsMakerTest {
    @Test
    fun loadBasic() {
        val settings = SettingsMaker().make {
            classpath("com/github/konfko/core/dataSources.properties")
        }

        assertThat(settings.get<String>("dataSource.first.username")).isEqualTo("firstUser")
    }

    @Test
    fun loadOverride() {
        val settings = SettingsMaker().make {
            classpath("com/github/konfko/core/dataSources.properties")
            map("defaults", mapOf("dataSource.first.connectionTimeout" to 120))
        }

        assertThat(settings.get<String>("dataSource.first.username")).isEqualTo("firstUser")
        assertThat(settings.get<String>("dataSource.first.connectionTimeout")).isEqualTo("120")
    }
}