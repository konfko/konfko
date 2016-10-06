package com.github.konfko.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.Period

/**
 * @author markopi
 */
class DefaultSettingsTypeConverterTest {
    val converter = DefaultSettingsTypeConverter

    @Test
    fun stringToNumeric() {
        "122" mustConvertTo 122.toByte()
        "122" mustConvertTo 122.toByte()
        "-350" mustConvertTo -350.toShort()
        "326541" mustConvertTo 326541.toInt()
        Long.MAX_VALUE.toString() mustConvertTo Long.MAX_VALUE
        "12345678901234567890" mustConvertTo BigDecimal("12345678901234567890")
    }

    @Test
    fun stringToBoolean() {
        "true" mustConvertTo true
        "false" mustConvertTo false
        "TRUE" mustConvertTo true
    }

    @Test
    fun sameTypesNeedNoConversion() {
        "test" mustConvertTo "test"
        12 mustConvertTo 12
        Instant.now().let { it mustConvertTo it }
    }

    @Test
    fun time() {
        "P6M" mustConvertTo Period.ofMonths(6)
        "PT6M15S" mustConvertTo Duration.ofMinutes(6).plusSeconds(15)
        "2016-09-15T10:13:16.505Z" mustConvertTo Instant.parse("2016-09-15T10:13:16.505Z")
    }

    @Test
    fun enums() {
        "first" mustConvertTo EnumTest.first
        "second" mustConvertTo EnumTest.second
        "third" mustConvertTo EnumTest.third
    }

    @Test
    fun numberOutOfRangeFails() {
        try {
            "1300" mustConvertTo 12.toByte()
            fail("Should fail conversion")
        } catch (e: Exception) {
            assertThat(e.message).containsIgnoringCase("Out of range")
        }
    }

    @Test
    fun badEnumFails() {
        try {
            "fourth" mustConvertTo EnumTest.second
            fail("Should fail conversion")
        } catch (e: Exception) {
        }
    }

    private inline infix fun <reified T : Any> Any.mustConvertTo(expected: T) {
        assertThat(converter.convertTo(this, T::class.java)).isEqualTo(expected)
    }

    private enum class EnumTest {
        first, second, third
    }
}