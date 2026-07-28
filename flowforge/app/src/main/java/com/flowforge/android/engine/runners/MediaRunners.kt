package com.flowforge.android.engine.runners

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import com.flowforge.android.triggers.FlowNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

suspend fun runMediaModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "media.control" -> mediaControl(node, env)
    "media.nowPlaying" -> nowPlaying(env)
    "media.play" -> playSound(node, env)
    "media.record" -> recordAudio(node, env)
    else -> null
}

private fun mediaControl(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val am = env.app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val action = env.choice(node, "action", "Play/Pause")
    val code = when (action) {
        "Play" -> KeyEvent.KEYCODE_MEDIA_PLAY
        "Pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
        "Next" -> KeyEvent.KEYCODE_MEDIA_NEXT
        "Previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        "Stop" -> KeyEvent.KEYCODE_MEDIA_STOP
        else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    }
    val now = System.currentTimeMillis()
    am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
    am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    return mapOf("action" to action)
}

private fun nowPlaying(env: RunEnv): Map<String, Any?> {
    val manager = env.app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        ?: error("Media sessions are unavailable on this device")
    val component = ComponentName(env.app, FlowNotificationListener::class.java)

    val sessions = runCatching { manager.getActiveSessions(component) }.getOrElse {
        error("Reading the playing track needs notification access — grant it in Settings inside FlowForge")
    }

    val session = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        ?: sessions.firstOrNull()
        ?: return mapOf("playing" to false, "title" to "", "artist" to "", "album" to "", "app" to "")

    val metadata = session.metadata
    val appName = runCatching {
        env.app.packageManager.getApplicationLabel(
            env.app.packageManager.getApplicationInfo(session.packageName, 0)
        ).toString()
    }.getOrDefault(session.packageName)

    return mapOf(
        "title" to metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
        "artist" to metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
        "album" to metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
        "app" to appName,
        "package" to session.packageName,
        "playing" to (session.playbackState?.state == PlaybackState.STATE_PLAYING),
        "durationMs" to (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.toDouble() ?: 0.0),
        "positionMs" to (session.playbackState?.position?.toDouble() ?: 0.0),
    )
}

private suspend fun playSound(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val source = env.choice(node, "source", "File")
    val streamName = env.choice(node, "stream", "Media")
    val stream = audioStream(streamName)

    if (source == "Beep") {
        val tone = ToneGenerator(stream, 90)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 400)
        delay(500)
        tone.release()
        return mapOf("played" to true, "durationMs" to 400.0)
    }

    val uri: Uri = when (source) {
        "URL" -> Uri.parse(env.text(node, "path").trim())
        "Notification tone" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        "Alarm tone" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        "Ringtone" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        else -> {
            val file = resolveFile(env, env.text(node, "path"))
            require(file.exists()) { "No file at ${file.absolutePath}" }
            Uri.fromFile(file)
        }
    }

    val player = MediaPlayer()
    player.setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(
                when (streamName) {
                    "Notification" -> AudioAttributes.USAGE_NOTIFICATION
                    "Alarm" -> AudioAttributes.USAGE_ALARM
                    "Ring" -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                    else -> AudioAttributes.USAGE_MEDIA
                }
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    )

    withContext(Dispatchers.IO) {
        player.setDataSource(env.app, uri)
        player.prepare()
    }
    val durationMs = player.duration.coerceAtLeast(0)
    player.start()

    if (env.bool(node, "wait", false)) {
        withTimeoutOrNull(minOf(durationMs.toLong() + 2000, 120_000L)) {
            suspendCancellableCoroutine { cont ->
                player.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                cont.invokeOnCancellation { runCatching { player.release() } }
            }
        }
        runCatching { player.release() }
    } else {
        player.setOnCompletionListener { runCatching { it.release() } }
    }

    return mapOf("played" to true, "durationMs" to durationMs.toDouble())
}

private suspend fun recordAudio(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val seconds = env.number(node, "seconds", 10.0).coerceIn(1.0, 600.0)
    val target = resolveFile(env, env.text(node, "filename").ifBlank { "recordings/clip.m4a" })
    target.parentFile?.mkdirs()

    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(env.app)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }

    try {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(96_000)
        recorder.setAudioSamplingRate(44_100)
        recorder.setOutputFile(target.absolutePath)
        recorder.prepare()
        recorder.start()
        delay((seconds * 1000).toLong())
        runCatching { recorder.stop() }
    } catch (e: Exception) {
        error("Recording failed — check the microphone permission (${e.message})")
    } finally {
        runCatching { recorder.release() }
    }

    return mapOf(
        "path" to target.absolutePath,
        "bytes" to target.length().toDouble(),
        "seconds" to seconds,
    )
}
