package com.github.konfko.core.structured

import com.github.konfko.core.*

/**
 * @author markopi
 */

/**
 * A Settings implementation that can navigate through nested [Map] and [List] objects.
 *
 * It assumes that all maps have string keys.
 *
 * During initialization, it will walk through the provided structure and convert flat keys to a structure of nested maps.
 *
 * For example, mapOf("dataSource.first.username" to "name") is converted to
 * mapOf("dataSource" to mapOf("first" to mapOf("username" to "name)))
 *
 * If the provided data is neither a Map or a List, it is interpreted as mapOf("" to data).
 */
class StructuredSettings(data: Any,
                         private val typeConverter: SettingsTypeConverter = DefaultSettingsTypeConverter) :
        AbstractSettings() {

    private val nestedProperties: Map<String, Any> = mapifyNested(flatToNested(data))

    override val empty: Boolean get() = nestedProperties.isEmpty()
    override fun toFlatMap(): Map<String, Any> = nestedToFlat(nestedProperties)
    override fun contains(key: String): Boolean = findNested(nestedProperties, key) != null

    override fun <T : Any> getTypedIfPresent(key: String, type: Class<T>): T? =
            findNested(nestedProperties, key)
                    ?.let { value -> typeConverter.convertTo(value, type) }

    override fun toNestedMap(): Map<String, Any> = nestedProperties

    override fun <T : Any> atTyped(key: String, type: Class<T>): Setting<T> =
            try {
                ConstantSetting(getTypedIfPresent(key, type))
            } catch (e: SettingsException) {
                ConstantSetting(null, conversionError = e)
            }
}
