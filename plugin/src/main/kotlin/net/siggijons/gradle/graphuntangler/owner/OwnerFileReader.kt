package net.siggijons.gradle.graphuntangler.owner

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.InputStream

class OwnerFileReader : OwnersReader {

    override fun read(file: File): Owners {
        return file.inputStream().use {
            read(it)
        }
    }

    fun read(string: String): Owners {
        return string.byteInputStream().use { read(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun read(inputStream: InputStream): Owners {
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
        }.toMap().let { Owners(ownerMap = it) }
    }
}