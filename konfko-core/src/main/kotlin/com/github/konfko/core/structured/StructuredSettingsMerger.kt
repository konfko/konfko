package com.github.konfko.core.structured

import com.github.konfko.core.uncheckedCast
import java.util.*

/**
 * @author markopi
 */
internal fun mergeNestedMaps(maps: List<Map<String, Any>>): Map<String, Any> {
    if (maps.isEmpty()) return emptyMap()
    if (maps.size == 1) return maps.first()

    val keys = maps.flatMapTo(LinkedHashSet()) { it.keys }.minus("*")

    val grouped = maps.flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .resolveWildcardSettings(keys)

    return grouped.mapValues { e -> mergeNestedValues(e.value) }
}

private fun mergeNestedValues(values: List<Any>): Any {
    if (values.size <= 1) return values.first()
    val mapsToMerge = mutableListOf<Map<String, Any>>()
    for (value in values) {
        if (value is Map<*, *>) {
            mapsToMerge.add(value.uncheckedCast())
        } else return value
    }
    return mergeNestedMaps(mapsToMerge)
}


private fun Map<String, List<Any>>.resolveWildcardSettings(keys: Set<String>): Map<String, List<Any>> {
    val wildcardSettings = get("*") ?: return this
    return LinkedHashMap(this).apply {
        remove("*")
        keys.forEach { key ->
            merge(key, wildcardSettings, { old, new -> old.plus(new) })
        }
    }
}
