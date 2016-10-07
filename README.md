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
    * self similar: each subtree in the Settings can itself be treated as Settings
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
    // Loading will not fail if an optional source fails to load (A warning is written to log instead)
    path("plugins.properties") optional true
    systemProperties()
    // all settings from a single source can be put under a prefix
    environment() under "env"
    classpath("default-config.properties")
    // you can also provide values programatically
    map(name = "hardcoded", properties = mapOf("env.user.home" to ".")) under "env"
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
