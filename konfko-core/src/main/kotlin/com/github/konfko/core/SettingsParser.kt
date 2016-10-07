package com.github.konfko.core

import com.github.konfko.core.DefaultSettingsParser.findAndRegisterExtensionParsers
import com.github.konfko.core.source.SettingsResource
import com.github.konfko.core.structured.StructuredSettings
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author markopi
 */

/**
 * Parser for parsing a particular [SettingsResource] format into [Settings]
 */
interface SettingsParser {
    /**
     * Parses this resource into Settings
     */
    fun parse(resource: SettingsResource): Settings
}

/**
 * Used for registering supported extensions by auto-discovery with ServiceLoader mechanism
 */
interface ExtensionSettingsParserFactory {
    /** Supported extensions. Must not contain the leading period. Example: {"yaml", "yml"} */
    val extensions: Set<String>
    val parser: SettingsParser
}


/**
 * Parser for property files. Properties must be in UTF-8 charset.
 */
object PropertySettingsParser : SettingsParser {
    override fun parse(resource: SettingsResource): Settings {
        val map = resource.openReader()
                .use { reader -> Properties().apply { load(reader) } }

        return StructuredSettings(map)
    }
}

/**
 * A composite parser that delegates to an actual parses based on extensions.
 * Extensions are retrieved from [SettingsResource.name]
 *
 * A new instance has no parsers registered.
 */
open class DelegatingSettingsParser : SettingsParser {
    private val extensionParsers: ConcurrentHashMap<String, SettingsParser> = ConcurrentHashMap()
    val supportedExtensions: Set<String> get() = extensionParsers.keys

    /**
     * Register a settings parser for this resource name extension. If this extension already has an extension
     * parser, it will be replaced.
     */
    fun registerExtensionParser(extension: String, parser: SettingsParser) {
        extensionParsers[extension] = parser
    }

    /**
     * Finds extension parsers by [ServiceLoader] mechanisms and registers them.
     *
     * Parsers can be found by implementing [ExtensionSettingsParserFactory] and registering themselves in
     * META-INF/services.
     */
    protected fun findAndRegisterExtensionParsers() {
        val loader = ServiceLoader.load(ExtensionSettingsParserFactory::class.java)
        loader.forEach { pf ->
            val parser = pf.parser
            pf.extensions.forEach { DefaultSettingsParser.registerExtensionParser(it, parser) }
        }
    }

    /**
     * Find a registered parser for this resource's and uses it to parse the resource.
     *
     * @throws SettingsException if no parser for this extension exists.
     */
    override fun parse(resource: SettingsResource): Settings {
        val ext = toExtension(resource.name)
        val parser = extensionParsers[ext] ?: throw SettingsException("No parser for extension [$ext]")
        return parser.parse(resource)
    }
}

/**
 * Default settings parser, used when no custom parser is provided to SettingsMaker.
 *
 * This parser automatically registered settings extension and any parser discovered by
 * [findAndRegisterExtensionParsers]
 */
object DefaultSettingsParser : DelegatingSettingsParser() {
    init {
        registerExtensionParser("properties", PropertySettingsParser)
        findAndRegisterExtensionParsers()
    }
}

private fun toExtension(path: String): String {
    val lastPath = path.substringAfterLast('/')
    val lastPeriod = lastPath.lastIndexOf('.')
    // compares with <= to avoid detecting extension for hidden linux files such as .bashrc
    if (lastPeriod <= 0) return ""
    return lastPath.substring(lastPeriod + 1)
}