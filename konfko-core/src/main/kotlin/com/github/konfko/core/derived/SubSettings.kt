package com.github.konfko.core.derived

import com.github.konfko.core.Settings
import com.github.konfko.core.structured.StructuredSettings
import com.github.konfko.core.structured.findNested
import com.github.konfko.core.structured.resolveSettingKey

/**
 * @author markopi
 */

/**
 *  Provides a view over a smaller part of existing settings. Only settings with a given prefix will be visible,
 *  with the prefix removed.
 *
 * @sample subSettingsSample
 */
internal class SubSettings (override val parent: Settings, prefix: String) : DerivedValueSettings<Any?>() {
    private val prefix = prefix.removeSuffix(".")
    override val derivedValue: Any? = findNested(parent.toNestedMap(), this.prefix)

    override fun makeParentKey(key: String): String = prefix.resolveSettingKey(key)
}

private fun subSettingsSample() {
    val root = StructuredSettings(mapOf(
            "dataSource.first.url" to "jdbc:test:localhost/first",
            "dataSource.first.username" to "firstUser",
            "dataSource.first.password" to "firstPassword",
            "dataSource.second.url" to "jdbc:test:localhost/second",
            "dataSource.second.username" to "secondUser",
            "dataSource.second.password" to "secondPassword",
            "dataSource.second.connectionTimeout" to "20"
    ))

    assert(root.subSettings("dataSource.first").toFlatMap() == mapOf(
            "url" to "jdbc:test:localhost/first",
            "username" to "firstUser",
            "password" to "firstPassword"))
    // must exactly match segment prefix
    assert((root.subSettings("dataSource.fir").toFlatMap() == emptyMap<String, Any>()))
    // can be nested
    assert(root.subSettings("dataSource.first") == root.subSettings("dataSource").subSettings("first"))
    // subSettings can also be expressed as "/"
    assert(root / "dataSource.first" == root / "dataSource" / "first")
}

infix fun Settings.subSettings(prefix: String): Settings = SubSettings(this, prefix)
infix operator fun Settings.div(prefix: String): Settings = SubSettings(this, prefix)

