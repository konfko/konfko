package com.github.konfko.core.source

import com.github.konfko.core.*
import com.github.konfko.core.derived.prefixBy
import com.github.konfko.core.structured.StructuredSettings
import com.github.konfko.core.structured.mergeNestedMaps
import com.github.konfko.core.watcher.ConfigurationChangeWatcher
import com.github.konfko.core.watcher.ReloadableSettings
import org.slf4j.Logger
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

        val allSettings = sources.map(SettingsSource::load)


        val mergedMap = mergeNestedMaps(allSettings.map { it.toNestedMap() })
        return StructuredSettings(mergedMap, typeConverter)
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
        path(Paths.get("config.settings"))
        // Loading will not fail if an optional source fails to load (A warning is written to log instead)
        // Also, can just specify a string for a path
        path("actor-config.settings").optional()
        systemProperties()
        // all settings from a single source can be transformed before being merged with others
        environment().transform { it.prefixBy("env") }
        classpath("default-system-config.settings")
                .optional()
                .transform { it.prefixBy("system") }
        provided(name = "hardcoded", settings = mapOf("user.home" to ".")).transform { it.prefixBy("env") }
    }
    return settings
}

class LoadSettingsFailure(val source: SettingsSource, val error: Exception)


class MakingSettingsSource internal constructor(private val delegate: SettingsSource) : SettingsSource by delegate {

    internal val transformers = mutableListOf<(Settings) -> Settings>()

    private var optional = false
    internal var failureHandler: (LoadSettingsFailure) -> Settings = DEFAULT_FAILURE_HANDLER



    override fun load(): Settings {
        val delegated: Settings = try {
            delegate.load()
        } catch(e: Exception) {
            if (optional) failureHandler.invoke(LoadSettingsFailure(delegate, e))
            else throw e
        }
        val transformed = transformers.fold(delegated, { delegated, transformer -> transformer(delegated) })
        return transformed
    }


    fun optional(optional: Boolean = true): MakingSettingsSource {
        this.optional = optional
        return this
    }

    fun optional(failureHandler: (LoadSettingsFailure) -> Settings): MakingSettingsSource {
        this.optional = true
        this.failureHandler = failureHandler
        return this
    }

    fun transform(transformer: (Settings) -> Settings): MakingSettingsSource {
        this.transformers.add(transformer)
        return this
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MakingSettingsSource::class.java)

        private val DEFAULT_FAILURE_HANDLER: (LoadSettingsFailure) -> Settings = { defaultFailureHandler(it) }

        internal fun defaultFailureHandler(failure: LoadSettingsFailure): Settings {
            val className = failure.source.javaClass.simpleName
            val sourceName = failure.source.name
            val errorMessage = failure.error.javaClass.name + ": " + failure.error.message
            val logMessage = "Could not load settings of type [$className] with name [$sourceName]: $errorMessage." +
                    " As the setting source is marked optional, loading will continue, with these settings treated as empty"
            val error = if (LOG.isDebugEnabled) failure.error else null
            LOG.warn(logMessage, error)
            return emptySettings()
        }
    }


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
    fun provided(name: String, settings: Any) = add(StructuredSettingsSource(name, settings))

    fun systemProperties() = add(systemPropertiesSource())
    fun environment() = add(environmentPropertiesSource())


    fun add(source: SettingsSource): MakingSettingsSource {
        val makingSource = MakingSettingsSource(source)
        sources.add(makingSource)
        return makingSource
    }

    fun add(resource: SettingsResource) = add(ResourceSettingsSource(resource, parser))
}
