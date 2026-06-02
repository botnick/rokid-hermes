package com.botnick.rokidhermes.loader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Launcher entry point. Checks this repo's GitHub releases for a newer APK; if
 * one exists, offers to download and install it via the system package
 * installer, otherwise launches the built-in app. Any failure falls through to
 * launching the installed app, so the loader never blocks normal use.
 */
class LoaderActivity : ComponentActivity() {

    private var installerFired = false
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var uiState by remember { mutableStateOf<LoaderUiState>(LoaderUiState.Checking) }
            var pendingRelease by remember { mutableStateOf<ReleaseInfo?>(null) }

            LaunchedEffect(Unit) {
                val release = UpdateChecker.checkLatestRelease()
                val newer = release != null &&
                    release.downloadUrl != null &&
                    UpdateChecker.isNewer(currentVersion(), release.version)
                if (newer) {
                    pendingRelease = release
                    uiState = LoaderUiState.UpdateAvailable(release!!.version)
                } else {
                    launchApp()
                }
            }

            LoaderHudScreen(
                state = uiState,
                onUpdate = {
                    val release = pendingRelease ?: return@LoaderHudScreen
                    lifecycleScope.launch { downloadAndInstall(release) { uiState = it } }
                },
                onSkip = { launchApp() }
            )
        }
    }

    /**
     * If we fired the installer and the user came back to us (cancelled or
     * finished installing), drop into the current app — done here, not inline
     * after startActivity(installer), so we never race the installer dialog.
     */
    override fun onResume() {
        super.onResume()
        if (installerFired && !launched) launchApp()
    }

    private suspend fun downloadAndInstall(
        release: ReleaseInfo,
        onState: (LoaderUiState) -> Unit
    ) {
        val url = release.downloadUrl ?: return

        if (!AppLauncher.canInstall(this)) {
            AppLauncher.openInstallPermissionSettings(this)
            onState(LoaderUiState.Error("Allow installs, then tap update again"))
            return
        }

        onState(LoaderUiState.Downloading(0f))
        var lastPct = -1
        val apk = CodeDownloader.download(this, url) { progress ->
            val pct = (progress * 100).toInt()
            if (pct != lastPct) { // throttle: one UI update per integer percent
                lastPct = pct
                lifecycleScope.launch { onState(LoaderUiState.Downloading(progress)) }
            }
        }

        if (apk == null) {
            onState(LoaderUiState.Error("Download failed"))
            return
        }

        onState(LoaderUiState.Loading)
        if (AppLauncher.installApk(this, apk)) {
            // Let the installer own the foreground; we launch the app from
            // onResume() only if the user returns here (i.e. cancels the install).
            installerFired = true
        } else {
            onState(LoaderUiState.Error("Couldn't open installer"))
        }
    }

    private fun launchApp() {
        if (launched) return
        launched = true
        AppLauncher.launchApp(this)
        finish()
    }

    private fun currentVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }
}
