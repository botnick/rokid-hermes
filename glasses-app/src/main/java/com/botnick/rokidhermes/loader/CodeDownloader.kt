package com.botnick.rokidhermes.loader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads a release APK from a GitHub asset URL into the app's cache
 * (`cache/updates/`), where a FileProvider can share it with the installer.
 */
object CodeDownloader {

    private const val DIR = "updates"
    private const val APK_FILENAME = "update.apk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * @param onProgress progress 0.0..1.0 (-1 if the size is unknown)
     * @return the downloaded APK file, or null on failure
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null

                val dir = File(context.cacheDir, DIR).apply { mkdirs() }
                val outputFile = File(dir, APK_FILENAME)
                val tempFile = File(dir, "$APK_FILENAME.tmp")
                val contentLength = body.contentLength()

                FileOutputStream(tempFile).use { fos ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            onProgress(if (contentLength > 0) totalRead.toFloat() / contentLength else -1f)
                        }
                    }
                }

                if (outputFile.exists()) outputFile.delete()
                if (!tempFile.renameTo(outputFile)) return@withContext null
                outputFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
