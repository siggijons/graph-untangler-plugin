package net.siggijons.gradle.graphuntangler

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.traverse.BreadthFirstIterator

/**
 * @suppress unused used as a plugin
 */
@Suppress("unused")
class GraphUntanglerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            GraphUntanglerPluginExtension::class.java,
            "untangler",
            GraphUntanglerPluginExtension::class.java
        )
        project.task("analyzeModuleGraph") { task ->
            task.doLast {
                println("Hello from the GraphUntanglerPlugin. ${extension.message}")
                project.rootProject
                    .dependencyPairs(extension.configurationsToAnalyze)
                    .toJGraphTGraph()
                    .nodeStatistics()
                    .forEach(::println)
            }
        }
    }

    private fun DefaultDirectedGraph<String, DependencyEdge>.nodeStatistics(): List<NodeStatistics> {
        val betweennessCentrality = BetweennessCentrality(this).scores
        val roots = vertexSet().find { inDegreeOf(it) == 0 }
        val iterator = BreadthFirstIterator(this, roots)
        val stats = mutableListOf<NodeStatistics>()
        while (iterator.hasNext()) {
            val node = iterator.next()
            val s = NodeStatistics(
                node = node,
                betweennessCentrality = requireNotNull(betweennessCentrality[node]) {
                    "betweennessCentrality not found for $node"
                },
                degree = degreeOf(node),
                inDegree = inDegreeOf(node),
                outDegree = outDegreeOf(node),
                height = iterator.getDepth(node)
            )
            stats.add(s)
        }
        return stats
    }

    private fun Project.dependencyPairs(
        configurationsToAnalyze: Set<String>
    ): List<Triple<Project, Project, String>> {
        return subprojects.flatMap { project ->
            project.configurations
                .filter { configurationsToAnalyze.contains(it.name) }
                .flatMap { configuration ->
                    configuration.dependencies.filterIsInstance<ProjectDependency>()
                        .map { Triple(project, it.dependencyProject, configuration.name) }
                }
        }
    }

    private fun List<Triple<Project, Project, String>>.toJGraphTGraph(): DefaultDirectedGraph<String, DependencyEdge> {
        val g = DefaultDirectedGraph<String, DependencyEdge>(DependencyEdge::class.java)
        forEach { (a, b, label) ->
            g.addVertex(a.name)
            g.addVertex(b.name)
            g.addEdge(a.name, b.name, DependencyEdge(label = label))
        }
        return g
    }

    class DependencyEdge(private val label: String) : DefaultEdge() {
        override fun toString(): String {
            return "($source : $target : $label)"
        }
    }

    data class NodeStatistics(
        val node: String,
        val betweennessCentrality: Double,
        val degree: Int,
        val inDegree: Int,
        val outDegree: Int,
        val height: Int
    )
}