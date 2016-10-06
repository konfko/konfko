package com.github.konfko.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.konfko.core.ExtensionSettingsParserFactory
import com.github.konfko.core.Settings
import com.github.konfko.core.SettingsParser
import com.github.konfko.core.source.SettingsResource
import com.github.konfko.core.structured.StructuredSettings

/**
 * @author markopi
 */

/**
 *  Base parser factory for specialized json and yaml parsers
 */
abstract class JacksonSettingsParserFactory(val mapper: ObjectMapper) : ExtensionSettingsParserFactory {
    protected fun parse(resource: SettingsResource): Settings {
        @Suppress("UNCHECKED_CAST")
        val map = mapper.readValue(resource.openInputStream(), Map::class.java) as Map<String, Any>
        return StructuredSettings(map)
    }

    override val parser: SettingsParser = JacksonSettingsParser(this)

    private class JacksonSettingsParser(val factory: JacksonSettingsParserFactory): SettingsParser {
        override fun parse(resource: SettingsResource): Settings = factory.parse(resource)
    }
}

/**
 *  Parser factory for json files
 */
class JsonSettingsParserFactory : JacksonSettingsParserFactory(createJsonParsingObjectMapper()) {
    override val extensions = setOf("json")
}

/**
 *  Parser factory for yaml files
 */
class YamlSettingsParserFactory : JacksonSettingsParserFactory(createYamlParsingObjectMapper()) {
    override val extensions = setOf("yaml", "yml")
}


internal fun registerCommonModules(mapper: ObjectMapper) {
    mapper.registerModule(JavaTimeModule())
    // should automatically register all modules in classpath? seems a bit much
    //mapper.findAndRegisterModules()
}

internal fun createJsonParsingObjectMapper(): ObjectMapper = ObjectMapper().apply {
    registerCommonModules(this)
}


internal fun createYamlParsingObjectMapper(): YAMLMapper = YAMLMapper().apply {
    registerCommonModules(this)
}



