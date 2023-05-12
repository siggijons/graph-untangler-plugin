package net.siggijons.gradle.graphuntangler.owner

import java.io.File
import java.io.InputStream

class GitHubCodeOwnersReader : OwnersReader {

    private val blank = "/\\s\\s+/".toRegex()

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
        return inputStream.reader().readLines().mapNotNull { line ->
            if (line.startsWith("#") || line.isBlank()) {
                // ignore comments and blank lines
                null
            } else if (!line.startsWith("/")) {
                // only attempt to parse rooted owners
                null
            } else {
                moduleOwnerPair(line)
            }
        }.toMap().let { Owners(ownerMap = it) }
    }

    private fun moduleOwnerPair(
        line: String
    ): Pair<String, String>? {
        val parts = line.replace(blank, " ").split(" ")
        return if (parts.size < 2 || parts[0].startsWith("*")) {
            null
        } else {
            val module = parts[0].split("*")[0]
                .replace("/", ":")
                .dropLastWhile { it == ':' }
            val firstOwner = parts[1]
            module to firstOwner
        }
    }
}