# konfko
Reloadable configuration in kotlin

# Features
* can load configuration from the filesystem, classpath, environment, system properties, or it can be provided programmatically
* supports java .properties files, and optionally .json and .yaml
* can read an individial setting as a String, any Number, Boolean, any Enum, Duration, Period, Instant, UUID, URI, URL
* optionally can also convert to any type supported by jackson-module-kotlin, including custom data classes
* extensible: each list above can be extended without any changes to konfko code and often even to the client application. Added features should play nicely with both core and other added features
* Wherever possible, tries to preserve original order of settings. 
* Can load from several sources. where additional sources only provides settings missing in the previous sources
    * can be used to define default or override sources
    * a source can be defined optional, or be transformed immediately after load (before merging with other sources) 
* Supports a nested structure of settings
    * which can be defined either as nested objects or flat, separated by a period. 
    * Mix and match is possible, even in the same file
* Settings can be transformed
    * Settings can be filtered by keys, limited to a subtree, or prefixed
    * self similar: transformations of Settings can itself be treated as Settings
* Supports reloading of configuration changes
    * Two methods: periodic watching of lastModified timestamps, or with java 7 WatchService
    * Even when reloading is enabled, Settings instances are immutable
    * Applications can use updated settings directly or subscribe to configuration change events
* Supports kotlin property delegates, both for required and for optional properties

# Getting started
 
## How to obtain
### Gradle
```gradle
compile "com.github.konfko:konfko-core:$konfko_version"
```

### Maven
```xml
<dependency>
    <groupId>com.github.konfko</groupId>
    <artifactId>konfko-core</artifactId>
    <version>${konfko_version}</version>
</dependency>
```


## How to use
Say you have a file config.properties:
```properties
server.hostname=localhost
server.port=8080
server.connectionTimeout=PT30S
```
The simplest way to use konfko is to create the settings from given resources, and then directly access the individual settings.

```kotlin
val settings = SettingsMaker().make {
    path("config.properties")
}

val hostname: String = settings["server.hostname"]
val port: Int = settings["server.port"]
val connectionTimeout: Duration = settings.getIfPresent("server.connectionTimeout") ?: Duration.ofSeconds(30)
```

More complex creation:
```kotlin
val settings = SettingsMaker().make {
    // highest priority sources are defined first
    path(Paths.get("config.properties"))
    // Loading will not fail if an optional source fails to load (A warning is written to log, but a custom handler is possible)
    path("plugins.properties") optional true
    systemProperties()
    // settings from a single source can be transformed before they are merged with others
    environment() transform { it.prefixBy("env") }
    classpath("default-config.properties")
    // you can also provide values programatically
    provided(name = "provided", settings = mapOf("env.user.home" to "."))
}
```

konfko-core doesn't provide any custom parsers; by default, only .properties files are supported. However, it is simple to extend it with additional formats. 

For example, you can just include konfko-jackson module (for brevity, only Gradle is shown):
```gradle
compile "com.github.konfko:konfko-jackson:$konfko_version"
```

and you will able to also parse .json and .yaml files. As a bonus, you will now also be able to convert a setting to any type supported by jackson (thanks to jackson-module-kotlin):
```kotlin
// properties with default values are considered optional
data class ServerConf(val hostname: String, 
                      val port: Int, 
                      val connectionTimeout: Duration = Duration.ofSeconds(60))
                      
val settings = SettingsMaker().make {
    path("config.yml")
    classpath("default-config.json")
}

val conf: ServerConf = settings["server"]
```

# Watch for changes
To watch configuration for changes, you need to register a change watcher when creating settings.
```kotlin
val watcher = ScheduledConfigurationChangeWatcher(watchPeriod = Duration.ofSeconds(1))

val reloadable = SettingsMaker().makeAndWatch(watcher) {
    path("config.properties")
}
// Start watching configuration for changes.
watcher.start() 

// immutable snapshot of current settings.
val settings = reloadable.current

// returns the value of "server.hostname" at the time reloadable.current was called
val hostname: String = settings["server.hostname"]
// returns a mutable reference to this setting. hostnameSetting.value will change 
// whenever underlying configuration changes
val hostnameSetting: Setting<String> = settings.at("server.hostname")
// handle to an optional setting
val timeoutSetting: Setting<Duration?> = settings.at("server.connectionTimeout").optional
// Setting also acts as a delegate:
// val hostnameDelegate: String by settings.at("server.hostname")
// can also register change listeners, but reference to Setting must still be kept
val hostameListener: Setting<URL> = settings.at("servet.hostname")
        .onUpdate { println("hostname changed to $it") }
```
# Usage examples
There are several samples showing various features in the test sources of each package:
* konfko-core: konfko-core/src/test/kotlin/com/github/konfko/core/sample
* konfko-jackson: konfko-jackson/src/test/kotlin/com/github/konfko/jackson/sample
