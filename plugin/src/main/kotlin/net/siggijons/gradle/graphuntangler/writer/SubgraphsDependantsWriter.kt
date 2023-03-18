package net.siggijons.gradle.graphuntangler.writer

import net.siggijons.gradle.graphuntangler.model.DependencyNode
import net.siggijons.gradle.graphuntangler.model.SubgraphDetails
import java.io.File

class SubgraphsDependantsWriter(
    private val outputDir: File
) {

    fun write(
        graphs: List<SubgraphDetails>,
    ) {
        graphs.forEach { subgraph ->
            writeDescendantsCounts(
                vertex = subgraph.vertex,
                descendants = subgraph.descendants,
            )
        }
    }

    /**
     * Write csv files with stats about the ownership of descendants.
     */
    private fun writeDescendantsCounts(
        vertex: DependencyNode,
        descendants: Set<DependencyNode>,
    ) {
        val descendantsMap = descendants.groupBy(
            keySelector = { it.owner },
            valueTransform = { it.project }
        )

        with(
            File(outputDir, "${vertex.safeFileName}-descendants-owners-count.csv").printWriter()
        ) {
            println("owner,modules")
            descendantsMap.forEach { (owner, projects) ->
                println("$owner,${projects.size}")
            }
            flush()
        }

        with(
            File(outputDir, "${vertex.safeFileName}-descendants-owners.csv").printWriter()
        ) {
            println("owner,module")
            descendantsMap.forEach { (owner, projects) ->
                projects.forEach {
                    println("$owner,$it")
                }
            }
            flush()
        }
    }
}
