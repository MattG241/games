package com.flowforge.android.engine.runners

import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.flowforge.android.core.ScreenCapture
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun runVisionModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "camera.photo" -> takePhoto(node, env)
    "screen.capture" -> takeScreenshot(node, env)
    "vision.ocr" -> readText(node, env)
    "vision.barcode" -> readBarcode(node, env)
    else -> null
}

private suspend fun takePhoto(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val target = resolveFile(env, env.text(node, "filename").ifBlank { "photos/shot.jpg" })
    target.parentFile?.mkdirs()
    val front = env.choice(node, "lens", "Back") == "Front"

    withContext(Dispatchers.Main) {
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(env.app)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { if (cont.isActive) cont.resume(it) }
                        .onFailure { if (cont.isActive) cont.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(env.app),
            )
        }

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        // A headless capture still needs a lifecycle owner; the app-wide one lives as long as we do.
        provider.unbindAll()
        provider.bindToLifecycle(
            androidx.lifecycle.ProcessLifecycleOwner.get(),
            selector,
            capture,
        )

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val options = ImageCapture.OutputFileOptions.Builder(target).build()
                capture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(env.app),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            if (cont.isActive) cont.resumeWithException(exception)
                        }
                    },
                )
            }
        } finally {
            provider.unbindAll()
        }
    }

    val bounds = decodeBounds(target)
    return mapOf(
        "path" to target.absolutePath,
        "bytes" to target.length().toDouble(),
        "uri" to android.net.Uri.fromFile(target).toString(),
        "width" to bounds.first.toDouble(),
        "height" to bounds.second.toDouble(),
    )
}

private suspend fun takeScreenshot(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val target = resolveFile(env, env.text(node, "filename").ifBlank { "screenshots/screen.png" })
    target.parentFile?.mkdirs()
    val result = ScreenCapture.capture(env.app, target)
    if (!result.ok) error(result.error ?: "Screen capture failed")
    val bounds = decodeBounds(target)
    return mapOf(
        "path" to target.absolutePath,
        "bytes" to target.length().toDouble(),
        "uri" to android.net.Uri.fromFile(target).toString(),
        "width" to bounds.first.toDouble(),
        "height" to bounds.second.toDouble(),
    )
}

private suspend fun readText(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val file = resolveFile(env, env.text(node, "path"))
    require(file.exists()) { "No image at ${file.absolutePath}" }

    val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
        ?: error("That file is not an image Android can decode")
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    val result = withTimeoutOrNull(60_000L) {
        suspendCancellableCoroutine<Text> { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    } ?: error("Text recognition timed out")

    val blocks = result.textBlocks.map { it.text }
    val lines = result.textBlocks.flatMap { block -> block.lines.map { it.text } }
    return mapOf(
        "text" to result.text,
        "blocks" to blocks,
        "lines" to lines,
        "found" to result.text.isNotBlank(),
    )
}

private suspend fun readBarcode(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val fromCamera = env.choice(node, "source", "Image file") == "Camera"
    val file = if (fromCamera) {
        // Grab a frame first, then read it — same code path either way.
        val temp = File(env.app.cacheDir, "barcode-frame.jpg")
        val shot = takePhoto(
            node.copy(params = node.params + mapOf("filename" to temp.absolutePath)),
            env,
        )
        File(shot["path"].toString())
    } else {
        resolveFile(env, env.text(node, "path"))
    }
    require(file.exists()) { "No image at ${file.absolutePath}" }

    val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
        ?: error("That file is not an image Android can decode")
    val scanner = BarcodeScanning.getClient()

    val barcodes = withTimeoutOrNull(60_000L) {
        suspendCancellableCoroutine<List<Barcode>> { cont ->
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    } ?: error("Barcode scanning timed out")

    val values = barcodes.mapNotNull { it.rawValue }
    return mapOf(
        "found" to values.isNotEmpty(),
        "value" to values.firstOrNull().orEmpty(),
        "format" to (barcodes.firstOrNull()?.format?.toString() ?: ""),
        "values" to values,
        "count" to values.size.toDouble(),
    )
}

private fun decodeBounds(file: File): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return options.outWidth to options.outHeight
}
