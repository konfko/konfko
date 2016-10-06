package com.github.konfko.jackson

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.konfko.core.ConversionRequest
import com.github.konfko.core.SettingsTypeConverter
import com.github.konfko.core.TypeConverterFactory

/**
 * @author markopi
 */

/**
 * Converts values to provided type, using provided jackson [ObjectMapper].
 *
 * Note that once this converter is selected, any conversion of sub values will be done using by jackson only.
 *
 * For example:
 * converting to data class Alarm(val message: String, val time: ZonedDateTime), message and time
 * properties will be converted using jackson ObjectMapper. There is no fallback back to the SettingsMapper's type converter
 *
 */
open class JacksonTypeConverter(val mapper: ObjectMapper) : SettingsTypeConverter {

    override fun canConvert(sourceType: Class<*>, targetType: Class<*>): Boolean {
        return mapper.canSerialize(sourceType) && mapper.canDeserialize(mapper.typeFactory.constructType(targetType))
    }

    override fun <F : Any, T : Any> convertTo(value: F, targetType: Class<T>): T {
        return mapper.convertValue(value, targetType)
    }
}

/**
 * Default jackson type converter. This instance is the one found by
 * [com.github.konfko.core.ConfigurableSettingsTypeConverter] when this jar is included in classpath
 */
object DefaultJacksonTypeConverter : JacksonTypeConverter(defaultObjectMapperConverter()) {
}

private fun defaultObjectMapperConverter() = ObjectMapper().apply {
    findAndRegisterModules()
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}


internal class JacksonTypeConverterFactory : TypeConverterFactory<Any, Any> {
    override val sourceType: Class<*> = Any::class.java
    override val targetType: Class<*> = Any::class.java
    // todo registering custom type converters should really be done better
    override val converter: (ConversionRequest<Any, Any>) -> Any
        get() = { request -> DefaultJacksonTypeConverter.convertTo(request.from, request.targetType) }

}