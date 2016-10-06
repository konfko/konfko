package com.github.konfko.core.structured

import com.github.konfko.core.toIntOrNull
import com.github.konfko.core.uncheckedCast
import java.util.*

/**
 * @author markopi
 */
internal fun String.resolveSettingKey(key: String) = when {
    this.isEmpty() -> key
    key.isEmpty() -> this
    else -> this + "." + key
}

@Suppress("UNCHECKED_CAST")
private fun putHere(target: Any, key: String, value: Any): Any? {
    if (target is MutableMap<*, *>) {
        val t = target as MutableMap<String, Any>
        val old = t[key]
        if (old is Map<*, *>) {
            val mutable = (if (old is MutableMap<*, *>) old else LinkedHashMap(old)) as MutableMap<String, Any>
            val oldValue: Any?
            if (value is Map<*, *>) {
                mutable.putAll(value as Map<String, Any>)
                oldValue = old
            } else {
                oldValue = mutable.put("", value)
            }
            t[key] = mutable
            return oldValue
        } else {
            return t.put(key, value)
        }
    } else if (target is MutableList<*>) {
        val t = target as MutableList<Any>
        val index = key.toIntOrNull() ?: throw IllegalArgumentException("Target [$target] is a List, but could not convert key [$key] to integer")
        val size = t.size
        return when {
            index < 0 || index > size -> throw IllegalArgumentException("List key index [$index] is out of bounds [0..$size]")
            index == size -> {
                t.add(value); null
            }
            else -> t.set(index, value)
        }
    } else {
        throw IllegalArgumentException("Tried to put value [$value] into target [$target] of type [${target.javaClass.name}], which is not a valid root container")
    }
}

@Suppress("UNCHECKED_CAST")
private fun putNested(target: Any, key: SettingKey, value: Any) {
    if (key.segments.isEmpty()) {
        putHere(target, "", value)
        return
    }
    val lastTarget = key.segments.dropLast(1).foldIndexed(target, { index, currentTarget, segment ->
        val existing = segment.find(currentTarget).firstOrNull()
        val actual = existing ?: LinkedHashMap<String, Any>().apply { putHere(currentTarget, segment.property, this) }

        if (actual !is MutableMap<*, *> && actual !is List<*>) {
            val intermediate = LinkedHashMap<String, Any>()
            intermediate.put("", actual)
            if (currentTarget is MutableMap<*, *>) {
                val t = currentTarget as MutableMap<String, Any>
                t.put(segment.property, intermediate)
            } else if (currentTarget is MutableList<*>) {
                val t = currentTarget as MutableList<Any>
                t[segment.property.toInt()] = intermediate
            } else {
                val partKey = SettingKey(key.segments.subList(0, index))
                throw IllegalArgumentException("Could not enter intermediate value at path $partKey")
            }
            intermediate
        } else {
            actual
        }
    })

    val actualValue = flatToNested(value)
    val old = putHere(lastTarget, key.segments.last().property, actualValue)
    if (old != null) {
        if (old is Map<*, *> != value is Map<*, *>) {
            throw IllegalArgumentException("Key [$key] is used both as a node and leaf")
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun flatToNested(data: Any): Any {
    return when (data) {
        is Map<*, *> -> {
            val result = LinkedHashMap<String, Any>()
            val t = data as Map<String, Any>
            for ((key, value) in t) {
                putNested(result, SettingKey.parse(key), value)
            }
            result
        }
        is List<*> -> {
            val t = data as List<Any>
            val result = t.map { flatToNested(it) }
            result
        }
        else -> data
    }
}

private fun nestedToFlat(target: MutableMap<String, Any>, prefix: String, obj: Any) {
    if (obj is Map<*, *>) {
        val o: Map<String, Any> = obj.uncheckedCast()
        for ((key1, value) in o) {
            val key = prefix.resolveSettingKey(key1)
            nestedToFlat(target, key, value)
        }
    } else if (obj is List<*>) {
        val o = obj.uncheckedCast<Any>()
        val listPrefix = if (prefix.isEmpty()) prefix else prefix + "."
        for (i in o.indices) {
            nestedToFlat(target, listPrefix + i, o[i])
        }
    } else {
        target.put(prefix, obj)
    }
}


internal fun nestedToFlat(nested: Any?): Map<String, Any> {
    val result = LinkedHashMap<String, Any>()
    if (nested == null) return result
    nestedToFlat(result, "", nested)
    return result
}


internal fun findNested(nestedProperties: Any?, key: String) = findNested(nestedProperties, SettingKey.parse(key))

internal fun findNested(nestedProperties: Any?, key: SettingKey): Any? {
    return key.find(nestedProperties).firstOrNull()
}

@Suppress("UNCHECKED_CAST")
internal fun mapifyNested(data: Any?): Map<String, Any> = when (data) {
    is Map<*, *> -> data as Map<String, Any>
    is List<*> -> (data as List<Any>).foldIndexed(LinkedHashMap<String, Any>(), { i, map, e -> map.put(i.toString(), e); map })
    null -> mapOf()
    else -> mapOf("" to data)
}

