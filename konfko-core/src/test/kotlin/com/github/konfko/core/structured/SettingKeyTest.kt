package com.github.konfko.core.structured

import com.github.konfko.core.structured.SettingKey
import org.assertj.core.api.AbstractIterableAssert
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions
import org.junit.Test

/**
 * @author markopi
 */
class SettingKeyTest {
    @Test
    fun findSingleLevel() {
        SettingKey.parse("dataSource").find(data).assert()
                .containsExactly(data["dataSource"])

        SettingKey.parse("noSuchProperty").find(data).assert()
                .isEmpty()
    }

    @Test
    fun findSecondLevel() {
        val expectedDataSource = data["dataSource"] as Map<*, *>

        SettingKey.parse("dataSource.first").find(data).assert()
            .containsExactly(expectedDataSource["first"])

        SettingKey.parse("dataSource.second").find(data).assert()
            .containsExactly(expectedDataSource["second"])

        SettingKey.parse("dataSource.noSuchProperty").find(data).assert()
            .isEmpty()

        SettingKey.parse("noSuchProperty.first").find(data).assert()
            .isEmpty()

    }

    @Test
    fun findThirdLevelLevel() {
        SettingKey.parse("dataSource.first.url").find(data).assert()
            .containsExactly("jdbc:test:localhost/first")

        SettingKey.parse("dataSource.first.maxSize").find(data).assert()
            .containsExactly(10)

        SettingKey.parse("dataSource.second.url").find(data).assert()
            .containsExactly("jdbc:test:localhost/second")

        SettingKey.parse("dataSource.second.noSuchProperty").find(data).assert()
            .isEmpty()

        SettingKey.parse("dataSource.noSuchProperty.password").find(data).assert()
            .isEmpty()
    }

    companion object {
        val data = mapOf(
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

    }
}

fun <A> A.assert(): AbstractObjectAssert<*, A> = Assertions.assertThat(this)

fun <A : Iterable<T>, T> A.assert(): AbstractIterableAssert<*, *, T> {
    return Assertions.assertThat(this)
}

//class PubListAssert<T>(actual: List<T>) : ListAssert<T>(actual) //fun <S, A, T> ListAssert<S, A, T>.map(mapper: (T) -> S): ListAssert<A> {
//    val es = arrayOf(Extractor<T, S> { it -> mapper(it) })
//    val tuples = extracting(*es)
//    return ListAssert<Tuple>(this.map {  })
//
//
//}
