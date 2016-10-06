package com.github.konfko.core.source

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * @author markopi
 */

/**
 * Provides input streams for [ResourceSettingsSource]
 */
interface SettingsResource {
    /**
     * If this resource represents an existing file on the filesystem, this value should point to it.
     * Read by [FilesystemWatcherService][com.github.konfko.core.watcher.FilesystemWatcherService] to watch
     * for configuration changes.
     *
     * Otherwise, it should be null.
     */
    val path: Path?
    /**
     * When was this resource last modified. If modification is not supported, it should return null
     */
    val lastModified: Instant?
    /**
     * Name of the resource. DefaultSettingsParser tries to extract an extension from this name to determine how to
     * parse the resource.
     */
    val name: String

    /**
     * Opens a new input stream for this resource.
     */
    fun openInputStream(): InputStream

    /**
     * Opens a new reader for this resource. Normally this is just a wrapper around [openInputStream]
     */
    fun openReader(charset: Charset = StandardCharsets.UTF_8): Reader = InputStreamReader(openInputStream(), charset)
}

/**
 * A resource pointing to a particular file on the filesystem
 */
class PathResource(override val path: Path) : SettingsResource {
    override val name = path.toString()
    override val lastModified: Instant? = Files.getLastModifiedTime(path)?.toInstant()

    override fun openInputStream(): InputStream = Files.newInputStream(path)

    override fun toString(): String = "${javaClass.simpleName}[$name]"
}

/**
 * A resource pointing to a particular classpath resource
 */
class ClassPathResource(private val classPath: String,
                        private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader()) :
        SettingsResource {
    override val name = classPath
    override val path: Path? = null
    override val lastModified: Instant? = null

    override fun openInputStream(): InputStream = classLoader.getResourceAsStream(classPath)
            ?: throw IOException("No such classpath resource: [$classPath]")

    override fun toString(): String = "${javaClass.simpleName}[$name]"
}