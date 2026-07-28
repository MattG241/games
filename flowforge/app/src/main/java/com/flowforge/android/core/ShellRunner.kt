package com.flowforge.android.core

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val via: String,
) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Runs shell commands through whichever privileged channel is available.
 *
 * Shizuku gives ADB-level rights without root; root gives everything; the plain shell is what any
 * app can already do. Callers say which they want, or take the best on offer.
 */
object ShellRunner {

    fun shizukuAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun shizukuBound(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun rootAvailable(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        finished && process.exitValue() == 0
    }.getOrDefault(false)

    fun requestShizukuPermission(requestCode: Int = 4210) {
        runCatching {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        }
    }

    fun run(command: String, preferred: String = "Best available", timeoutSeconds: Long = 20): ShellResult {
        val order = when (preferred) {
            "Shizuku" -> listOf("Shizuku")
            "Root" -> listOf("Root")
            "Unprivileged" -> listOf("Unprivileged")
            else -> listOf("Shizuku", "Root", "Unprivileged")
        }

        var last: ShellResult? = null
        for (channel in order) {
            val result = when (channel) {
                "Shizuku" -> if (shizukuAvailable()) runViaShizuku(command, timeoutSeconds) else null
                "Root" -> runViaProcess(listOf("su", "-c", command), "Root", timeoutSeconds)
                else -> runViaProcess(listOf("sh", "-c", command), "Unprivileged", timeoutSeconds)
            } ?: continue
            if (result.ok) return result
            last = result
        }
        return last ?: ShellResult(
            exitCode = -1,
            stdout = "",
            stderr = "No shell channel is available. Install Shizuku and grant FlowForge access, " +
                "or use a rooted device.",
            via = "none",
        )
    }

    /**
     * Shizuku's process API is not part of its published surface, so it is reached by reflection.
     * If a future Shizuku release moves it, this degrades to "unavailable" instead of crashing.
     */
    private fun runViaShizuku(command: String, timeoutSeconds: Long): ShellResult? = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null)
            ?: return@runCatching null

        val processClass = process.javaClass
        val stdout = (processClass.getMethod("getInputStream").invoke(process) as java.io.InputStream)
            .bufferedReader().use(BufferedReader::readText)
        val stderr = (processClass.getMethod("getErrorStream").invoke(process) as java.io.InputStream)
            .bufferedReader().use(BufferedReader::readText)
        val exit = processClass.getMethod("waitFor").invoke(process) as? Int ?: -1

        ShellResult(exit, stdout.trim(), stderr.trim(), "Shizuku")
    }.getOrNull()

    private fun runViaProcess(command: List<String>, via: String, timeoutSeconds: Long): ShellResult? =
        runCatching {
            val process = ProcessBuilder(command).start()
            val finished = process.waitFor(timeoutSeconds.coerceIn(1, 300), TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching ShellResult(-1, "", "Timed out after $timeoutSeconds s", via)
            }
            ShellResult(
                exitCode = process.exitValue(),
                stdout = process.inputStream.bufferedReader().use(BufferedReader::readText).trim(),
                stderr = process.errorStream.bufferedReader().use(BufferedReader::readText).trim(),
                via = via,
            )
        }.getOrNull()
}
