package com.github.konfko.core

import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * @author markopi
 */

/**
 * Converter to convert a particular setting into requested type
 */
interface SettingsTypeConverter {
    fun canConvert(sourceType: Class<*>, targetType: Class<*>): Boolean

    /**
     * Converts a [value] to target type [targetType]
     *
     * @throws SettingTypeConversionException if the value could not be converted to targetType
     */
    fun <F : Any, T : Any> convertTo(value: F, targetType: Class<T>): T
}

/**
 * Request for a single type conversion
 */
data class ConversionRequest<out F : Any, T : Any>(val from: F, val targetType: Class<T>, val root: SettingsTypeConverter)

/**
 * Used for type converter auto-discovery with ServiceLoader mechanism
 */
interface TypeConverterFactory<in F : Any, T : Any> {
    val sourceType: Class<*>
    val targetType: Class<*>
    val converter: (ConversionRequest<F, T>) -> T
}

private val primitiveToWrapperMap: Map<Class<*>, Class<*>> = mapOf(
        java.lang.Byte.TYPE to java.lang.Byte.valueOf(0).javaClass,
        java.lang.Short.TYPE to java.lang.Short.valueOf(0).javaClass,
        java.lang.Integer.TYPE to java.lang.Integer.valueOf(0).javaClass,
        java.lang.Long.TYPE to java.lang.Long.valueOf(0).javaClass,
        java.lang.Float.TYPE to java.lang.Float.valueOf(0.0f).javaClass,
        java.lang.Double.TYPE to java.lang.Double.valueOf(0.0).javaClass,
        java.lang.Boolean.TYPE to java.lang.Boolean.valueOf(false).javaClass,
        java.lang.Character.TYPE to java.lang.Character.valueOf(' ').javaClass
)

/**
 * A composite type converter which can be configured with several type-to-type converters.
 *
 * On conversion request, this converter will find the most specific converter defined and use it for conversion.
 *
 * A newly created has no registered converters, however it provides functions [addDefaultConverters] and
 * [findAndRegisterTypeConverters] that can be called to register some basic and discoverable converters
 */
abstract class ConfigurableSettingsTypeConverter : SettingsTypeConverter {
    private val converters: ConcurrentMap<Class<*>, ConcurrentMap<Class<*>, (ConversionRequest<*, *>) -> Any>> = ConcurrentHashMap()

    private inline fun <T> Deque<T>.pollFirstThat(predicate: (T) -> Boolean): T? {
        while (true) {
            val next = this.poll() ?: return null
            if (predicate(next)) return next
        }
    }

    private fun createClassHierarchySequence(key: Class<*>): Sequence<Class<*>> {

        val processed = mutableSetOf<Class<*>>()
        val queue = LinkedList<Class<*>>().apply { add(key) }
        return generateSequence {
            while (true) {
                val next = queue.pollFirstThat { !processed.contains(it) } ?: return@generateSequence null
                next.superclass?.let { if (!processed.contains(it)) queue.add(it) }
                next.interfaces.forEach { if (!processed.contains(it)) queue.add(it) }
                processed.add(next)
                return@generateSequence next
            }
            @Suppress("UNREACHABLE_CODE") throw AssertionError()
        }
    }


    fun <V> ConcurrentMap<Class<*>, V>.getByClassHierarchy(key: Class<*>): V? {
        val actualKey = wrapIfPrimitive(key)
        get(actualKey)?.let { return it }

        val classes = createClassHierarchySequence(actualKey)
        return classes.map { get(it) }.firstOrNull { it != null }
    }

    private fun wrapIfPrimitive(sourceType: Class<*>): Class<*> {
        return primitiveToWrapperMap[sourceType] ?: sourceType
    }

    fun <F : Any, T : Any> addTypedConverter(sourceType: Class<F>, targetType: Class<T>, converter: (ConversionRequest<F, T>) -> T) {
        val convertersFrom = converters.getOrPut(wrapIfPrimitive(sourceType), { ConcurrentHashMap() })

        @Suppress("UNCHECKED_CAST")
        val cnv = converter as (ConversionRequest<*, *>) -> Any
        convertersFrom[wrapIfPrimitive(targetType)] = cnv
    }

    inline fun <reified F : Any, reified T : Any> addTypedConverter(noinline converter: (ConversionRequest<F, T>) -> T) =
            addTypedConverter(F::class.java, T::class.java, converter)

    fun <F : Any, T : Any> addConverter(sourceType: Class<F>, targetType: Class<T>, converter: (F) -> T) =
            addTypedConverter(sourceType, targetType, { converter(it.from) })

    inline fun <reified F : Any, reified T : Any> addConverter(noinline converter: (F) -> T) =
            addConverter(F::class.java, T::class.java, converter)

    fun <F : Any, T : Any> getConverter(from: Class<F>, to: Class<T>): ((ConversionRequest<F, T>) -> T)? {
        val fromConverter = converters.getByClassHierarchy(from) ?: return null
        @Suppress("UNCHECKED_CAST")
        return fromConverter.getByClassHierarchy(to) as ((ConversionRequest<F, T>) -> T)? ?: return null
    }

    override fun canConvert(sourceType: Class<*>, targetType: Class<*>) = getConverter(sourceType, targetType) != null

    /**
     * @throws SettingTypeConversionException if the value could not be converted to targetType
     * @throws NoSuchConverterException if no converter for this value/type combination could be found
     */
    override fun <F : Any, T : Any> convertTo(value: F, targetType: Class<T>): T {
        val converter = getConverter(value.javaClass, targetType) ?:
                if (wrapIfPrimitive(targetType).isAssignableFrom(wrapIfPrimitive(value.javaClass))) {
                    @Suppress("UNCHECKED_CAST")
                    return value as T
                } else {
                    throw NoSuchConverterException(value, targetType)
                }

        val result = try {
            converter(ConversionRequest(value, targetType, this))
        } catch (e: Exception) {
            throw SettingTypeConversionException(value, targetType, e.message, e)
        }
        return result
    }


    protected fun addDefaultConverters() {
        // numeric
        addConverter<String, Byte> { it.toByte() }
        addConverter<String, Short> { it.toShort() }
        addConverter<String, Int> { it.toInt() }
        addConverter<String, Long> { it.toLong() }
        addConverter<String, Float> { it.toFloat() }
        addConverter<String, Double> { it.toDouble() }
        addConverter<String, BigDecimal> { BigDecimal(it) }


        addConverter<String, Boolean> { it.toBoolean() }
        addConverter<String, Char> { if (it.length == 1) it[0] else throw IllegalArgumentException("String [$it] is not a valid Char") }

        addConverter<String, Duration>(Duration::parse)
        addConverter<String, Period>(Period::parse)
        addConverter<String, Instant>(Instant::parse)

        // default toString
        addConverter<Any, String> { it.toString() }

        // searches through all enums to find the matching enum. java.lang.Enum.valueOf() is not available in kotlin.
        // Is there a better way?
        addTypedConverter<String, Enum<*>> { request -> request.targetType.enumConstants.first { it.name == request.from } }

        addConverter<String, UUID>(UUID::fromString)
        addConverter<String, URI>(URI::create)
        addConverter(::URL)
    }

    protected fun findAndRegisterTypeConverters() {
        val loader = ServiceLoader.load(TypeConverterFactory::class.java)
        loader.forEach { tc ->
            @Suppress("UNCHECKED_CAST")
            val f = tc.converter as (ConversionRequest<Any, Any>) -> Any
            addTypedConverter(f)
        }
    }
}

/**
 * Default settings type converter, used when no custom converter is provided.
 *
 */
object DefaultSettingsTypeConverter : ConfigurableSettingsTypeConverter() {
    init {
        addDefaultConverters()
        findAndRegisterTypeConverters()
    }
}


private fun convertReason(reason: String?): String = if (reason != null) ": $reason" else ""

/**
 * Thrown if there was an error during type conversion
 */
@Suppress("CanBeParameter")
open class SettingTypeConversionException(val value: Any, val targetType: Class<*>, val reason: String?, cause: Throwable? = null)
: SettingsException("Could not convert value [$value] of type [${value.javaClass.name}] to type [${targetType.name}]${convertReason(reason)}", cause)

/**
 * Thrown when no applicable converter was found
 */
open class NoSuchConverterException(value: Any, targetType: Class<*>)
: SettingTypeConversionException(value, targetType, "No converter found")