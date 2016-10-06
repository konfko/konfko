package com.github.konfko.core.structured

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author markopi
 */
class StructuredSettingsConverterKtTest {
    @Test
    fun expand() {
        val expanded = flatToNested(flatData)

        assertThat(expanded).isEqualTo(nestedData)
    }

    @Test
    fun flatten() {
        val flattened = nestedToFlat(nestedData)

        assertThat(flattened).isEqualTo(flatData)
    }

    @Test
    fun expandFlatPropertyIntoNested() {
        val data = linkedMapOf(
                "dataSource.second.url" to "jdbc:test:localhost/second",
                "dataSource.second" to mapOf(
                        "username" to "secondUser",
                        "password" to "secondPassword"
                ),
                "dataSource.second.connectionTimeout" to 20
        )
        val nested = flatToNested(data) as Map<*, *>

        assertThat(nested.deepGet("dataSource.second.url")).isEqualTo("jdbc:test:localhost/second")
        assertThat(nested.deepGet("dataSource.second.username")).isEqualTo("secondUser")
        assertThat(nested.deepGet("dataSource.second.password")).isEqualTo("secondPassword")
        assertThat(nested.deepGet("dataSource.second")).isEqualTo(mapOf(
                "url" to "jdbc:test:localhost/second",
                "username" to "secondUser",
                "password" to "secondPassword",
                "connectionTimeout" to 20
        ))
    }

    fun Map<*, *>.deepGet(deepKey: String): Any? {
        val segments = deepKey.split('.')
        return segments.fold(this as Any?, { map, segment ->
            if (map is Map<*, *>) map[segment] else null
        })
    }

    companion object {
        private val flatData = mapOf(
                "dataSource.first.url" to "jdbc:test:localhost/first",
                "dataSource.first.username" to "firstUser",
                "dataSource.first.password" to "firstPassword",
                "dataSource.second.url" to "jdbc:test:localhost/second",
                "dataSource.second.username" to "secondUser",
                "dataSource.second.password" to "secondPassword",
                "dataSource.second.connectionTimeout" to "20"
        )

        private val nestedData = mapOf(
                "dataSource" to mapOf(
                        "first" to mapOf(
                                "url" to "jdbc:test:localhost/first",
                                "username" to "firstUser",
                                "password" to "firstPassword"),
                        "second" to mapOf(
                                "url" to "jdbc:test:localhost/second",
                                "username" to "secondUser",
                                "password" to "secondPassword",
                                "connectionTimeout" to "20")))
    }

}