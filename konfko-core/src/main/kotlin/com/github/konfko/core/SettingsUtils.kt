package com.github.konfko.core

/**
 * @author markopi
 */
internal fun <E> List<*>.uncheckedCast(): List<E> {
    @Suppress("UNCHECKED_CAST")
    return this as List<E>
}

internal fun <K, V> Map<*, *>.uncheckedCast(): Map<K, V> {
    @Suppress("UNCHECKED_CAST")
    return this as Map<K, V>
}

internal fun String.toIntOrNull(): Int? =
        try {
            toInt()
        } catch(e: NumberFormatException) {
            null
        }

internal fun <K, V> MutableMap<K, V>.removeInPlace(predicate: (entry: Map.Entry<K, V>) -> Boolean) {
    val it = entries.iterator()
    while (it.hasNext()) {
        val entry = it.next()
        if (predicate(entry)) it.remove()
    }
}