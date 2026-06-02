package com.botnick.rokidhermes.loader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Launches the built-in app and installs downloaded update APKs.
 *
 * Self-update for a sideloaded app is done the only way that actually works:
 * hand a downloaded APK to the system package installer (via a FileProvider
 * content URI). The previous DexClassLoader approach never ran the downloaded
 * code — `startActivity` resolves an Activity by name through the base APK's
 * class loader, so the loaded DEX class was silently ignored.
 */
object AppLauncher {

    private const val ENTRY_ACTIVITY = "com.botnick.rokidhermes.MainActivity"
    private const val APK_MIME = "application/vnd.android.package-archive"

    /** Starts the installed MainActivity (the normal, non-update path). */
    fun launchApp(context: Context) {
        try {
            val intent = Intent().apply {
                setClassName(context.packageName, ENTRY_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Whether the OS will let us launch a package install (Android O+ gates this). */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Sends the user to the "install unknown apps" settings page for this app. */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Fires the system installer for [apk]. Returns false if it couldn't be launched. */
    fun installApk(context: Context, apk: File): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
