package com.github.konfko.core.source

import com.github.konfko.core.Settings
import com.github.konfko.core.SettingsParser
import com.github.konfko.core.structured.StructuredSettings
import java.time.Instant
import kotlin.properties.Delegates.observable

/**
 * @author markopi
 */

/**
 * A source for particular settings. Can be optionally backed by an external resource
 */
interface SettingsSource {
    /**
     * Settings source name. For files/classpath resources it is the class name.
     *
     * If this source is backed by an external resource, the default settings parser will try to extract the extension
     * from this value in order to figure out how to parse the resource input stream
     */
    val name: String

    /**
     * Instant of when this source was last modified. Can be null if the source can never change
     */
    val lastModified: Instant?

    /**
     * backing resource, if any
     */
    val backingResource: SettingsResource?

    /**
     * Loads the settings from this source
     */
    fun load(): Settings
}


/**
 * A [SettingsSource] that is backed by a backing resource
 */
class ResourceSettingsSource(
        override val backingResource: SettingsResource,
        private val parser: SettingsParser)
    : SettingsSource {

    override val name = backingResource.name
    override val lastModified: Instant? get() = backingResource.lastModified

    override fun load(): Settings = parser.parse(backingResource)
}

/**
 * A [SettingsSource] that is backed by an map.
 *
 * Supports updates by setting the [settings] property to a new map
 */
class StructuredSettingsSource(
        override val name: String,
        settings: Any)
    : SettingsSource {

    override var lastModified: Instant = Instant.now()
        private set

    var settings: Any by observable(settings) { property, old, new -> lastModified = Instant.now() }

    override val backingResource: SettingsResource? = null

    override fun load(): Settings = StructuredSettings(settings)
}

private object SystemPropertiesSource : SettingsSource {
    override val name = "<system settings>"
    override val lastModified: Instant? = null
    override val backingResource: SettingsResource? = null

    override fun load(): Settings {
        @Suppress("UNCHECKED_CAST")
        return StructuredSettings(System.getProperties() as Map<String, Any>)
    }
}

private object EnvironmentPropertiesSource : SettingsSource {
    override val name = "<environment settings>"
    override val lastModified: Instant? = null
    override val backingResource: SettingsResource? = null

    private fun convertKey(from: String): String = from.toLowerCase().replace('_', '.')

    override fun load(): Settings {
        val converted = System.getenv().mapKeys { convertKey(it.key) }
        return StructuredSettings(converted)
    }
}


/**
 * Returns all system settings
 */
internal fun systemPropertiesSource(): SettingsSource = SystemPropertiesSource

/**
 * Returns all environment settings, changing key format from SNAKE_CASE to period.case
 */
internal fun environmentPropertiesSource(): SettingsSource = EnvironmentPropertiesSource