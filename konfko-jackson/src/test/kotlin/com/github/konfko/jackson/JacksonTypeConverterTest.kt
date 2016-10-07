package com.github.konfko.jackson

import com.github.konfko.core.source.StructuredSettingsSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author markopi
 */
class JacksonTypeConverterTest {
    @Test
    fun `convert without specifying default values`() {
        val data = mapOf(
                "url" to "jdbc:test:localhost/first",
                "username" to "firstUser",
                "password" to "firstPassword")

        val conf = DefaultJacksonTypeConverter.convertTo(data, DataSourceConf::class.java)

        assertThat(conf).isEqualTo(DataSourceConf(
                url = "jdbc:test:localhost/first",
                username = "firstUser",
                password = "firstPassword",
                connectionTimeout = 120))
    }

    @Test
    fun `convert with extraneous properties`() {
        val data = mapOf(
                "url" to "jdbc:test:localhost/first",
                "username" to "firstUser",
                "password" to "firstPassword",
                "extraneous" to "unknown")

        val conf = DefaultJacksonTypeConverter.convertTo(data, DataSourceConf::class.java)

        assertThat(conf).isEqualTo(DataSourceConf(
                url = "jdbc:test:localhost/first",
                username = "firstUser",
                password = "firstPassword",
                connectionTimeout = 120))
    }

    @Test
    fun `convert string to int`() {
        val data = mapOf(
                "url" to "jdbc:test:localhost/first",
                "username" to "firstUser",
                "password" to "firstPassword",
                "connectionTimeout" to "30")

        val conf = DefaultJacksonTypeConverter.convertTo(data, DataSourceConf::class.java)

        assertThat(conf).isEqualTo(DataSourceConf(
                url = "jdbc:test:localhost/first",
                username = "firstUser",
                password = "firstPassword",
                connectionTimeout = 30))
    }

    @Test
    fun `convert inner map`() {
        val data = mapOf(
                "dataSource.first.url" to "jdbc:test:localhost/first",
                "dataSource.first.username" to "firstUser",
                "dataSource.first.password" to "firstPassword",
                "dataSource.second.url" to "jdbc:test:localhost/second",
                "dataSource.second.username" to "secondUser",
                "dataSource.second.password" to "secondPassword",
                "dataSource.second.connectionTimeout" to "20"
        )
        val nestedData = StructuredSettingsSource("test", data).load().toNestedMap()

        val conf = DefaultJacksonTypeConverter.convertTo(nestedData, DataSourcesConf::class.java)

        assertThat(conf).isEqualTo(DataSourcesConf(
                dataSource = mapOf(
                        "first" to DataSourceConf(
                                url = "jdbc:test:localhost/first",
                                username = "firstUser",
                                password = "firstPassword",
                                connectionTimeout = 120),
                        "second" to DataSourceConf(
                                url = "jdbc:test:localhost/second",
                                username = "secondUser",
                                password = "secondPassword",
                                connectionTimeout = 20))))
    }


    data class DataSourceConf(val url: String, val username: String, val password: String, val connectionTimeout: Int = 120)
    data class DataSourcesConf(val dataSource: Map<String, DataSourceConf>)


}