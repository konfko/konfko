package com.github.konfko.core

import com.github.konfko.core.structured.*

/**
 * @author markopi
 */

/**
 *  Provides a view over a smaller part of existing settings. Only settings with a given prefix will be visible,
 *  with the prefix removed.
 *
 * @sample subSettingsSample
 */
class SubSettings internal constructor(override val parent: Settings, prefix: String) : DerivedSettings() {
    private val prefix = prefix.removeSuffix(".")
    private val subValue: Any? = findNested(parent.toNestedMap(), this.prefix)

    override val empty: Boolean get() = subValue == null
    val absolutePrefix: String by lazy {
        parentChain
                .filterIsInstance<SubSettings>()
                .plus(this)
                .map { it.prefix }
                .joinToString(".")
    }

    override fun contains(key: String): Boolean = findNested(subValue, key) != null

    override fun <T : Any> getTyped(key: String, type: Class<T>): T =
            getTypedIfPresent(key, type) ?: throw NoSuchSettingException(absolutePrefix.resolveSettingKey(key))

    override fun <T : Any> getTypedIfPresent(key: String, type: Class<T>): T? =
            parent.getTypedIfPresent(prefix.resolveSettingKey(key), type)

    override fun toFlatMap() = nestedToFlat(subValue)

    override fun toNestedMap(): Map<String, Any> = mapifyNested(subValue)

    override fun <T : Any> atTyped(key: String, type: Class<T>): Setting<T> {
        return parent.atTyped(prefix.resolveSettingKey(key), type)
    }
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

infix fun Settings.subSettings(prefix: String) = SubSettings(this, prefix)
infix operator fun Settings.div(prefix: String) = subSettings(prefix)

