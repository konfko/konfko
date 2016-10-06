package com.github.konfko.core.watcher

import com.github.konfko.core.Settings
import com.github.konfko.core.source.SettingsMaker
import com.github.konfko.core.withTempDir
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * @author markopi
 */
class NioConfigurationChangeWatcherTest {
    @Before
    fun checkExclusion() {
        Assume.assumeTrue("Watcher tests were excluded",
                System.getProperty("gradle.exclude.watcher.tests")?.toLowerCase() != "true")
    }

    @Test
    fun `listeners get notified of changes`() {
        withTempDir { tempDir ->
            val confFile: Path = tempDir.resolve("conf.properties")
            writeConf(confFile, PROPERTIES_1)


            val watcher = NioConfigurationChangeWatcher()
            watcher.start()

            val settings = SettingsMaker().makeAndWatch(watcher) { path(confFile) }

            val events = LinkedBlockingDeque<Settings>()
            val changeListener = settings.registerWeakListener { settings ->
                events.add(settings)
            }
            // listener should not be executed immediately
            assertThat(events.isEmpty())

            // should receive change event when configuration is first rewritten
            writeConf(confFile, PROPERTIES_2)
            with(events.poll(200, TimeUnit.MILLISECONDS)) {
                assertHasSetting("dataSource.first.username", "firstUser_Updated")
                assertHasNoSetting("dataSource.first.password")
            }
            assertThat(events.isEmpty())

            // should receive another event when file is rewritten back
            writeConf(confFile, PROPERTIES_1)
            with(events.poll(200, TimeUnit.MILLISECONDS)) {
                assertHasSetting("dataSource.first.password", "firstPassword")
                assertHasSetting("dataSource.first.username", "firstUser")
            }
            assertThat(events.isEmpty())

            // listener should no longer receive events once it is unregistered
            settings.unregisterWeakListener(changeListener)
            writeConf(confFile, PROPERTIES_2)
            assertThat(events.poll(200, TimeUnit.MILLISECONDS)).isNull()

            watcher.close()
        }
    }

    private fun writeConf(confFile: Path, content: String) {
        Files.write(confFile, content.toByteArray(StandardCharsets.UTF_8))
    }


    companion object {
        private val PROPERTIES_1 = """
dataSource.first.url=jdbc:test:localhost/first
dataSource.first.username=firstUser
dataSource.first.password=firstPassword
""".trim()
        private val PROPERTIES_2 = """
dataSource.first.url=jdbc:test:localhost/first
dataSource.first.username=firstUser_Updated
""".trim()


        fun Settings?.assertHasNoSetting(key: String) {
            if (this == null) {
                throw AssertionError("Setting change was not triggered")
            }
            val actual = getTypedIfPresent(key, Any::class.java)
            assertThat(actual).withFailMessage("setting key [$key] should not be present, but actual: [$actual]").isNull()
        }

        inline fun <reified T : Any> Settings?.assertHasSetting(key: String, expected: T) {
            if (this == null) {
                throw AssertionError("Setting change was not triggered")
            }

            val actual = getTypedIfPresent(key, T::class.java)
            assertThat(actual).withFailMessage("setting [$key] should have value [$expected], but found: [$actual]").isEqualTo(expected)
        }
    }
}


