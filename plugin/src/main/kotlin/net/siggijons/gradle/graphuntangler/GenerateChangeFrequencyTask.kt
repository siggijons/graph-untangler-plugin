package net.siggijons.gradle.graphuntangler

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

private const val DEFAULT_FREQUENCY_DAYS = 28L

abstract class GenerateChangeFrequencyTask : DefaultTask() {

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Input
    abstract val configurationsToAnalyze: SetProperty<String>

    @TaskAction
    fun run() {
        val frequencyStart = (project.findProperty("frequency-start") as String?)?.let {
            LocalDate.parse(it)
        } ?: LocalDate.now()

        val days = (project.findProperty("frequency-days") as String?)?.toLong()
            ?: DEFAULT_FREQUENCY_DAYS

        val date = frequencyStart.minusDays(days).atStartOfDay()

        logger.info("Checking change frequency since $date")

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
        } catch (e: IOException) {
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