package com.example.reyohoho.ui

import android.util.Log
import java.text.Collator
import java.util.*
import kotlin.math.max

/**
 * Утилиты для работы с файлами торрентов и определения сериалов
 */
object TorrentFileUtils {
    private const val TAG = "TorrentFileUtils"

    data class TorrentFile(
        val index: Int,
        val name: String,
        val path: String,
        val size: Long,
        val isVideo: Boolean = false,
        val episodeInfo: EpisodeInfo? = null
    )

    data class EpisodeInfo(
        val season: Int?,
        val episode: Int?,
        val title: String? = null,
        val quality: String? = null
    )

    /**
     * Определяет сезон и серию по названию файла
     */
    fun parseEpisodeInfo(filename: String): EpisodeInfo? {
        Log.d(TAG, "Парсинг названия файла: $filename")
        
        val cleanName = filename.lowercase()
        var season: Int? = null
        var episode: Int? = null
        var quality: String? = null
        
        // Строгие паттерны для поиска сезона и серии (только явные форматы)
        val patterns = listOf(
            // S01E01, S1E1, s01e01 (только с буквами S и E)
            Regex("""s(\d{1,2})e(\d{1,2})"""),
            // Season 01 Episode 01, Season 1 Episode 1 (только полные слова)
            Regex("""season\s+(\d{1,2})\s+episode\s+(\d{1,2})""")
        )
        
        // Пробуем найти совпадения
        for (pattern in patterns) {
            val match = pattern.find(cleanName)
            if (match != null) {
                try {
                    season = match.groupValues[1].toInt()
                    episode = match.groupValues[2].toInt()
                    Log.d(TAG, "Найдено: Сезон $season, Серия $episode")
                    break
                } catch (e: NumberFormatException) {
                    Log.w(TAG, "Ошибка парсинга чисел: ${e.message}")
                }
            }
        }
        
        // Определяем качество
        quality = detectQuality(filename)
        
        return if (season != null && episode != null) {
            EpisodeInfo(season, episode, null, quality)
        } else {
            // Если не нашли стандартные паттерны, попробуем найти хотя бы номера
            val numbers = Regex("""\d+""").findAll(cleanName).map { it.value.toInt() }.toList()
            if (numbers.size >= 2) {
                // Предполагаем, что первое число - сезон, второе - серия
                EpisodeInfo(numbers[0], numbers[1], null, quality)
            } else {
                null
            }
        }
    }

    /**
     * Определяет качество видео по названию файла
     */
    private fun detectQuality(filename: String): String? {
        val cleanName = filename.lowercase()
        
        return when {
            cleanName.contains("2160p") || cleanName.contains("4k") -> "4K"
            cleanName.contains("1080p") -> "1080p"
            cleanName.contains("720p") -> "720p"
            cleanName.contains("480p") -> "480p"
            cleanName.contains("1440p") -> "1440p"
            cleanName.contains("uhd") -> "UHD"
            cleanName.contains("hd") && !cleanName.contains("hdtv") -> "HD"
            else -> null
        }
    }

    /**
     * Проверяет, является ли файл видеофайлом
     */
    fun isVideoFile(filename: String): Boolean {
        val videoExtensions = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", 
            "mpg", "mpeg", "3gp", "asf", "rm", "rmvb", "vob", "ts", "m2ts"
        )
        
        val extension = filename.substringAfterLast('.', "").lowercase()
        return videoExtensions.contains(extension)
    }

    /**
     * Сортирует файлы по сезонам и сериям
     */
    fun sortFilesByEpisode(files: List<TorrentFile>): List<TorrentFile> {
        val collator = Collator.getInstance(Locale("ru", "RU"))
        
        return files.sortedWith { file1, file2 ->
            val episode1 = file1.episodeInfo
            val episode2 = file2.episodeInfo
            
            when {
                // Если у обоих файлов есть информация о сериях
                episode1 != null && episode2 != null -> {
                    val seasonComparison = (episode1.season ?: 0).compareTo(episode2.season ?: 0)
                    if (seasonComparison != 0) {
                        seasonComparison
                    } else {
                        (episode1.episode ?: 0).compareTo(episode2.episode ?: 0)
                    }
                }
                // Если только у первого есть информация о серии
                episode1 != null && episode2 == null -> -1
                // Если только у второго есть информация о серии
                episode1 == null && episode2 != null -> 1
                // Если у обоих нет информации о сериях, сортируем по имени
                else -> collator.compare(file1.name, file2.name)
            }
        }
    }

    /**
     * Группирует файлы по сезонам
     */
    fun groupFilesBySeason(files: List<TorrentFile>): Map<Int?, List<TorrentFile>> {
        return files.groupBy { it.episodeInfo?.season }
    }

    /**
     * Форматирует название файла без номера серии
     */
    fun formatEpisodeTitle(file: TorrentFile): String {
        return file.name.substringBeforeLast('.')
    }
    
    /**
     * Получает номер серии для отображения над файлом
     */
    fun getEpisodeNumber(file: TorrentFile): String? {
        val episode = file.episodeInfo
        return when {
            episode?.episode != null -> {
                "Серия ${episode.episode}"
            }
            else -> null
        }
    }

    /**
     * Форматирует размер файла
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 0) return "Неизвестно"
        
        val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.1f %s", size, units[unitIndex])
    }

    /**
     * Определяет приоритетный файл для автовоспроизведения
     * (самый большой видеофайл или первый по порядку серий)
     */
    fun findPriorityFile(files: List<TorrentFile>): TorrentFile? {
        val videoFiles = files.filter { it.isVideo }
        
        if (videoFiles.isEmpty()) return files.firstOrNull()
        
        // Сначала пробуем найти первую серию первого сезона
        val withEpisodeInfo = videoFiles.filter { it.episodeInfo != null }
        
        if (withEpisodeInfo.isNotEmpty()) {
            val sorted = sortFilesByEpisode(withEpisodeInfo)
            return sorted.firstOrNull()
        }
        
        // Если нет информации о сериях, берем самый большой файл
        return videoFiles.maxByOrNull { it.size }
    }

    /**
     * Фильтрует только видеофайлы и добавляет информацию о сериях
     */
    fun processVideoFiles(rawFiles: List<TorrentFile>): List<TorrentFile> {
        Log.d(TAG, "Обработка ${rawFiles.size} файлов")
        
        // Сначала попробуем найти точные видеофайлы
        val videoFiles = rawFiles.filter { isVideoFile(it.name) }.map { file ->
            Log.d(TAG, "Найден видеофайл: ${file.name}")
            file.copy(
                isVideo = true,
                episodeInfo = parseEpisodeInfo(file.name)
            )
        }
        
        // Если видеофайлы найдены, возвращаем их
        if (videoFiles.isNotEmpty()) {
            Log.d(TAG, "Обработано ${videoFiles.size} видеофайлов")
            return videoFiles
        }
        
        // Если точных видеофайлов не найдено, попробуем найти файлы, которые могут быть видео
        val possibleVideoFiles = rawFiles.filter { file ->
            val name = file.name.lowercase()
            val size = file.size
            
            // Исключаем явно не видео файлы
            val isNotVideo = name.endsWith(".txt") || name.endsWith(".nfo") || 
                           name.endsWith(".jpg") || name.endsWith(".png") ||
                           name.endsWith(".srt") || name.endsWith(".sub") ||
                           name.contains("sample") || size < 10 * 1024 * 1024 // меньше 10MB
            
            !isNotVideo
        }.map { file ->
            Log.d(TAG, "Возможный видеофайл: ${file.name}")
            file.copy(
                isVideo = true,
                episodeInfo = parseEpisodeInfo(file.name)
            )
        }
        
        Log.d(TAG, "Найдено ${possibleVideoFiles.size} возможных видеофайлов")
        return possibleVideoFiles
    }
}