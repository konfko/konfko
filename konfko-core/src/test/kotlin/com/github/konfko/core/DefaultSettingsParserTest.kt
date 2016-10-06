package com.github.konfko.core

import com.github.konfko.core.source.ClassPathResource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Test
import java.io.IOException

/**1
 * @author markopi
 */
class DefaultSettingsParserTest {
    @Test
    fun parseProperties() {
        val settings = DefaultSettingsParser.parse(ClassPathResource("com/github/konfko/core/dataSources.properties"))

        assertThat(settings.get<String>("dataSource.first.password")).isEqualTo("firstPassword")
        assertThat(settings.get<Int>("dataSource.second.connectionTimeout")).isEqualTo(20)
    }

    @Test
    fun failsOnUnknownExtension() {
        try {
            DefaultSettingsParser.parse(ClassPathResource("com/github/konfko/core/dataSources.UNKNOWN"))
            fail("Should throw exception on unknown extension")
        } catch(e: SettingsException) {
            assertThat(e.message).contains("extension").contains("UNKNOWN")
        }
    }

    @Test
    fun failsOnMissingResource() {
        try {
            DefaultSettingsParser.parse(ClassPathResource("com/github/konfko/core/dataSources-MISSING.properties"))
            fail("Should throw exception on missing resource")
        } catch(e: IOException) {
            assertThat(e.message).contains("MISSING")
        }
    }
}
