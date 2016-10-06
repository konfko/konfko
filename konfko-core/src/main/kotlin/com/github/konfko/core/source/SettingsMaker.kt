package com.github.konfko.core.source

import com.github.konfko.core.*
import com.github.konfko.core.structured.StructuredSettings
import com.github.konfko.core.structured.mergeNestedMaps
import com.github.konfko.core.watcher.ConfigurationChangeWatcher
import com.github.konfko.core.watcher.ReloadableSettings
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * @author markopi
 */

/**
 * Entry point for creating new settings.
 *
 * Used to create both static and reloadable settings. Sources to load from are configured in blocks inside
 * [make] and [makeAndWatch]
 *
 * Can be extended by adding extension functions to [SettingsMakerContext]
 *
 * @sample settingsMakerSample
 */
class SettingsMaker(
        val typeConverter: SettingsTypeConverter = DefaultSettingsTypeConverter,
        val parser: SettingsParser = DefaultSettingsParser
) {
    private val LOG = LoggerFactory.getLogger(javaClass)

    /**
     * Creates static settings from sources provided in the initialization block
     */
    fun make(init: SettingsMakerContext.() -> Unit): Settings {
        return load(makeSources(init))
    }

    /**
     * Creates reloadable settings from sources provided in the initialization block
     *
     * Uses the provided watcher to watch for changes and trigger reloads
     *
     * Reload can also be triggered manually via [ReloadableSettings.triggerReload]
     */
    fun makeAndWatch(watcher: ConfigurationChangeWatcher, init: SettingsMakerContext.() -> Unit): ReloadableSettings {
        val sources = makeSources(init)
        return ReloadableSettings(sources, { load(it) }, watcher)
    }

    private fun load(sources: List<SettingsSource>): Settings {
        if (sources.isEmpty()) throw SettingsException("No settings sources were provided")

        val allSettings = sources.map { source -> loadSource(source) }


        val mergedMap = mergeNestedMaps(allSettings.map { it.toNestedMap() })
        return StructuredSettings(mergedMap, typeConverter)
    }

    private fun loadSource(source: SettingsSource): Settings {
        return try {
            source.load()
        } catch(e: Exception) {
            if (source is MakingSettingsSource && source.optional) {
                LOG.warn("Couldn't load settings from ${source.name}. However, this source is marked as optional, so loading will continue", e)
                emptySettings()
            } else throw e
        }
    }

    private fun makeSources(init: SettingsMakerContext.() -> Unit): List<SettingsSource> {
        val context = SettingsMakerContext(parser)
        context.init()
        val sources = context.sources.toList()
        return sources
    }
}

private fun settingsMakerSample(): Settings {
    val settings = SettingsMaker().make {
        // highest priority sources are defined first
        path(Paths.get("config.properties"))
        // Loading will not fail if an optional source fails to load (A warning with stacktrace is written to log instead)
        // Also, can just specify a string for a path
        path("actor-config.properties") optional true
        systemProperties()
        // all settings from a single source can be put under a prefix
        environment() under "env"
        classpath("default-config.properties")
        map(name = "hardcoded", properties = mapOf("user.home" to ".")) under "env"
    }
    return settings
}

class MakingSettingsSource internal constructor(private val delegate: SettingsSource) : SettingsSource by delegate {
    private var prefix = ""
    internal var optional = false
        private set

    override fun load(): Settings {
        val delegated = delegate.load()
        if (prefix == "" || delegated.empty) {
            return delegated
        } else {
            val map = mapOf(prefix to delegated.toNestedMap())
            return StructuredSettings(map)
        }
    }

    infix fun under(prefix: String): MakingSettingsSource = apply { this.prefix = prefix }

    infix fun optional(optional: Boolean): MakingSettingsSource = apply { this.optional = optional }
}

/**
 * Context for providing Settings sources. Can be extended with extension methods calling [add]
 * to make providing other types of sources a bit easier
 */
class SettingsMakerContext internal constructor(private val parser: SettingsParser = DefaultSettingsParser) {
    internal val sources: MutableList<MakingSettingsSource> = mutableListOf()

    fun path(path: String) = add(PathResource(Paths.get(path)))
    fun path(path: Path) = add(PathResource(path))
    fun path(path: File) = add(PathResource(path.toPath()))
    fun classpath(classpath: String) = add(ClassPathResource(classpath))
    fun map(name: String, properties: Map<String, Any>) = add(MapSettingsSource(name, properties))

    fun systemProperties() = add(systemPropertiesSource())
    fun environment() = add(environmentPropertiesSource())

    fun add(source: SettingsSource): MakingSettingsSource {
        val makingSource = MakingSettingsSource(source)
        sources.add(makingSource)
        return makingSource
    }

    fun add(resource: SettingsResource) = add(ResourceSettingsSource(resource, parser))
}
