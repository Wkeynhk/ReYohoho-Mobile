package com.example.reyohoho.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * Форматирование размера файла для отображения
 */
fun formatFileSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format("%.2f ГБ", gb)
        mb >= 1 -> String.format("%.2f МБ", mb)
        kb >= 1 -> String.format("%.2f КБ", kb)
        else -> "$size Б"
    }
}

/**
 * Форматирование даты для отображения
 */
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Запись строки в лог-файл в папку reyohoho (Download/ReYohoho/log.txt)
 */
fun logToFile(message: String) {
    try {
        val logDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ReYohoho")
        if (!logDir.exists()) logDir.mkdirs()
        val logFile = File(logDir, "log.txt")
        val writer = FileWriter(logFile, true)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        writer.append("[$timestamp] $message\n")
        writer.close()
    } catch (e: IOException) {
        // Игнорируем ошибки логирования
    }
} 