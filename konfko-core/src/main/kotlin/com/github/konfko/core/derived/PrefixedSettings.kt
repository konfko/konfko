package com.github.konfko.core.derived

import com.github.konfko.core.Settings
import com.github.konfko.core.structured.SettingKey

/**
 * @author markopi
 */
class PrefixedSettings(override val parent: Settings, prefix: String) : DerivedValueSettings<Any?>() {
    private val prefix = prefix.removeSuffix(".")

    override fun makeParentKey(key: String): String {
        if (prefix == "") return key
        check(key.startsWith(prefix)) { "Tried to retrieve setting with key [$key] which does not start with prefix [$prefix]" }
        if (key==prefix) return ""
        return key.substring(prefix.length+1)
    }

    override val derivedValue: Any? = prependPrefixed(this.prefix, parent.toNestedMap())

    private fun prependPrefixed(prefix: String, value: Map<String, Any>): Any? {
        if (prefix == "") return value

        val key = SettingKey.parse(prefix)
        val result: Map<String, Any> = key.segments.foldRight(value, { segment, map ->
            mutableMapOf<String, Any>().apply {
                set(segment.property, map)
            }
        })
        return result
    }
}

fun Settings.prefixBy(prefix: String ): DerivedSettings = PrefixedSettings(this, prefix)
