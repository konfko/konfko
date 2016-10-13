package com.github.konfko.core.watcher

/**
 * @author markopi
 */
interface ConfigurationChangeWatcher {
    fun register(settings: ReloadableSettings)
    fun unregister(settings: ReloadableSettings)
}

