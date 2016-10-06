package com.github.konfko.core.watcher

import com.github.konfko.core.uncheckedCast
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class FileChangeEvent(val path: Path, val kind: WatchEvent.Kind<Path>)

class FilesystemWatcherService(
        val threadFactory: ThreadFactory = DefaultWatcherServiceThreadFactory,
        mergePeriod: Duration = Duration.ofMillis(100)) : Closeable {
    private val LOG = LoggerFactory.getLogger(javaClass)

    private val mutex = Object()
    private val mergePeriodMillis = mergePeriod.toMillis()
    @Volatile var state = State.uninitialized
        private set
    private val running = AtomicBoolean(false)
    lateinit private var thread: Thread
    private val listeners = CopyOnWriteArrayList<(FileChangeEvent) -> Unit>()


    lateinit private var watcher: WatchService

    private val rootsToAddOnStart = CopyOnWriteArrayList<Path>()
    private val watchRoots = ConcurrentHashMap<Path, WatchKeyConfig>()
    private val watchKeys = ConcurrentHashMap<WatchKey, WatchKeyConfig>()

    private fun assertState(vararg expected: State) {
        val actual = state
        if (!expected.contains(actual)) throw IllegalStateException("Invalid service state: $actual, expected: $expected")
    }

    fun start() {
        synchronized(mutex) {
            assertState(State.uninitialized)
            state = State.initializing

            this.watcher = FileSystems.getDefault().newWatchService()

            try {
                rootsToAddOnStart.forEach { register(it) }
                rootsToAddOnStart.clear()

                thread = threadFactory.newThread { this.run() }
                running.set(true)
                thread.start()
            } catch(e: Exception) {
                state = State.failed
                watcher.close()
                throw e
            }
        }
    }

    fun stop() {
        synchronized(mutex) {
            if (state != State.running && state != State.initializing) return
            state = State.closing
            running.set(false)
            thread.interrupt()
        }
        awaitTermination()
    }

    override fun close() = stop()

    private fun awaitTermination() {
        val state = this.state
        when (state) {
            State.closing -> thread.join(10000)
            else -> Unit
        }
        if (thread.isAlive) {
            LOG.error("WatcherService worker thread did not properly shut down in under 10 seconds. Some resources may remain unreleased")
        }
    }


    fun addListener(listener: (FileChangeEvent) -> Unit) {
        listeners.add(listener)
    }


    fun addListeners(vararg listeners: (FileChangeEvent) -> Unit) {
        this.listeners.addAll(Arrays.asList(*listeners))
    }

    fun removeListener(listener: (FileChangeEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun removeListeners(vararg listeners: (FileChangeEvent) -> Unit) {
        this.listeners.removeAll(Arrays.asList(*listeners))
    }

    fun register(dir: Path) {
        synchronized(mutex) {
            assertState(State.running, State.uninitialized, State.initializing)
            if (state == State.uninitialized) {
                rootsToAddOnStart.add(dir)
                return
            }

            val existingConfig = watchRoots[dir]
            if (existingConfig != null) {
                existingConfig.count++
                return
            }
            val key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
            LOG.info("Watching directory for changes: {}", dir.toAbsolutePath())
            val config = WatchKeyConfig(key, dir)
            watchKeys.put(key, WatchKeyConfig(key, dir))
            watchRoots.put(dir, config)

        }
    }

    fun unregister(dir: Path) {
        synchronized(mutex) {
            assertState(State.running, State.uninitialized, State.initializing)
            if (state == State.uninitialized) {
                if (!rootsToAddOnStart.remove(dir)) throw IllegalStateException("Path [$dir] is not registered")
                return
            }

            val config = watchRoots[dir] ?: throw IllegalStateException("Path [$dir] is not registered")
            config.count--
            if (config.count <= 0) {
                watchRoots.remove(dir)
                watchKeys.remove(config.key)
                config.key.cancel()
            }
        }
    }

    private fun run() {
        try {
            assertState(State.initializing)
            state = State.running
            try {
                while (running.get()) {
                    val key = watcher.take()
                    val watchKeyConfig = watchKeys[key] ?: continue

                    val events = pollEvents(key)
                    processPolledEvents(watchKeyConfig, events)

                    // reset key and remove from set if directory no longer accessible
                    val valid = key.reset()
                    if (!valid) {
                        LOG.info("Unregistering directory {}", watchKeyConfig.path)
                        synchronized(mutex) {
                            val config = watchKeys.remove(key)
                            if (config != null) watchRoots.remove(config.path)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                // closing
            } finally {
                watcher.close()
            }
            state = State.closed
            LOG.info("Disk file change watcher has stopped")
        } catch (e: Throwable) {
            state = State.failed
            LOG.error("Unexpected exception in watcher service. The watcher has stopped.", e)
        }
    }


    private fun processPolledEvents(watchKeyConfig: WatchKeyConfig, events: Collection<WatchEvent<Path>>) {
        val dir = watchKeyConfig.path
        for (event in events) {
            val kind = event.kind()
            val ev = cast<Path>(event)
            val path = dir.resolve(ev.context())

            LOG.trace("Detected file change: [{}] {}", kind, path)
            notifyListeners(path, kind)
        }
    }

    private fun notifyListeners(path: Path, kind: WatchEvent.Kind<Path>) {
        val event = FileChangeEvent(path, kind)
        for (listener in listeners) {
            try {
                listener(event)
                // avoid catching Throwable
            } catch (e: Exception) {
                LOG.error("Error invoking file change listener " + listener, e)
            } catch (e: Error) {
                LOG.error("Error invoking file change listener " + listener, e)
            }
        }
    }

    private fun pollEvents(key: WatchKey): Collection<WatchEvent<Path>> {
        val events: Collection<WatchEvent<Path>>
        if (mergePeriodMillis > 0) {
            Thread.sleep(mergePeriodMillis)
            val mergedEvents = LinkedHashMap<Path, WatchEvent<Path>>()
            val polledEvents: Collection<WatchEvent<Path>> = key.pollEvents().uncheckedCast()
            polledEvents.forEach { e -> mergedEvents.put(e.context(), e) }
            events = mergedEvents.values
        } else {
            events = key.pollEvents().uncheckedCast()
        }
        return events
    }

    enum class State {
        uninitialized, initializing, running, failed, closing, closed
    }

    private fun <T> cast(event: WatchEvent<*>): WatchEvent<T> {
        @Suppress("UNCHECKED_CAST")
        return event as WatchEvent<T>
    }

    private data class WatchKeyConfig(val key: WatchKey, val path: Path, var count: Int = 1)
}

internal object DefaultWatcherServiceThreadFactory : ThreadFactory {
    private val nextIndex = AtomicInteger(1)

    override fun newThread(r: Runnable): Thread =
            Thread(r).apply {
                name = "FilesystemWatcherService-${nextIndex.getAndAdd(1)}"
                isDaemon = true
            }
}