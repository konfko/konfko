@file:Suppress("UNUSED_VARIABLE")

// Variables are used to provide examples

package com.github.konfko.core.sample

import com.github.konfko.core.*
import com.github.konfko.core.source.SettingsMaker
import com.github.konfko.core.watcher.NioConfigurationChangeWatcher
import java.net.URI
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * @author markopi
 */

fun main(args: Array<String>) {
    val scheduler = Executors.newScheduledThreadPool(1) // just to periodically print current configuration

    withTempDir { dir ->
        val conf = dir.resolve("sample.properties")
        conf.write(confProperties)

        val watcher = NioConfigurationChangeWatcher()

        val reloadableSettings = SettingsMaker().makeAndWatch(watcher) {
            path(conf)
            systemProperties() under "system" // prefix all system properties with "system"
        }
        // once obtained, settings will never change. For a new version, you must call ReloadableSettings.current again
        val settings = reloadableSettings.current


        // listens to changes to java version (if removed from samples.properties it will revert to system properties)
        val javaVersion = settings.at<String>("system.java.version").onUpdate { log("Java version changed to $it") }

        // directly retrieve values from settings
        retrieveSettingDirectly(settings)

        val service = createConfigurableService(settings)

        // starts printing service settings every 5 seconds
        scheduler.scheduleServiceConfigurationPrinter(service)

        // watcher can be started immediately or at a later time, i.e. when the rest of application has been initialized
        watcher.start()

        println()
        log("Started to watch for configuration changes in file ${conf.toAbsolutePath()}")
        log("Current configuration will be printed out every 5 seconds. You can make changes to file ${conf.toAbsolutePath()} and they will be visible in ConfigurableService")


        println()
        log("Press <enter> to stop the sample")
        println()
        readLine()

        // Unregisters itself from watcher. Needed only if reloadable's lifetime is much shorter than watcher's
        // Otherwise it will be closed as part of watcher's cleanup
        reloadableSettings.close()

        watcher.close()
    }
    scheduler.shutdown()
}

private fun retrieveSettingDirectly(settings: Settings) {
    val startingUrlString: String = settings["httpClient.url"]
    // value is converted on retrieval
    val startingUrl: URI = settings["httpClient.url"]
    log("Starting url: $startingUrl")
}

private fun createConfigurableService(settings: Settings): ConfigurableService {
    val httpClientSettings = settings.subSettings("httpClient") // alternative: settings / "httpClient"
    val service = ConfigurableService(
            // can refer to full path from root settings
            settings.at("httpClient.url"),
            // can also use a subSettings view, identical to settings.at("httpClient.connectionTimeout")
            httpClientSettings.at("connectionTimeout"),
            httpClientSettings.at("keyStore"))
    return service
}

// periodically prints current configuration
private fun ScheduledExecutorService.scheduleServiceConfigurationPrinter(service: ConfigurableService) {
    scheduleAtFixedRate({
        try {
            log(service.toString())
        } catch(e: Exception) {
            log("Error reading settings")
            e.printStackTrace()
        }
    }, 5L, 5L, TimeUnit.SECONDS)
}

// A demo class that shows how to configure services with reloadable configuration.
// If you use jackson module, it is preferable to have a single Setting with custom conf data object.
// Look at JacksonSettingsSample in jackson module for an example
class ConfigurableService(url: Setting<URI>,
                          connectionTimeout: Setting<Duration>,
                          keyStore: Setting<String>) {
    // by delegation (property type annotations are optional, here they are just for clarity)
    val url: URI by url
    // nullable properties must use delegate to Setting.optional
    val connectionTimeout: Duration? by connectionTimeout.optional

    /* If a change fo settings requires some action by the service, you can register a listener on the setting
     * There are several types of listeners, look at Setting documentation
     *
     * IMPORTANT: For listeners to work, the service must still keep a reference to the Setting, as that listens to
     * change events only as a WeakReference (in order to avoid memory leaks)

     * setting.value will not be updated unless all registered listeners have successfully executed
     */
    val keyStore: String by keyStore.onRefresh { changeKeystore(it.old, it.new) }

    init {
        // init keystore
        log("Initializing keystore to ${keyStore.value}")
    }

    private fun changeKeystore(old: String?, new: String?) = log("Changing service keystore from [$old] to [$new]")

    override fun toString(): String = "ConfigurableService(url=$url, connectionTimeout=$connectionTimeout)"
}


fun log(message: String) = println("${LocalTime.now()} $message")

val confProperties = """
httpClient.url=http://localhost:9090
httpClient.keyStore=/path/to/keystore
httpClient.connectionTimeout=PT20S

# if only
system.java.version=kotlin
# system.file.encoding=UNKNOWN
""".trim()