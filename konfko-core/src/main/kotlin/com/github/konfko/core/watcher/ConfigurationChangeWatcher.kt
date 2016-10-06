package com.github.konfko.core.watcher

import com.github.konfko.core.removeInPlace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * @author markopi
 */
interface ConfigurationChangeWatcher {
    fun register(settings: ReloadableSettings)
    fun unregister(settings: ReloadableSettings)
}

class NioConfigurationChangeWatcher() : ConfigurationChangeWatcher, Closeable {
    private val LOG: Logger = LoggerFactory.getLogger(javaClass)

    private val watcher = FilesystemWatcherService()
    private val lock = ReentrantReadWriteLock()
    private val reloadableSettingsToWatchedPaths = mutableMapOf<ReloadableSettings, List<Path>>()
    private val watchedPathsToReloadableSettings = mutableMapOf<Path, MutableList<ReloadableSettings>>()

    private val listener = { event: FileChangeEvent -> notify(event) }

    fun start() {
        watcher.start()
        watcher.addListener(listener)
    }

    fun stop() {
        watcher.removeListener(listener)
        watcher.stop()
    }

    override fun close() {
        stop()
        watcher.close()
    }


    private fun notify(event: FileChangeEvent) {
        val settingsToReload = lock.read {
            val settingsWithResource = watchedPathsToReloadableSettings[event.path]
            settingsWithResource?.toList() ?: emptyList()
        }

        settingsToReload.forEach { settings ->
            try {
                settings.triggerReload()
            } catch(e: Exception) {
                LOG.error("Error reloading settings [$settings]", e)
            }
        }
    }

    override fun register(settings: ReloadableSettings) {
        val settingsPaths = settings.resources
                .map { it.backingResource?.path }
                .filterNotNull()
                .filter {
                    if (!Files.isDirectory(it.parent)) {
                        LOG.warn("Path [$it] cannot be registered for changes, since [${it.parent}] is not an existing directory")
                        false
                    } else true
                }

        if (settingsPaths.isEmpty()) {
            LOG.warn("Will not watch changes for settings [$settings] as it has no watchable path resources")
            return
        }
        lock.write {
            if (reloadableSettingsToWatchedPaths.containsKey(settings)) {
                LOG.info("Settings [$settings] are already registered")
                return
            }
            reloadableSettingsToWatchedPaths.put(settings, settingsPaths)
            settingsPaths.forEach { path ->
                watchedPathsToReloadableSettings.getOrPut(path, { mutableListOf() }).add(settings)
            }
            settingsPaths.forEach { path -> watcher.register(path.parent) }
        }
    }

    override fun unregister(settings: ReloadableSettings) {
        lock.write {
            val paths = reloadableSettingsToWatchedPaths.remove(settings)
            if (paths == null) {
                LOG.info("Settings [$settings] are not registered")
                return
            }
            paths.forEach { path -> watchedPathsToReloadableSettings[path]?.remove(settings) }
            paths.forEach { path -> watcher.unregister(path.parent) }

            watchedPathsToReloadableSettings.removeInPlace { it.value.isEmpty() }
        }
    }
}
