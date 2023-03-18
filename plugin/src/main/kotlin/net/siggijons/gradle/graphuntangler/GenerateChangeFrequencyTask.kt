package net.siggijons.gradle.graphuntangler

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.time.LocalDate
import java.time.LocalDateTime

abstract class GenerateChangeFrequencyTask : DefaultTask() {

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Input
    abstract val configurationsToAnalyze: SetProperty<String>

    @TaskAction
    fun run() {
        val days = 28L
        val date = LocalDate.now().minusDays(days).atStartOfDay()

        val frequencyMap = project.subprojects.associate {
            it.path to frequency(it, date)
        }

        output.get().asFile.printWriter().use { writer ->
            writer.println("module,changes")
            frequencyMap.forEach { (a, b) ->
                writer.println("$a,$b")
            }
            writer.flush()
        }
    }

    private fun frequency(
        project: Project,
        since: LocalDateTime
    ): Int {
        val dir = project.buildFile.parentFile
        return try {
            exec(
                "git log --follow --format=oneline --since=\"${since}\" -- $dir " +
                        "| wc -l " +
                        "| tr -d \" \""
            )
                .trim()
                .toInt()
        } catch (e: Exception) {
            logger.warn("Error checking frequency of $project", e)
            0
        }
    }

    private fun exec(command: String): String {
        val process = ProcessBuilder()
            .command("bash", "-c", command)
            .redirectErrorStream(true)
            .start()
        return process.inputStream.bufferedReader().readText()
    }
}