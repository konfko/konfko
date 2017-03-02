@file:JvmName("SimpleSettingsSample")

package com.github.konfko.core.sample

import com.github.konfko.core.derived.subSettings
import com.github.konfko.core.getIfPresent
import com.github.konfko.core.source.SettingsMaker

/**
 * @author markopi
 */

fun main(args: Array<String>) {
    val settings = SettingsMaker().make {
        classpath("com/github/konfko/core/dataSources.properties")
    }

    // read a single setting
    val firstUrl: String = settings["dataSource.first.url"]

    // partial views
    val firstDataSource = settings.subSettings("dataSource.first")
    val secondDataSource = settings.subSettings("dataSource").subSettings("second")
    val secondUrl: String = secondDataSource["url"]

    println("First url: $firstUrl, second url: $secondUrl")

    // optional Int settings
    val firstConnectionTimeout: Int? = firstDataSource.getIfPresent("connectionTimeout")
    val secondConnectionTimeout: Int? = secondDataSource.getIfPresent("connectionTimeout")
    println("First timeout: $firstConnectionTimeout, second timeout: $secondConnectionTimeout")

    println("All data sources: ${settings.subSettings("dataSource").topLevelKeys}")
}
