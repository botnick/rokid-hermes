package com.botnick.rokidhermes.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Captures a single frame from the glasses camera and returns it as a downscaled
 * JPEG `data:` URL, ready to drop into an OpenAI `image_url` content part for
 * Hermes' vision. Binds the camera only for the moment of capture (no preview,
 * no continuous use) to keep it light on a wearable.
 */
class CameraCapture(private val context: Context) {

    /**
     * @return a `data:image/jpeg;base64,…` URL, or null if capture failed.
     * Must be called from a coroutine; all CameraX work runs on the main executor.
     */
    suspend fun captureDataUrl(
        owner: LifecycleOwner,
        maxDim: Int = 1568,
        quality: Int = 92
    ): String? = suspendCancellableCoroutine { cont ->
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = try {
                future.get()
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
                return@addListener
            }
            try {
                provider.unbindAll()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val selector =
                    if (provider.hasCameraSafe(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                provider.bindToLifecycle(owner, selector, capture)
                capture.takePicture(
                    mainExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val url = try {
                                encodeDataUrl(image, maxDim, quality)
                            } catch (e: Exception) {
                                e.printStackTrace(); null
                            } finally {
                                image.close()
                            }
                            provider.unbindAll()
                            if (cont.isActive) cont.resume(url)
                        }

                        override fun onError(exc: ImageCaptureException) {
                            exc.printStackTrace()
                            provider.unbindAll()
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                provider.unbindAll()
                if (cont.isActive) cont.resume(null)
            }
        }, mainExecutor)
    }

    private fun ProcessCameraProvider.hasCameraSafe(selector: CameraSelector): Boolean =
        try {
            hasCamera(selector)
        } catch (e: Exception) {
            false
        }

    private fun encodeDataUrl(image: ImageProxy, maxDim: Int, quality: Int): String {
        // ImageCapture's default output is JPEG: a single plane of encoded bytes.
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }

        // Decode bounds first, then subsample so we never decode the full sensor
        // image (8–12+ MP -> tens of MB) into memory — keeps it OOM-safe while
        // still producing a sharp image just above the target size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }

        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalStateException("decode failed")

        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated != bmp) bmp.recycle()
            bmp = rotated
        }

        val longest = max(bmp.width, bmp.height)
        if (longest > maxDim) {
            val scale = maxDim.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt(),
                (bmp.height * scale).toInt(),
                true
            )
            if (scaled != bmp) bmp.recycle()
            bmp = scaled
        }

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bmp.recycle()
        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }
}
