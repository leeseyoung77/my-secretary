package com.luxboy.mysecretary.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Downloads the APK using Android's [DownloadManager], then launches the system installer.
     * Suspends until the download finishes (success or failure). Returns true if installer was launched.
     */
    suspend fun downloadAndLaunchInstall(info: UpdateInfo): Boolean =
        suspendCancellableCoroutine { cont ->
            val targetFile = File(updatesDir(), "update-${info.latestVersion}.apk").also {
                if (it.exists()) it.delete()
            }

            val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
                .setTitle("My Secretary 업데이트")
                .setDescription("v${info.latestVersion}")
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationUri(Uri.fromFile(targetFile))
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )

            val downloadId: Long = runCatching { downloadManager.enqueue(request) }
                .getOrElse {
                    cont.resume(false); return@suspendCancellableCoroutine
                }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id != downloadId) return
                    runCatching { ctx.unregisterReceiver(this) }
                    if (queryStatus(downloadId) == DownloadManager.STATUS_SUCCESSFUL && targetFile.exists()) {
                        launchInstaller(targetFile)
                        cont.resume(true)
                    } else {
                        cont.resume(false)
                    }
                }
            }
            // ACTION_DOWNLOAD_COMPLETE is a system broadcast from DownloadManager —
            // must be RECEIVER_EXPORTED, otherwise Android 14+ silently drops it
            // and the installer never auto-launches after download.
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED,
            )
            cont.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
                runCatching { downloadManager.remove(downloadId) }
            }
        }

    private fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        runCatching { context.startActivity(intent) }
    }

    private fun queryStatus(id: Long): Int {
        val query = DownloadManager.Query().setFilterById(id)
        downloadManager.query(query)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (idx >= 0) return c.getInt(idx)
            }
        }
        return DownloadManager.STATUS_FAILED
    }

    private fun updatesDir(): File =
        File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
}
