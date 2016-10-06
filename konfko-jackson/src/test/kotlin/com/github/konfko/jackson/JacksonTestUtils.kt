package com.github.konfko.jackson

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

/**
 * @author markopi
 */
inline fun <R> withTempDir(block: (Path) -> R): R {
    val tempDir = Files.createTempDirectory("konfko")
    try {
        return block.invoke(tempDir)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}


fun Path.write(content: String, charset: Charset = Charsets.UTF_8) {
    Files.write(this, content.toByteArray(charset))
}