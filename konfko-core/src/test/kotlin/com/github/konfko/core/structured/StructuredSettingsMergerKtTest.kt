package com.github.konfko.core.structured

import com.github.konfko.core.structured.mergeNestedMaps
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author markopi
 */
class StructuredSettingsMergerKtTest {
    @Test
    fun mergeSingleMap() {
        val first = mapOf(
                "dataSource" to mapOf(
                        "first" to first,
                        "second" to second))

        val merged = mergeNestedMaps(listOf(first))
        assertThat(merged).isEqualTo(first)
    }

    @Test
    fun mergeUnclashingNodes() {
        val expected = mapOf(
                "dataSource" to mapOf(
                        "first" to first,
                        "second" to second))

        val first = mapOf(
                "dataSource" to mapOf(
                        "first" to first))
        val second = mapOf(
                "dataSource" to mapOf(
                        "second" to second))

        val merged = mergeNestedMaps(listOf(first, second))
        assertThat(merged).isEqualTo(expected)
    }

    @Test
    fun mergeClashingNodes() {
        val expected = mapOf(
                "dataSource" to mapOf(
                        "merged" to mapOf(
                                "url" to "jdbc:test:localhost/first",
                                "username" to "user",
                                "password" to "firstPassword",
                                "maxSize" to 10,
                                "connectionTimeout" to 20
                        )))

        val first = mapOf(
                "dataSource" to mapOf(
                        "merged" to first))
        val second = mapOf(
                "dataSource" to mapOf(
                        "merged" to second))

        val merged = mergeNestedMaps(listOf(first, second))
        assertThat(merged).isEqualTo(expected)
    }

    @Test
    fun mergeDefaultSettings() {
        val expected = mapOf(
                "dataSource" to mapOf(
                        "first" to mapOf(
                                "url" to "jdbc:test:localhost/first",
                                "username" to "user",
                                "password" to "firstPassword",
                                "maxSize" to 10,
                                "connectionTimeout" to 30),
                        "second" to mapOf(
                                "url" to "jdbc:test:localhost/second",
                                "username" to "secondUser",
                                "password" to "secondPassword",
                                "connectionTimeout" to 20)
                ))

        val defaultAny = mapOf(
                "dataSource" to mapOf(
                        "*" to defaultAny))
        val first = mapOf(
                "dataSource" to mapOf(
                        "first" to first))
        val second = mapOf(
                "dataSource" to mapOf(
                        "second" to second))

        val merged = mergeNestedMaps(listOf(first, second, defaultAny))
        assertThat(merged).isEqualTo(expected)
    }


    companion object {
        val first = mapOf(
                "url" to "jdbc:test:localhost/first",
                "username" to "user",
                "password" to "firstPassword",
                "maxSize" to 10)
        val second = mapOf(
                "url" to "jdbc:test:localhost/second",
                "username" to "secondUser",
                "password" to "secondPassword",
                "connectionTimeout" to 20)

        val defaultAny = mapOf(
                "connectionTimeout" to 30)
    }
}