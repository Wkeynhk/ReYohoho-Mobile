package com.example.reyohoho.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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