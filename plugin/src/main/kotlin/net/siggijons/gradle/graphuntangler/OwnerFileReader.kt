package net.siggijons.gradle.graphuntangler

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.InputStream

class OwnerFileReader {

    fun read(string: String): Map<String, String> {
        return string.byteInputStream().use { read(it) }
    }

    fun read(file: File): Map<String, String> {
        return file.inputStream().use {
            read(it)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun read(inputStream: InputStream): Map<String, String> {
        val yaml = Yaml()
        val data: Map<String, Map<Any, Any>> = yaml.load(inputStream)
        return data.flatMap { (id, map) ->
            val team = checkNotNull(map["team"] as? String) {
                "no team found for $id"
            }
            val modules = checkNotNull(map["modules"] as? List<String>) {
                "no modules found for $id"
            }
            modules.map { it to team }
        }.toMap()
    }
}