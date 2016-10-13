package com.github.konfko.core.watcher

import com.github.konfko.core.source.SettingsSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * @author markopi
 */
class ScheduledConfigurationChangeWatcher(watchPeriod: Duration = Duration.ofSeconds(10)) : ConfigurationChangeWatcher, Closeable {
    private val LOG: Logger = LoggerFactory.getLogger(javaClass)

    private val internalScheduler: ScheduledExecutorService
    private val lock = ReentrantLock()
    @Volatile private var state = State.initialized
    private val watchPeriodMillis = watchPeriod.toMillis()

    private val reloadableSettingsToWatchedResources = ConcurrentHashMap<ReloadableSettings, List<WatchedResource>>()

    init {
        internalScheduler = Executors.newSingleThreadScheduledExecutor(ScheduledThreadFactory)
    }

    override fun register(settings: ReloadableSettings) {
        lock.withLock {
            if (reloadableSettingsToWatchedResources.containsKey(settings)) {
                LOG.info("Settings [$settings] are already registered")
                return
            }
            reloadableSettingsToWatchedResources.put(settings, settings.resources.map { WatchedResource(it, it.lastModified) })
        }
    }

    override fun unregister(settings: ReloadableSettings) {
        lock.withLock {
            val removed = reloadableSettingsToWatchedResources.remove(settings)
            if (removed == null) {
                LOG.info("Settings [$settings] were not registered")
                return
            }
        }
    }

    fun start() {
        lock.withLock {
            if (state != State.initialized) IllegalStateException("Illegal state: $state")
            internalScheduler.scheduleWithFixedDelay({ run() }, watchPeriodMillis, watchPeriodMillis, TimeUnit.MILLISECONDS)
            state = State.running
        }
    }

    fun stop() {
        lock.withLock {
            if (state != State.running) return
            internalScheduler.shutdownNow()
            state = State.closed
        }
    }


    override fun close() {
        stop()
    }


    private fun run() {
        for ((settings, watchedResources) in reloadableSettingsToWatchedResources) {
            val modifiedResources = checkResourcesForUpdates(watchedResources)
            if (!modifiedResources.isEmpty()) {
                LOG.debug("Detected changes to the following resources: ${modifiedResources.map { it.source.name }}")
                try {
                    settings.triggerReload()
                } catch(e: Exception) {
                    LOG.error("Error reloading settings [$settings]", e)
                }
            }
        }
    }

    private fun checkResourcesForUpdates(watchedResources: List<WatchedResource>): List<WatchedResource> {
        val modified = mutableListOf<WatchedResource>()
        watchedResources.forEach { res ->
            val newLastModified = res.source.lastModified
            if (newLastModified != null) {
                val lastModifiedNoticed = res.lastModifiedNoticed
                if (lastModifiedNoticed == null || lastModifiedNoticed < newLastModified) {
                    modified.add(res)
                    res.lastModifiedNoticed = newLastModified
                }
            }
        }
        return modified
    }

    private object ScheduledThreadFactory : ThreadFactory {
        val lastThreadIndex = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread = Thread(r).apply {
            isDaemon = true
            name = "ScheduledConfigurationChangeWatcher-${lastThreadIndex.incrementAndGet()}"
        }
    }

    private enum class State {
        initialized, running, closed
    }

    private class WatchedResource(val source: SettingsSource, var lastModifiedNoticed: Instant?)
}


