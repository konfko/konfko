package com.github.konfko.core

import java.util.*

/**
 * @author markopi
 */

/**
 * A collection of settings.
 * It provides a nested view of setting properties. In keys, levels are specified with '.'
 *
 * Implementations of this interface are always immutable, event for reloadable settings.
 * You can however get a potentially reloadable view of a particular setting with [atTyped], or more
 * comfortably with the extension function [at]
 *
 * To get an reloaded version of Settings, you need to call [ReloadableSettings.snapshot]
 */
interface Settings {
    /**
     * returns true if the settings are empty. If the settings contain only one scalar value, it can be retrieved
     * with key "" (empty string) and the Settings are not considered empty
     */
    val empty: Boolean
    /**
     * A set of all top level keys. Order of keys is the same as it was defined in the sources
     */
    val topLevelKeys: Set<String>

    /**
     * Returns true if the settings contain this key. The key can be either a final property or an intermediate node
     */
    fun contains(key: String): Boolean

    /**
     * Get the setting value at [key] and converts it to [type]. If there is no such key, it returns null.
     *
     * This function has a slightly weird name to avoid clashing with the wrapping extension function [getIfPresent],
     * which is the one you should normally use.
     *
     *  @throws SettingTypeConversionException if the value could not be converted to this type
     */
    fun <T : Any> getTypedIfPresent(key: String, type: Class<T>): T?

    /**
     * Get the setting value at [key] and converts it to [type].
     *
     * This function has a slightly weird name to avoid clashing with the wrapping extension function [get],
     * which is the one you should normally use.
     *
     *  @throws NoSuchSettingException if there is no setting under this key
     *  @throws SettingTypeConversionException if the value could not be converted to type [T]
     */
    fun <T : Any> getTyped(key: String, type: Class<T>): T

    /**
     * Returns a flattened map representation of these settings. Nested levels are separated by '.'
     */
    fun toFlatMap(): Map<String, Any>

    /**
     * Returns a nested map representation of these settings. Each level is a separate map.
     */
    fun toNestedMap(): Map<String, Any>

    /**
     * Get the [Setting] handler of type [T] at [key]. This always returns successfully, even if the value does not
     * exist or cannot be converted to the proper type. Any such errors are delayed until first invocation of
     * the relevant Setting property.
     *
     * This function has a slightly weird name to avoid clashing with the wrapping extension function [at],
     * which is the one you should normally use.
     */
    fun <T : Any> atTyped(key: String, type: Class<T>): Setting<T>
}

/**
 * Get the setting value at [key] and converts it to type [T].
 *
 * This function is a type reified wrapper around [Settings.getTyped]
 *
 *  @throws NoSuchSettingException if there is no setting under this key
 *  @throws SettingTypeConversionException if the value could not be converted to type [T]
 */
inline operator fun <reified T : Any> Settings.get(key: String): T = getTyped(key, T::class.java)

/**
 * Get the setting value at [key] and converts it to type [T]. If there is no such key, it returns null.
 *
 * This function is a type reified wrapper around [Settings.getTypedIfPresent]
 *
 *  @throws SettingTypeConversionException if the value could not be converted to this type
 */
inline fun <reified T : Any> Settings.getIfPresent(key: String): T? = getTypedIfPresent(key, T::class.java)

/**
 * Get the [Setting] handler of type [T] at [key]. This always returns successfully, even if the value does not
 * exist or cannot be converted to the proper type. Any such errors are delayed until first invocation of
 * the relevant Setting property.
 *
 * If the underlying settings are reloadable, this provides a mutable view of the most recent value that was
 * successfully converted to type [T]
 *
 * This function is a type reified wrapper around [Settings.atTyped]
 */
inline infix fun <reified T : Any> Settings.at(key: String): Setting<T> = atTyped(key, T::class.java)

abstract class AbstractSettings : Settings {
    override val topLevelKeys: Set<String> get() = toNestedMap().keys
    override fun <T : Any> getTyped(key: String, type: Class<T>): T = getTypedIfPresent(key, type) ?: throw NoSuchSettingException(key.toString())

    override fun hashCode(): Int {
        return toFlatMap().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return (other is Settings && toFlatMap() == other.toFlatMap())
    }
}

/**
 * An abstract class for settings that have been derived from other settings.
 */
abstract class DerivedSettings : AbstractSettings() {
    /**
     * Root settings in the derived chain
     */
    val root: Settings get() = parentChain.first()

    /**
     * Chain of all parents. First element is the root, last element is the immediate parent of this settings
     */
    protected val parentChain: List<Settings> by lazy {
        var p: Settings = parent
        val parents = LinkedList<Settings>()
        parents.addFirst(p)
        while (p is DerivedSettings) {
            p = p.parent
            parents.addFirst(p)
        }
        parents.toList()
    }

    /**
     * Settings from which this settings was derived from
     */
    abstract val parent: Settings
}

private object EmptySettings : AbstractSettings() {
    override val empty = true

    override fun contains(key: String): Boolean = false

    override fun <T : Any> getTypedIfPresent(key: String, type: Class<T>): T? = null

    override fun toFlatMap(): Map<String, Any> = emptyMap()

    override fun toNestedMap(): Map<String, Any> = emptyMap()

    override fun <T : Any> atTyped(key: String, type: Class<T>): Setting<T> = ConstantSetting(null)
}


/**
 * General exception when handling settings. Root of Settings exception hierarchy
 */
open class SettingsException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Tried to retrieve a setting that does not exist. The (absolute) key used is retrievable via [key] property
 */
open class NoSuchSettingException(val key: String) : SettingsException("Required setting with key [$key] not present")


/**
 * Returns empty settings
 */
fun emptySettings(): Settings = EmptySettings