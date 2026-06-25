package automatl.juras.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@DisableCachingByDefault(because = "Git status is live build metadata.")
abstract class GenerateBuildMetadata @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val appVersion: Property<String>

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val commit = gitOutput("rev-parse", "HEAD").ifBlank { "unknown" }
        val dirty = gitOutput("status", "--porcelain").isNotBlank()
        val commitDisplay = if (dirty && commit != "unknown") "$commit-dirty" else commit
        val outputFile = outputDir.file(
            "automatl/juras/ui/platform/BuildMetadata.kt"
        ).get().asFile

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package automatl.juras.ui.platform

            internal object BuildMetadata {
                const val appVersion = ${appVersion.get().kotlinStringLiteral()}
                const val commit = ${commitDisplay.kotlinStringLiteral()}
            }
            """.trimIndent() + "\n",
        )
    }

    private fun gitOutput(vararg args: String): String {
        val stdout = ByteArrayOutputStream()
        val result = try {
            execOperations.exec {
                workingDir = rootDirectory.get().asFile
                commandLine("git", *args)
                standardOutput = stdout
                isIgnoreExitValue = true
            }
        } catch (_: RuntimeException) {
            return ""
        }
        return if (result.exitValue == 0) stdout.toString().trim() else ""
    }
}

private fun String.kotlinStringLiteral(): String = buildString {
    append('"')
    for (char in this@kotlinStringLiteral) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}
