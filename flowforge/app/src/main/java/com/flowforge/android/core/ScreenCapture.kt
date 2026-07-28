package com.flowforge.android.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.flowforge.android.engine.runners.Notifications
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class CaptureResult(val ok: Boolean, val error: String? = null)

/**
 * One-frame screen capture through MediaProjection.
 *
 * Android insists the user consents in an Activity and, from Android 10, that the capture happens
 * while a foreground service is running — so a request hops Activity -> Service -> back here.
 */
object ScreenCapture {

    @Volatile
    internal var pending: CompletableDeferred<CaptureResult>? = null

    @Volatile
    internal var targetPath: String? = null

    suspend fun capture(context: Context, file: File): CaptureResult {
        val deferred = CompletableDeferred<CaptureResult>()
        pending = deferred
        targetPath = file.absolutePath

        val intent = Intent(context, ScreenCaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { return CaptureResult(false, "Could not ask for screen capture consent") }

        return withTimeoutOrNull(90_000L) { deferred.await() }
            ?: CaptureResult(false, "Screen capture timed out waiting for consent")
    }

    internal fun finish(result: CaptureResult) {
        pending?.complete(result)
        pending = null
    }
}

/** Transparent shim whose only job is to collect the system's capture consent. */
class ScreenCaptureActivity : ComponentActivity() {

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            ScreenCapture.finish(CaptureResult(false, "Screen capture was declined"))
            finish()
            return@registerForActivityResult
        }
        val service = Intent(this, ScreenCaptureService::class.java)
            .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
            .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service)
        else startService(service)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            ScreenCapture.finish(CaptureResult(false, "This device does not support screen capture"))
            finish()
            return
        }
        consent.launch(manager.createScreenCaptureIntent())
    }
}

/** Holds the foreground-service requirement while one frame is grabbed. */
class ScreenCaptureService : android.app.Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotice()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data: Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        val path = ScreenCapture.targetPath
        if (data == null || path == null) {
            ScreenCapture.finish(CaptureResult(false, "Screen capture was cancelled"))
            stopSelf()
            return START_NOT_STICKY
        }

        runCatching { grabFrame(resultCode, data, File(path)) }
            .onSuccess { ScreenCapture.finish(CaptureResult(true)) }
            .onFailure { ScreenCapture.finish(CaptureResult(false, it.message ?: "Screen capture failed")) }

        stopSelf()
        return START_NOT_STICKY
    }

    private fun startForegroundNotice() {
        Notifications.ensureChannels(this)
        val notification = androidx.core.app.NotificationCompat
            .Builder(this, Notifications.CHANNEL_SERVICE)
            .setSmallIcon(com.flowforge.android.R.drawable.ic_tile)
            .setContentTitle("Capturing the screen")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun grabFrame(resultCode: Int, data: Intent, target: File) {
        val manager = getSystemService(MediaProjectionManager::class.java)
            ?: error("Screen capture is unavailable")
        val projection: MediaProjection = manager.getMediaProjection(resultCode, data)
            ?: error("The system refused the capture session")

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            .getRealMetrics(metrics)

        val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)

        // Android 14 requires a registered callback before the virtual display is created.
        projection.registerCallback(object : MediaProjection.Callback() {}, null)

        val display = projection.createVirtualDisplay(
            "FlowForge",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null,
        )

        try {
            var image = reader.acquireLatestImage()
            var waited = 0
            while (image == null && waited < 3000) {
                Thread.sleep(50)
                waited += 50
                image = reader.acquireLatestImage()
            }
            requireNotNull(image) { "No screen frame arrived" }

            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * metrics.widthPixels
            val bitmap = Bitmap.createBitmap(
                metrics.widthPixels + rowPadding / plane.pixelStride,
                metrics.heightPixels,
                Bitmap.Config.ARGB_8888,
            )
            bitmap.copyPixelsFromBuffer(plane.buffer)
            image.close()

            val cropped = Bitmap.createBitmap(bitmap, 0, 0, metrics.widthPixels, metrics.heightPixels)
            target.parentFile?.mkdirs()
            target.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cropped.recycle()
            bitmap.recycle()
        } finally {
            runCatching { display.release() }
            runCatching { reader.close() }
            runCatching { projection.stop() }
        }
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val NOTIFICATION_ID = 4202
    }
}
