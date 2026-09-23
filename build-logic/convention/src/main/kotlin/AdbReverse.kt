import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import java.io.File
import java.io.IOException
import java.util.Properties

/** Never fails the build: every failure is one line on the console. */
abstract class AdbReverseTask : DefaultTask() {

    @get:Input
    abstract val adbExecutable: Property<String>

    @get:Input
    abstract val port: Property<String>

    init {
        // The tunnel dies with the emulator and with the adb server, so an up-to-date result would
        // mean a tunnel that is silently absent. It is a few milliseconds; just run it every time.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun openTunnels() {
        val adb = adbExecutable.get()
        val tunnelPort = port.get()

        val (listed, devices) = execute(adb, "devices")
        if (listed != 0) {
            logger.lifecycle("adb reverse: skipped, `adb devices` failed. $devices")
            return
        }

        val serials = devices
            .lineSequence()
            .drop(1)
            .map { it.trim().split(Regex("""\s+""")) }
            .filter { it.size >= 2 && it[1] == "device" }
            .map { it.first() }
            .toList()

        if (serials.isEmpty()) {
            logger.lifecycle("adb reverse: no device attached, no tunnel opened.")
            return
        }

        serials.forEach { serial ->
            val (code, output) = execute(adb, "-s", serial, "reverse", "tcp:$tunnelPort", "tcp:$tunnelPort")

            if (code == 0) {
                logger.lifecycle("adb reverse: $serial localhost:$tunnelPort -> this machine")
            } else {
                logger.lifecycle("adb reverse: $serial failed. $output")
            }
        }
    }

    private fun execute(vararg command: String): Pair<Int, String> =
        try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()

            process.waitFor() to output.trim()
        } catch (e: IOException) {
            -1 to "Could not run ${command.first()}: ${e.message}"
        }
}

/**
 * Hung off assemble rather than install, because Android Studio's Run button never calls the
 * install task.
 */
fun Project.openAdbReverseTunnelForLocalBuilds(host: String, port: String) {
    val adbReverseLocalServer = tasks.register<AdbReverseTask>("adbReverseLocalServer") {
        group = "install"
        description = "Opens the adb reverse tunnel the `local` flavor's default host relies on."

        adbExecutable.set(findAdb())
        this.port.set(port)
    }

    if (host in setOf("localhost", "127.0.0.1")) {
        tasks
            .matching { it.name.startsWith("assembleLocal") || it.name.startsWith("installLocal") }
            .configureEach { finalizedBy(adbReverseLocalServer) }
    }
}

private fun Project.findAdb(): String {
    val executable = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"

    val sdkDirectory = providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .orNull
        ?.let { Properties().apply { load(it.reader()) }.getProperty("sdk.dir") }

    val roots = listOfNotNull(
        providers.environmentVariable("ANDROID_HOME").orNull,
        providers.environmentVariable("ANDROID_SDK_ROOT").orNull,
        sdkDirectory,
    )

    return roots
        .map { File(it, "platform-tools/$executable") }
        .firstOrNull { it.isFile }
        ?.absolutePath
        ?: executable
}
