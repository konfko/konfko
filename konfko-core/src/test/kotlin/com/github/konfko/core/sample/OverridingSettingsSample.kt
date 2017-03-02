@file:JvmName("OverridingSettingsSample")

package com.github.konfko.core.sample

import com.github.konfko.core.derived.subSettings
import com.github.konfko.core.source.SettingsMaker

/**
 * @author markopi
 */
fun main(args: Array<String>) {
    overrideWithSystemProperties()
    overrideWithSystemPropertiesUnderPrefix()
}


private fun overrideWithSystemProperties() {
    // override settings in dataSources.properties with any system property
    System.setProperty("dataSource.first.username", "overrideUsername")
    val settings = SettingsMaker().make {
        systemProperties()
        classpath("com/github/konfko/core/dataSources.properties")
    }

    val firstUsername: String = settings["dataSource.first.username"]
    val secondUsername: String = settings["dataSource.second.username"]

    println("First username: $firstUsername, Second username: $secondUsername")

    System.clearProperty("dataSource.first.username")
}

private fun overrideWithSystemPropertiesUnderPrefix() {
    // require a namespace for system properties overrides
    System.setProperty("dataSource.first.username", "ignoredUsername")
    System.setProperty("konfko.dataSource.first.password", "overridePassword")
    val settings = SettingsMaker().make {
        systemProperties().transform { it.subSettings("konfko") }
        classpath("com/github/konfko/core/dataSources.properties")
    }

    val firstUsername: String = settings["dataSource.first.username"]
    val firstPassword: String = settings["dataSource.first.password"]
    println("First username: $firstUsername, First password: $firstPassword")

    System.clearProperty("konfko.dataSource.first.password")
}
