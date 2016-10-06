package com.github.konfko.core.structured

import com.github.konfko.core.toIntOrNull

/**
 * @author markopi
 */
internal class SettingKey(val segments: List<SettingKeySegment>) {
    companion object {
        fun parse(key: String): SettingKey {
            return SettingKey(key.split(".").filter { it.isNotEmpty() }.map { SettingKeySegment.Property(it) })
        }
    }

    override fun toString(): String = segments.joinToString(".") { it.toString() }

    fun find(data: Any?): List<Any> {
        val level = if (data != null) listOf(data) else emptyList()
        return segments.fold(level) { level, segment ->
            level.flatMap { segment.find(it) }
        }
    }

    fun resolve(relative: String): SettingKey = when {
        relative.isEmpty() -> this
        else -> SettingKey(segments.plus(parse(relative).segments))
    }

    fun resolve(relative: SettingKey): SettingKey = SettingKey(segments.plus(relative.segments))
}


internal sealed class SettingKeySegment {

    abstract val property: String
    abstract fun find(data: Any): List<Any>

    class Property(override val property: String) : SettingKeySegment() {
        override fun find(data: Any): List<Any> {
            return when (data) {
                is Map<*, *> -> data[property]?.let { listOf(it) } ?: emptyList()
                is List<*> -> property.toIntOrNull()
                        ?.let { if (it >= 0 && it < data.size) data[it] else null }
                        ?.let { listOf(it) }
                        ?: emptyList()
                else -> if (property == "") listOf(data) else emptyList()
            }
        }

        override fun toString(): String = property
    }
}

