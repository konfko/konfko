package com.github.konfko.jackson

import com.github.konfko.core.DefaultSettingsParser
import com.github.konfko.core.derived.subSettings
import com.github.konfko.core.source.ClassPathResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author markopi
 */
class DefaultSettingsParserTest {
    @Test
    fun jsonModuleFoundWithServiceFactory() {
        assertThat(DefaultSettingsParser.supportedExtensions).contains("json", "yaml", "yml")
    }

    @Test
    fun parseYaml() {
        val settings = DefaultSettingsParser.parse(ClassPathResource("com/github/konfko/jackson/dataSources.yaml"))

        assertThat(settings.get<String>("dataSource.first.username")).isEqualTo("firstUser")

        assertThat(settings.subSettings("dataSource").subSettings("second").get<String>("password")).isEqualTo("secondPassword")
    }

}