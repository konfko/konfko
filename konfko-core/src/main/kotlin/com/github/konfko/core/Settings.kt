package com.github.konfko.core

/**
 * @author markopi
 */

/**
 * Base interface for a collection of settings. Normally you should refer to [Settings].
 * This is a separate interface in order to simplify settings composition.
 *
 */
interface SettingsBase {
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
     *  @throws SettingTypeConversionException if the value could not be converted to this type
     */
    fun <T : Any> find(key: String, type: Class<T>): T?


    /**
     * Get the setting value at [key] and converts it to [type].
     *
     *  @throws NoSuchSettingException if there is no setting under this key
     *  @throws SettingTypeConversionException if the value could not be converted to type [T]
     */
    fun <T : Any> get(key: String, type: Class<T>): T


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
     */
    fun <T : Any> bind(key: String, type: Class<T>): Setting<T>

}

/**
 * A collection of settings.
 * It provides a nested view of setting settings. In keys, levels are specified with '.'
 *
 * Implementations of this interface are always immutable, event for reloadable settings.
 * You can however get a potentially reloadable view of a particular setting with [bind].
 *
 * To get an reloaded version of Settings, you need to call [ReloadableSettings.snapshot]
 *
 * This is an abstract class instead of an interface in order to support inline method, needed for reified types.
 */
abstract class Settings : SettingsBase {

    /**
     * Get the setting value at [key] and converts it to type [T]. If there is no such key, it returns null.
     *
     * This function is a type reified wrapper around [Settings.find]
     *
     * @throws SettingTypeConversionException if the value could not be converted to this type
     *
     * @see [SettingsBase.find]
     */
    inline fun <reified T : Any> find(key: String): T? = find(key, T::class.java)

    /**
     * Get the setting value at [key] and converts it to type [T].
     *
     * @throws NoSuchSettingException if there is no setting under this key
     * @throws SettingTypeConversionException if the value could not be converted to type [T]
     *
     * @see [SettingsBase.get]
     */
    inline operator fun <reified T : Any> get(key: String): T = get(key, T::class.java)


    /**
     * Get the [Setting] handler of type [T] at [key]. This always returns successfully, even if the value does not
     * exist or cannot be converted to the proper type. Any such errors are delayed until first invocation of
     * the relevant Setting property.

     * @see [SettingsBase.bind]
     */
    inline infix fun <reified T : Any> bind(key: String): Setting<T> = bind(key, T::class.java)
}


abstract class ValuedSettings : Settings() {
    override val topLevelKeys: Set<String> get() = toNestedMap().keys
    override fun <T : Any> get(key: String, type: Class<T>): T = find(key, type) ?: throw NoSuchSettingException(key.toString())

    override fun hashCode(): Int {
        return toFlatMap().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return (other is Settings && toFlatMap() == other.toFlatMap())
    }
}


private object EmptySettings : ValuedSettings() {
    override val empty = true

    override fun contains(key: String): Boolean = false

    override fun <T : Any> find(key: String, type: Class<T>): T? = null

    override fun toFlatMap(): Map<String, Any> = emptyMap()

    override fun toNestedMap(): Map<String, Any> = emptyMap()

    override fun <T : Any> bind(key: String, type: Class<T>): Setting<T> = ConstantSetting(null)
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