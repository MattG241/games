package com.flowforge.android.engine.runners

import com.flowforge.android.core.ShellResult
import com.flowforge.android.core.ShellRunner
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun runPrivilegedModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "priv.shell" -> shell(node, env)
    "priv.radio" -> radio(node, env)
    "priv.appControl" -> appControl(node, env)
    "priv.key" -> sendKey(node, env)
    "priv.setting" -> systemSetting(node, env)
    else -> null
}

private fun ShellResult.bundle(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf(
    "exitCode" to exitCode.toDouble(),
    "stdout" to stdout,
    "stderr" to stderr,
    "via" to via,
    "ok" to ok,
) + extra

private suspend fun exec(
    env: RunEnv,
    command: String,
    preferred: String = "Best available",
    timeout: Long = 20,
): ShellResult = withContext(Dispatchers.IO) {
    ShellRunner.run(command, preferred, timeout)
}

private suspend fun shell(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val command = env.text(node, "command").trim()
    require(command.isNotBlank()) { "Enter a command to run" }
    val result = exec(
        env,
        command,
        env.choice(node, "via", "Best available"),
        env.number(node, "timeout", 20.0).toLong(),
    )
    if (result.via == "none") error(result.stderr)
    return result.bundle()
}

private suspend fun radio(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val radio = env.choice(node, "radio", "Wi-Fi")
    val requested = env.choice(node, "state", "On")

    val current = when (radio) {
        "Wi-Fi" -> exec(env, "settings get global wifi_on").stdout.trim() == "1"
        "Mobile data" -> exec(env, "settings get global mobile_data").stdout.trim() == "1"
        "Airplane mode" -> exec(env, "settings get global airplane_mode_on").stdout.trim() == "1"
        else -> exec(env, "settings get global bluetooth_on").stdout.trim() == "1"
    }
    val on = when (requested) {
        "Off" -> false
        "Toggle" -> !current
        else -> true
    }

    val command = when (radio) {
        "Wi-Fi" -> "svc wifi ${if (on) "enable" else "disable"}"
        "Mobile data" -> "svc data ${if (on) "enable" else "disable"}"
        "Bluetooth" -> "svc bluetooth ${if (on) "enable" else "disable"}"
        else -> "settings put global airplane_mode_on ${if (on) 1 else 0} && " +
            "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $on"
    }

    val result = exec(env, command)
    if (result.via == "none") error(result.stderr)
    if (!result.ok) {
        error("$radio could not be switched — this needs Shizuku or root (${result.stderr.take(160)})")
    }
    return result.bundle(mapOf("done" to true, "radio" to radio, "state" to if (on) "On" else "Off"))
}

private suspend fun appControl(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val pkg = env.text(node, "package").trim()
    require(pkg.isNotBlank()) { "Pick an app" }
    val action = env.choice(node, "action", "Force stop")
    val permission = env.text(node, "permission").trim()

    val command = when (action) {
        "Grant permission" -> {
            require(permission.isNotBlank()) { "Enter the permission to grant" }
            "pm grant $pkg $permission"
        }
        "Revoke permission" -> {
            require(permission.isNotBlank()) { "Enter the permission to revoke" }
            "pm revoke $pkg $permission"
        }
        "Clear data" -> "pm clear $pkg"
        "Disable" -> "pm disable-user --user 0 $pkg"
        "Enable" -> "pm enable $pkg"
        else -> "am force-stop $pkg"
    }

    val result = exec(env, command)
    if (result.via == "none") error(result.stderr)
    if (!result.ok) error("$action failed: ${result.stderr.ifBlank { result.stdout }.take(200)}")
    return result.bundle(mapOf("done" to true, "output" to result.stdout))
}

private suspend fun sendKey(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val key = env.choice(node, "key", "Home")
    val keycode = when (key) {
        "Back" -> 4
        "Recents" -> 187
        "Power" -> 26
        "Volume up" -> 24
        "Volume down" -> 25
        "Camera" -> 27
        "Enter" -> 66
        "Custom keycode" -> env.number(node, "keycode", 3.0).toInt()
        else -> 3
    }
    val result = exec(env, "input keyevent $keycode")
    if (result.via == "none") error(result.stderr)
    if (!result.ok) error("Sending $key failed: ${result.stderr.take(200)}")
    return result.bundle(mapOf("sent" to true, "key" to key, "keycode" to keycode.toDouble()))
}

private suspend fun systemSetting(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val namespace = env.choice(node, "namespace", "global")
    val key = env.text(node, "key").trim()
    require(key.isNotBlank()) { "Enter the setting key" }

    return if (env.choice(node, "action", "Write") == "Read") {
        val result = exec(env, "settings get $namespace $key")
        if (result.via == "none") error(result.stderr)
        result.bundle(mapOf("done" to result.ok, "key" to key, "value" to result.stdout.trim()))
    } else {
        val value = env.text(node, "value")
        val result = exec(env, "settings put $namespace $key $value")
        if (result.via == "none") error(result.stderr)
        if (!result.ok) error("Writing $namespace.$key failed: ${result.stderr.take(200)}")
        result.bundle(mapOf("done" to true, "key" to key, "value" to value))
    }
}
