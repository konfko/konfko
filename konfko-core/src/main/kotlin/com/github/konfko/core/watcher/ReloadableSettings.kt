package com.github.konfko.core.watcher

import com.github.konfko.core.*
import com.github.konfko.core.source.SettingsSource
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.*

/**
 * @author markopi
 */
class ReloadableSettings internal constructor(
        val resources: List<SettingsSource>,
        private val loadSettings: (List<SettingsSource>) -> Settings,
        private val watcher: ConfigurationChangeWatcher) :
        Closeable {
    private val LOG = LoggerFactory.getLogger(javaClass)

    // Should only be accessed through current (except in constructor)
    // first is current static settings, second is a delegate that delegate most calls to first,
    // except creation of Setting, which it delegates to this ReloadableSettings
    @Volatile private var snapshot: Pair<Settings, ReloadableSettingSettings>

    var current: Settings
        get() = snapshot.second
        private set(value) {
            snapshot = createSnapshot(value)
        }


    /* Abuse of WeakHashMap to maintain a collection of weak references to property change listeners.
     * Listener is stored as key, value is property prefix on which the listener is registered.
     * This means that order of listeners is not preserved. Do we need something like a weak list?
     */
    private val listeners = WeakHashMap<(Settings) -> Unit, String?>()

    init {
        val new = loadSettings(resources)
        snapshot = createSnapshot(new)

        watcher.register(this)
    }

    private fun createSnapshot(new: Settings) = Pair(new, ReloadableSettingSettings(new, this))

    fun triggerReload() {
        val old = current
        val new = loadSettings(resources)

        val updatedProperties = findUpdatedSettingKeys(old, new)
        if (updatedProperties.isEmpty()) {
            LOG.info("Triggered configuration reload, but no properties were updated. No listeners will be notified")
            return
        }
        current = new

        LOG.info("Detected changes to configuration properties: $updatedProperties. Notifying listeners.")
        notifyListeners(new, updatedProperties)
    }


    internal fun registerWeakListener(prefix: String? = null, listener: (Settings) -> Unit): (Settings) -> Unit {
        synchronized(listeners) {
            listeners.put(listener, prefix)
        }
        return listener
    }

    internal fun unregisterWeakListener(listener: (Settings) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners(settings: Settings, updatedProperties: Set<String>) {

        val sortedUpdates = TreeSet(updatedProperties)
        val listenersToNotify = synchronized(listeners) {
            listeners.entries
                    .filter {
                        shouldBeNotified(it.value, sortedUpdates).apply {
                            LOG.info("Should listener ${it.value} be notified: $this")
                        }
                    }
        }

        listenersToNotify.forEach { entry ->
            try {
                val prefix = entry.value
                val sub = if (prefix == null) settings else settings.subSettings(prefix)

                entry.key.invoke(sub)
            } catch(e: Exception) {
                LOG.error("Error reloading settings with prefix {${entry.value ?: ""}}, with listener: ${entry.key}", e)
            }
        }
    }

    internal fun <T : Any> typedSetting(key: String, type: Class<T>): Setting<T> = ReloadableSetting(this, key, type)

    override fun close() {
        watcher.unregister(this)
    }
}

// delegates all retrievals to static settings (such as MapSettings), except setting function,
// which is delegated to reloadable settings
private class ReloadableSettingSettings(val static: Settings, val reloadable: ReloadableSettings) : Settings by static {
    override fun <T : Any> atTyped(key: String, type: Class<T>): Setting<T> = reloadable.typedSetting(key, type)
}

// visible for testing
internal fun findUpdatedSettingKeys(old: Settings, current: Settings): Set<String> {
    val oldMap = old.toFlatMap()
    val newMap = current.toFlatMap()

    val updatedKeys = mutableSetOf<String>()
    // add all keys in old map not present or modified in second map
    oldMap.entries.forEach { entry -> if (newMap[entry.key] != entry.value) updatedKeys.add(entry.key) }
    // add all keys in second map not present in first map
    newMap.entries.forEach { entry -> if (!oldMap.containsKey(entry.key)) updatedKeys.add(entry.key) }
    return updatedKeys
}

// visible for testing
internal fun shouldBeNotified(prefix: String?, sortedUpdates: TreeSet<String>): Boolean {
    if (prefix == null) return true
    if (sortedUpdates.contains(prefix)) return true
    val fix = prefix + "."
    return sortedUpdates.ceiling(fix)?.let { it.startsWith(fix) } ?: false
}

private class ReloadableSetting<T : Any>(settings: ReloadableSettings, val key: String, val type: Class<T>) : AbstractSetting<T>() {
    // Current value is held here, abusing Pair to serve as Either
    @Volatile private var currentValue: Pair<T?, SettingsException?>

    override val value: T
        get() = valueOrNull ?: throw NoSuchSettingException(key)

    override val valueOrNull: T?
        get() {
            val (value, error) = currentValue
            return if (error != null) throw error else value
        }

    private val updateListener: (Settings) -> Unit

    init {
        // register a weak listener on reloadable and keep a reference to avoid GC
        updateListener = settings.registerWeakListener(key, { update(it) })

        currentValue = try {
            Pair(settings.current.getTypedIfPresent(key, type), null)
        } catch(e: SettingsException) {
            Pair(null, e)
        }
    }

    private fun update(s: Settings) {
        val oldValue = currentValue.first
        val newValue = when {
        // to support Setting<Settings> we cast it directly
            type.isAssignableFrom(s.javaClass) -> type.cast(s)
            else -> s.getTypedIfPresent("", type)
        }
        currentValue = Pair(newValue, null)
        notifyListeners(SettingUpdate(old = oldValue, new = newValue))
    }
}


