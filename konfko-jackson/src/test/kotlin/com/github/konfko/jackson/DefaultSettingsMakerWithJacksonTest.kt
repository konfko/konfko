package com.github.konfko.jackson

import com.github.konfko.core.source.SettingsMaker
import org.junit.Test

/**
 * @author markopi
 */
class DefaultSettingsMakerWithJacksonTest {
    @Test
    fun convertDataObject() {
        val settings = SettingsMaker().make {
            classpath("com/github/konfko/jackson/dataSources.yaml")
        }

        val value: DataSourceConfig = settings["dataSource.first"]
        println("value = ${value}")
    }

    data class DataSourceConfig(val username: String, val password: String = "password", val connectionTimeout: Int) {
        lateinit var url: String
        var maxSize: Int = 10

    }

}