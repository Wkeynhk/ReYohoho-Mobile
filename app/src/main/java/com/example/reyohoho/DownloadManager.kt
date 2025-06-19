package com.example.reyohoho

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * Информация о скачиваемом файле для отображения прогресса
 */
data class DownloadInfo(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long = 0,
    val downloadedSize: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val filePath: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Singleton класс для управления загрузками в приложении
 */
class AppDownloadManager private constructor(private val context: Context) {
    private val TAG = "AppDownloadManager"
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    
    // Список активных загрузок
    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadInfo>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, DownloadInfo>> = _activeDownloads.asStateFlow()
    
    // Список завершенных загрузок
    private val _downloadHistory = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloadHistory: StateFlow<List<DownloadInfo>> = _downloadHistory.asStateFlow()
    
    // Каталог для загрузок
    private val appDownloadDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "ReYohoho"
    )
    
    // Приемник для завершенных загрузок
    private val downloadCompleteReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            
            if (downloadId == -1L) return
            
            // Проверяем статус загрузки
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                
                val downloadStatus = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                    else -> DownloadStatus.FAILED
                }
                
                handleDownloadCompleted(downloadId, downloadStatus)
            }
            
            cursor.close()
        }
    }
    
    init {
        // Создаем каталог для загрузок, если его нет
        if (!appDownloadDir.exists()) {
            appDownloadDir.mkdirs()
        }
        
        // Регистрируем BroadcastReceiver для отслеживания завершенных загрузок
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        context.registerReceiver(downloadCompleteReceiver, filter)
        
        // Загружаем историю загрузок
        loadDownloadHistory()
    }
    
    /**
     * Запуск скачивания файла
     */
    fun downloadFile(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?): Long {
        try {
            // Создаем имя файла, если оно не указано
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                ?: SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + "_download"
            
            // Создаем запрос на скачивание
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                // Добавляем заголовки
                userAgent?.let { addRequestHeader("User-Agent", it) }
                
                // Настраиваем уведомления
                setTitle(fileName)
                setDescription("Загрузка через ReYohoho")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // Устанавливаем каталог для скачивания
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "ReYohoho/$fileName"
                    )
                } else {
                    val file = File(appDownloadDir, fileName)
                    setDestinationUri(Uri.fromFile(file))
                }
                
                // Разрешаем скачивание по мобильной сети
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            
            // Запускаем скачивание
            val downloadId = downloadManager.enqueue(request)
            
            // Создаем информацию о загрузке
            val downloadInfo = DownloadInfo(
                id = downloadId,
                url = url,
                fileName = fileName,
                mimeType = mimeType ?: "application/octet-stream",
                status = DownloadStatus.IN_PROGRESS,
                filePath = File(appDownloadDir, fileName).absolutePath
            )
            
            // Добавляем в список активных загрузок
            val updatedDownloads = _activeDownloads.value.toMutableMap()
            updatedDownloads[downloadId] = downloadInfo
            _activeDownloads.value = updatedDownloads
            
            return downloadId
        } catch (e: Exception) {
            return -1L
        }
    }
    
    /**
     * Отмена загрузки
     */
    fun cancelDownload(downloadId: Long) {
        try {
            downloadManager.remove(downloadId)
            
            // Обновляем статус загрузки
            val updatedDownloads = _activeDownloads.value.toMutableMap()
            val download = updatedDownloads[downloadId]?.copy(status = DownloadStatus.CANCELLED)
            
            if (download != null) {
                // Добавляем в историю
                val updatedHistory = _downloadHistory.value.toMutableList()
                updatedHistory.add(download)
                _downloadHistory.value = updatedHistory
                
                // Удаляем из активных загрузок
                updatedDownloads.remove(downloadId)
                _activeDownloads.value = updatedDownloads
                
                // Сохраняем историю
                saveDownloadHistory()
            }
        } catch (e: Exception) {
        }
    }
    
    /**
     * Получение прогресса загрузки
     */
    fun updateDownloadProgress(downloadId: Long) {
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                
                val status = cursor.getInt(statusIndex)
                val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                val bytesTotal = cursor.getLong(bytesTotalIndex)
                var localUri = ""
                
                if (localUriIndex >= 0) {
                    localUri = cursor.getString(localUriIndex) ?: ""
                }
                
                // Получаем текущую информацию о загрузке
                val currentDownload = _activeDownloads.value[downloadId] ?: return
                
                // Обновляем статус загрузки
                val newStatus = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                    else -> DownloadStatus.IN_PROGRESS
                }
                
                // Обновляем информацию о загрузке
                val updatedDownload = currentDownload.copy(
                    fileSize = bytesTotal,
                    downloadedSize = bytesDownloaded,
                    status = newStatus,
                    filePath = if (localUri.isNotEmpty()) localUri else currentDownload.filePath
                )
                
                // Обновляем список активных загрузок
                val updatedDownloads = _activeDownloads.value.toMutableMap()
                updatedDownloads[downloadId] = updatedDownload
                _activeDownloads.value = updatedDownloads
                
                if (newStatus == DownloadStatus.COMPLETED || newStatus == DownloadStatus.FAILED) {
                    handleDownloadCompleted(downloadId, newStatus)
                }
            }
            
            cursor.close()
        } catch (e: Exception) {
        }
    }
    
    /**
     * Обработка завершенной загрузки
     */
    private fun handleDownloadCompleted(downloadId: Long, status: DownloadStatus) {
        // Получаем информацию о загрузке
        val download = _activeDownloads.value[downloadId] ?: return
        
        // Обновляем статус
        val updatedDownload = download.copy(status = status)
        
        // Добавляем в историю загрузок
        val updatedHistory = _downloadHistory.value.toMutableList()
        updatedHistory.add(0, updatedDownload)
        _downloadHistory.value = updatedHistory
        
        // Удаляем из активных загрузок
        val updatedDownloads = _activeDownloads.value.toMutableMap()
        updatedDownloads.remove(downloadId)
        _activeDownloads.value = updatedDownloads
        
        // Сохраняем историю
        saveDownloadHistory()
    }
    
    /**
     * Сохранение истории загрузок
     */
    private fun saveDownloadHistory() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Сохраняем количество элементов
            editor.putInt(PREF_HISTORY_COUNT, _downloadHistory.value.size)
            
            // Сохраняем каждый элемент
            _downloadHistory.value.forEachIndexed { index, info ->
                editor.putLong("$PREF_HISTORY_ID$index", info.id)
                editor.putString("$PREF_HISTORY_URL$index", info.url)
                editor.putString("$PREF_HISTORY_FILENAME$index", info.fileName)
                editor.putString("$PREF_HISTORY_MIMETYPE$index", info.mimeType)
                editor.putLong("$PREF_HISTORY_FILESIZE$index", info.fileSize)
                editor.putLong("$PREF_HISTORY_DOWNLOADED$index", info.downloadedSize)
                editor.putInt("$PREF_HISTORY_STATUS$index", info.status.ordinal)
                editor.putString("$PREF_HISTORY_PATH$index", info.filePath)
                editor.putLong("$PREF_HISTORY_TIMESTAMP$index", info.timestamp)
            }
            
            editor.apply()
        } catch (e: Exception) {
        }
    }
    
    /**
     * Загрузка истории загрузок
     */
    private fun loadDownloadHistory() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt(PREF_HISTORY_COUNT, 0)
            
            val history = mutableListOf<DownloadInfo>()
            
            for (i in 0 until count) {
                val id = prefs.getLong("$PREF_HISTORY_ID$i", 0)
                val url = prefs.getString("$PREF_HISTORY_URL$i", "") ?: ""
                val fileName = prefs.getString("$PREF_HISTORY_FILENAME$i", "") ?: ""
                val mimeType = prefs.getString("$PREF_HISTORY_MIMETYPE$i", "") ?: ""
                val fileSize = prefs.getLong("$PREF_HISTORY_FILESIZE$i", 0)
                val downloadedSize = prefs.getLong("$PREF_HISTORY_DOWNLOADED$i", 0)
                val statusOrdinal = prefs.getInt("$PREF_HISTORY_STATUS$i", 0)
                val filePath = prefs.getString("$PREF_HISTORY_PATH$i", "") ?: ""
                val timestamp = prefs.getLong("$PREF_HISTORY_TIMESTAMP$i", 0)
                
                val status = try {
                    DownloadStatus.values()[statusOrdinal]
                } catch (e: Exception) {
                    DownloadStatus.COMPLETED
                }
                
                // Проверяем существование файла
                val fileExists = File(filePath).exists()
                
                // Добавляем только если файл существует и загрузка была завершена
                if ((status == DownloadStatus.COMPLETED && fileExists) || status != DownloadStatus.COMPLETED) {
                    val info = DownloadInfo(
                        id = id,
                        url = url,
                        fileName = fileName,
                        mimeType = mimeType,
                        fileSize = fileSize,
                        downloadedSize = downloadedSize,
                        status = if (fileExists) status else DownloadStatus.FAILED,
                        filePath = filePath,
                        timestamp = timestamp
                    )
                    
                    history.add(info)
                }
            }
            
            _downloadHistory.value = history.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
        }
    }
    
    /**
     * Получение списка всех скачанных файлов
     */
    fun getDownloadedFiles(): List<File> {
        return if (appDownloadDir.exists() && appDownloadDir.isDirectory) {
            appDownloadDir.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    /**
     * Удаление файла из истории загрузок
     */
    fun deleteFile(downloadInfo: DownloadInfo): Boolean {
        try {
            // Удаляем файл
            val file = File(downloadInfo.filePath)
            val fileDeleted = if (file.exists()) file.delete() else true

            // Удаляем запись из истории
            val updatedHistory = _downloadHistory.value.toMutableList()
            updatedHistory.removeIf { it.id == downloadInfo.id }
            _downloadHistory.value = updatedHistory

            // Сохраняем изменения
            saveDownloadHistory()

            return fileDeleted
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Открытие файла
     */
    fun openFile(downloadInfo: DownloadInfo, activity: Activity?) {
        try {
            val file = File(downloadInfo.filePath)
            if (!file.exists()) return
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val authority = "${context.packageName}.provider"
                androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            } else {
                Uri.fromFile(file)
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, downloadInfo.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
        }
    }
    
    companion object {
        private const val PREFS_NAME = "download_history"
        private const val PREF_HISTORY_COUNT = "history_count"
        private const val PREF_HISTORY_ID = "history_id_"
        private const val PREF_HISTORY_URL = "history_url_"
        private const val PREF_HISTORY_FILENAME = "history_filename_"
        private const val PREF_HISTORY_MIMETYPE = "history_mimetype_"
        private const val PREF_HISTORY_FILESIZE = "history_filesize_"
        private const val PREF_HISTORY_DOWNLOADED = "history_downloaded_"
        private const val PREF_HISTORY_STATUS = "history_status_"
        private const val PREF_HISTORY_PATH = "history_path_"
        private const val PREF_HISTORY_TIMESTAMP = "history_timestamp_"
        
        @Volatile
        private var instance: AppDownloadManager? = null
        
        fun getInstance(context: Context): AppDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: AppDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
} 