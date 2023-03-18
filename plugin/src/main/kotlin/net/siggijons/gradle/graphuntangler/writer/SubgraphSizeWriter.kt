package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.model.IsolatedSubgraphDetails
import java.io.File

class SubgraphSizeWriter(
    private val file: File
) {
    fun write(
        graphs: List<IsolatedSubgraphDetails>
    ) {
        with(file.printWriter()) {
            println("vertex,isolatedDagSize,fullGraphSize\n")
            graphs.forEach {
                println("${it.vertex.project},${it.isolatedDagSize},${it.fullGraphSize}\n")
            }
            flush()
        }
    }
}
