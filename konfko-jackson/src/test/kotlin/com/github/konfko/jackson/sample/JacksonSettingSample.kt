package com.github.konfko.jackson.sample

import com.github.konfko.core.Setting
import com.github.konfko.core.Settings
import com.github.konfko.core.derived.prefixBy
import com.github.konfko.core.source.SettingsMaker
import com.github.konfko.core.watcher.NioConfigurationChangeWatcher
import com.github.konfko.jackson.withTempDir
import com.github.konfko.jackson.write
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
    val scheduler = Executors.newScheduledThreadPool(1)

    withTempDir { dir ->
        val conf = dir.resolve("sample.yaml")
        conf.write(confYaml)

        val watcher = NioConfigurationChangeWatcher()

        // Jackson parser is automatically found and registered when jackson module is in classpath
        // No need to specify it here, either for file parsing or for type conversion
        val reloadableSettings = SettingsMaker().makeAndWatch(watcher) {
            path(conf)
            systemProperties().transform { it.prefixBy("system") } // prefix all system settings with "system"
        }
        // once obtained, settings will never change. For an updated version, you must call ReloadableSettings.current again
        val settings = reloadableSettings.current

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
        //reloadableSettings.close()

        watcher.close()
    }
    scheduler.shutdown()
}

private fun createConfigurableService(settings: Settings): ConfigurableService {
    val service = ConfigurableService(
            // Use jackson parser to convert to settings classes
            settings.at("httpClient")
    )
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


// A demo class that shows how to configure services with settings class configuration
// If you use jackson module, it is preferable to have a single Setting with custom conf settings object
class ConfigurableService(configuration: Setting<Configuration>) {
    val conf by configuration.onUpdate { new -> reload(new) }


    fun init() {
        // typically, in dependency injection, initialization is separate from instantiation. This method could
        // perhaps be annotated by @PostConstruct and called by Spring
        log("Configuration initialized to $conf")
    }

    private fun reload(conf: Configuration) = log("Configuration changed to $conf")

    override fun toString(): String = with(conf) {
        "ConfigurableService(url=$url, connectionTimeout=$connectionTimeout, keystore=$keyStore)"
    }


    data class Configuration(val url: URI, val connectionTimeout: Duration, val keyStore: String)
}


fun log(message: String) = println("${LocalTime.now()} $message")

val confYaml = """
httpClient:
    url: http://localhost:9090
    keyStore: /path/to/keystore
# can mix and match nested and absolute keys
httpClient.connectionTimeout: PT20S
""".trim()