package com.github.konfko.core.watcher

import com.github.konfko.core.withTempDir
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * @author markopi
 */
class FilesystemWatcherServiceTest {
    @Before
    fun checkExclusion() {
        Assume.assumeTrue("Watcher tests were excluded",
                System.getProperty("gradle.exclude.watcher.tests")?.toLowerCase() != "true")
    }

    @Test
    fun startAndStop() {
        Files.write(Paths.get("d:/temp/props.txt"), System.getProperties().map { "${it.key}: ${it.value}" })
        withWatcher { }
    }

    @Test
    fun detectSingleChange() {
        withWatcher {
            val updateFile = root.resolve("newFile.txt")
            write(updateFile, "fileContents")
            assertNextEvent(updateFile, "fileContents")
        }
    }

    @Test
    fun detectMultipleChangesToSameFile() {
        withWatcher {
            val updateFile = root.resolve("newFile.txt")

            write(updateFile, "first")
            assertNextEvent(updateFile, "first")

            write(updateFile, "second")
            assertNextEvent(updateFile, "second")
        }
    }

    @Test
    fun detectChangesToMultipleFiles() {
        withWatcher {
            val firstFile = root.resolve("first.txt")
            val secondFile = root.resolve("second.txt")

            write(firstFile, "first")
            assertNextEvent(firstFile, "first")

            write(secondFile, "second")
            assertNextEvent(secondFile, "second")
        }
    }

    @Test
    fun detectCreationAndDeletionOfFile() {
        withWatcher {
            val file = root.resolve("first.txt")
            write(file, "first")
            assertNextEvent(file, "first")

            Files.delete(file)
            assertNextEvent(file).let { assertThat(it.kind == StandardWatchEventKinds.ENTRY_DELETE) }
        }
    }


    @Test
    fun createAndRemoveListeners() {
        withWatcher() {
            val file = root.resolve("file.txt")

            val others = LinkedBlockingDeque<FileChangeEvent>()
            val otherListener: (FileChangeEvent) -> Unit = { event -> others.add(event) }

            // added listener will receive events
            watcher.addListener(otherListener)

            write(file, "contents")
            assertNextEvent(file, "contents") // the first listener must still receive events
            assertThat(others.poll(100, TimeUnit.MILLISECONDS).path).isEqualTo(file)

            // when listener is removed, it lo longer receives events
            watcher.removeListener(otherListener)

            write(file, "otherContents")
            assertNextEvent(file, "otherContents")
            assertThat(others).isEmpty()
        }
    }


    @Test
    fun registerMultipleRoots() {
        withWatcher() {
            withTempDir { otherRoot ->

                watcher.register(otherRoot)

                val file = root.resolve("file.txt")
                val otherFile = otherRoot.resolve("otherFile.txt")

                write(file, "file")
                assertNextEvent(file, "file")

                write(otherFile, "otherFile")
                assertNextEvent(otherFile, "otherFile")

                watcher.unregister(otherRoot)

                val newOtherFile = otherRoot.resolve("newOtherFile.txt")
                write(newOtherFile, "newOtherFile")
                // will no longer trigger the event as the root was unregistered
                assertThat(events.poll(100, TimeUnit.MILLISECONDS)).isNull()

            }
        }
    }

    @Test
    fun registerShouldMaintainStackStyleBehavior() {
        withWatcher() {
            // same root should not be registered again
            watcher.register(root)
            val file = root.resolve("file.txt")

            write(file, "file")
            assertNextEvent(file, "file")
            // event was only triggered once
            assertThat(events.poll(100, TimeUnit.MILLISECONDS)).isNull()

            watcher.unregister(root)
            // registered twice, unregistered once. Should still be registered
            write(file, "file2")
            assertNextEvent(file, "file2")

            watcher.unregister(root)
            // unregistered twice, no more events
            write(file, "file3")
            assertThat(events.poll(100, TimeUnit.MILLISECONDS)).isNull()
        }
    }

    class WithWatcher(val root: Path, val watcher: FilesystemWatcherService) {
        val events = LinkedBlockingDeque<FileChangeEvent>()

        init {
            watcher.addListener {
                LOG.debug("Received change notification: $it")
                events.add(it)
            }
        }

        fun assertNextEvent(expectedPath: Path,
                            expectedContents: String? = null,
                            timeout: Long = 1000L,
                            timeUnit: TimeUnit = TimeUnit.MILLISECONDS): FileChangeEvent {
            val event = events.poll(timeout, timeUnit)
                    ?: throw AssertionError("Did not receive change event within expected window ($timeout $timeUnit)")
            assertThat(event.path).isEqualTo(expectedPath)
            if (expectedContents != null) {
                assertThat(read(expectedPath)).withFailMessage("File contents do not match").isEqualTo(expectedContents)
            }
            return event
        }
    }

    inline fun <R> withWatcher(threadFactory: ThreadFactory = Executors.defaultThreadFactory(),
                               mergePeriod: Duration = Duration.ofMillis(100),
                               block: WithWatcher.() -> R): R =
            withTempDir { root ->
                val watcher = FilesystemWatcherService(threadFactory, mergePeriod)
                watcher.start()
                watcher.register(root)
                return try {
                    val withWatcher = WithWatcher(root, watcher)
                    withWatcher.block()
                } finally {
                    watcher.stop()
                }
            }

    companion object {
        val LOG = LoggerFactory.getLogger(FilesystemWatcherServiceTest::class.java)!!

        fun write(path: Path, content: String): Path = Files.write(path, content.toByteArray())
        fun read(path: Path): String = Files.readAllBytes(path).toString(Charsets.UTF_8)
    }
}
