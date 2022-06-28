package io.github.patxibocos.pcsscraper.document

import java.io.File
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText

class Cache(private val path: Path) {
    fun get(key: String): String? {
        val file = path.resolve(key)
        if (file.exists()) {
            return file.readText()
        }
        return null
    }

    fun put(keyToContent: Pair<String, String>) {
        val (key, content) = keyToContent
        val file = File(path.resolve(key).toUri())
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    fun delete(key: String) {
        val file = path.resolve(key)
        file.deleteIfExists()
    }
}
