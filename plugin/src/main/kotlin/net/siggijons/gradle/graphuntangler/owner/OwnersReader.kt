package net.siggijons.gradle.graphuntangler.owner

import java.io.File

interface OwnersReader {
    fun read(file: File): Owners
}
