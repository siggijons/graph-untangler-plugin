package net.siggijons.gradle.graphuntangler

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.traverse.BreadthFirstIterator

open class AnalyzeModuleGraphTask : DefaultTask() {

    @Input
    lateinit var configurationsToAnalyze: Set<String>

    @TaskAction
    fun run() {
        val graph = project.rootProject
            .dependencyPairs(configurationsToAnalyze)
            .toJGraphTGraph()

        val nodeStatistics = graph.nodeStatistics()
        nodeStatistics.forEach(::println)

        project.rootProject.buildDir
    }

    /**
     * Calculate statistics for graph
     *
     * A breath first iterator is used to traverse the graph and calculate the height using
     * all vertices with 0 in degree as the roots. This is untested for graphs with multiple roots
     * but it could work.
     */
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

    /**
     * Create a list of all dependency pairs for the matching configurations
     *
     * @param configurationsToAnalyze configuration names to analyze
     * @see [Configuration.getName]
     */
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

    /**
     * Create a DirectedGraph from all dependency pairs in a project
     */
    private fun List<Triple<Project, Project, String>>.toJGraphTGraph(): DefaultDirectedGraph<String, DependencyEdge> {
        val g = DefaultDirectedGraph<String, DependencyEdge>(DependencyEdge::class.java)
        forEach { (a, b, label) ->
            g.addVertex(a.name)
            g.addVertex(b.name)
            g.addEdge(a.name, b.name, DependencyEdge(label = label))
        }
        return g
    }
}