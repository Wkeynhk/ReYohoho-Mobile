package com.example.reyohoho.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import android.content.Context.RECEIVER_NOT_EXPORTED
import kotlinx.coroutines.delay
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import com.example.reyohoho.MainActivity
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

object UpdateChecker {
    data class UpdateResult(val isUpdateAvailable: Boolean, val latestVersion: String, val downloadUrl: String = "")

    private const val GITHUB_API_URL = "https://api.github.com/repos/Wkeynhk/ReYohoho-Mobile/releases/latest"

    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder().url(GITHUB_API_URL).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Ошибка запроса к GitHub: ${response.code}")
        }
        val body = response.body?.string() ?: throw Exception("Пустой ответ от GitHub")
        val json = JSONObject(body)
        val tagName = json.getString("tag_name")
        val assets = json.getJSONArray("assets")
        if (assets.length() == 0) {
            throw Exception("В релизе нет APK")
        }
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            if (name.endsWith(".apk")) {
                apkUrl = asset.getString("browser_download_url")
                break
            }
        }
        if (apkUrl.isEmpty()) {
            throw Exception("APK не найден в релизе")
        }
        val currentVersion = "3.1"
        val latestVersion = tagName.replace("v", "")
        val isUpdate = latestVersion != currentVersion
        UpdateResult(isUpdate, latestVersion, apkUrl)
    }

    suspend fun downloadAndInstallApk(context: Context, url: String, version: String, autoInstall: Boolean = false) = withContext(Dispatchers.Main) {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ReYohoho/$version")
            if (!dir.exists()) dir.mkdirs()
            val fileName = "app-release.apk"
            val file = File(dir, fileName)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Загрузка обновления...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(file))
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)
            Toast.makeText(context, "Скачивание обновления началось", Toast.LENGTH_SHORT).show()
            if (autoInstall) {
                for (i in 1..180) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                        val localUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            cursor.close()
                            try {
                                val apkFile = File(dir, fileName)
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", apkFile)
                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(installIntent)
                            } catch (e: Exception) {
                            }
                            break
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            cursor.close()
                            break
                        }
                    }
                    cursor.close()
                    delay(1000)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    suspend fun downloadApkOnly(context: Context, url: String, version: String): Long = withContext(Dispatchers.Main) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ReYohoho/$version")
        if (!dir.exists()) dir.mkdirs()
        val fileName = "app-release.apk"
        val file = File(dir, fileName)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("Загрузка обновления...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(file))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(request)
        id
    }

    fun installDownloadedApk(context: Context, downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId)
        if (uri != null) {
            try {
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(installIntent)
            } catch (e: Exception) {
            }
        } else {
            Toast.makeText(context, "APK не найден для установки", Toast.LENGTH_LONG).show()
        }
    }

    fun sendUpdateNotification(context: Context, version: String) {
        val channelId = "update_channel"
        val notificationId = 1001
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_about", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Доступно обновление")
            .setContentText("Вышла новая версия: $version")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.stat_sys_download_done, "Обновить", pendingIntent)
        val manager = NotificationManagerCompat.from(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Обновления", android.app.NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        manager.notify(notificationId, builder.build())
    }
}

class UpdateCheckWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settingsManager = com.example.reyohoho.ui.SettingsManager.getInstance(applicationContext)
        val notifyOnUpdate = settingsManager.isNotifyOnUpdateEnabled()
        if (!notifyOnUpdate) {
            return Result.success()
        }
        val savedDownloadedVersion = settingsManager.getDownloadedUpdateVersion()
        val savedDownloadedId = settingsManager.getDownloadedUpdateId()
        var latestVersion: String? = null
        val ignoredUpdateVersion = settingsManager.getIgnoredUpdateVersion()
        try {
            if (savedDownloadedVersion != null && savedDownloadedId > 0) {
                return Result.success()
            }
            if (ignoredUpdateVersion != null && latestVersion == ignoredUpdateVersion) {
                return Result.success()
            }
            val result = UpdateChecker.checkForUpdate()
            latestVersion = result.latestVersion
            if (result.isUpdateAvailable) {
                if (ignoredUpdateVersion == null || latestVersion != ignoredUpdateVersion) {
                    UpdateChecker.sendUpdateNotification(applicationContext, result.latestVersion)
                }
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}

fun scheduleUpdateCheckWorker(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(15, TimeUnit.MINUTES)
        .addTag("update_check")
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "update_check",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
} 