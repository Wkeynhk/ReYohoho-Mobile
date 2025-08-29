package com.example.reyohoho

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Мощный блокировщик рекламы, основанный на RuAdList+EasyList
 */
class AdBlocker {
    companion object {
        private const val TAG = "PowerAdBlocker"
        private const val EASYLIST_URL = "https://easylist-downloads.adblockplus.org/ruadlist+easylist.txt"
        private const val LOCAL_DOMAINS_FILE = "adblock_domains.txt"
        
        // Паттерны для извлечения доменов из RuAdList
        private val DOMAIN_PATTERN = Pattern.compile("^\\|\\|([a-z0-9][a-z0-9.-]*\\.(?:[a-z]{2,}|xn--[a-z0-9]+))\\^")
        private val EXACT_DOMAIN_PATTERN = Pattern.compile("^\\|\\|([a-z0-9][a-z0-9.-]*\\.(?:[a-z]{2,}|xn--[a-z0-9]+))\\^\$")
        private val SUBDOMAIN_PATTERN = Pattern.compile("^([a-z0-9][a-z0-9.-]*)\\.([a-z]{2,}|xn--[a-z0-9]+)$")
        
        // Хранилище для доменов из фильтр-листа
        private val blockingDomains = ConcurrentHashMap<String, Boolean>()

        // Безопасные домены, которые никогда не блокируем
        private val SAFE_DOMAINS = setOf(
            "youtube.com", "youtu.be", "vimeo.com", "rutube.ru", "ok.ru", 
            "vk.com", "video.mail.ru", "googleapis.com", "google.com",
            "player.vimeo.com", "cdnjs.cloudflare.com", "cdn.jsdelivr.net", "unpkg.com",
            "fonts.googleapis.com", "fonts.gstatic.com", "ajax.googleapis.com", 
            "rawgit.com", "code.jquery.com", "github.io", "cloudflare.com",
            "jwpsrv.com", "yastatic.net", "yandex.ru", "jsdelivr.net",
            "github.com", "apple.com", "microsoft.com", "amazon.com",
            "avatars.mds.yandex.net", "st.kp.yandex.net", "b-cdn.net", "reyohoho.b-cdn.net"
        )



        // Флаг инициализации
        @Volatile
        private var isInitialized = false
        
        // Режим загрузки доменов
        @Volatile
        private var useLocalFile = false
        
        /**
         * Инициализация блокировщика: загрузка списка доменов
         */
        suspend fun initialize(context: Context) {
            if (isInitialized) return
            
            try {
                Log.d(TAG, "Начало инициализации AdBlocker")
                
                withContext(Dispatchers.IO) {
                    if (useLocalFile) {
                        // Режим локального файла
                        val localDomains = loadLocalDomains(context)
                        if (localDomains.isNotEmpty()) {
                            Log.d(TAG, "Загружено ${localDomains.size} доменов из локального файла")
                            localDomains.forEach { domain ->
                                blockingDomains[domain] = true
                            }
                        } else {
                            Log.w(TAG, "Локальный файл пуст, загружаем резервный список")
                            loadFallbackDomains()
                        }
                    } else {
                        // Режим интернета (по умолчанию)
                        try {
                            val onlineDomains = fetchFilterList(EASYLIST_URL)
                            if (onlineDomains.isNotEmpty()) {
                                Log.d(TAG, "Загружено ${onlineDomains.size} доменов из интернета")
                                onlineDomains.forEach { domain ->
                                    blockingDomains[domain] = true
                                }
                            } else {
                                throw Exception("Пустой список доменов из интернета")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Ошибка загрузки из интернета: ${e.message}, пробуем локальный файл")
                            val localDomains = loadLocalDomains(context)
                            if (localDomains.isNotEmpty()) {
                                Log.d(TAG, "Загружено ${localDomains.size} доменов из локального файла")
                                localDomains.forEach { domain ->
                                    blockingDomains[domain] = true
                                }
                            } else {
                                Log.w(TAG, "Локальный файл тоже пуст, загружаем резервный список")
                                loadFallbackDomains()
                            }
                        }
                    }
                }
                
                isInitialized = true
                Log.d(TAG, "AdBlocker успешно инициализирован")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при инициализации AdBlocker: ${e.message}")
                loadFallbackDomains()
            }
        }
        
        /**
         * Переключение режима загрузки доменов
         */
        fun setUseLocalFile(useLocal: Boolean) {
            useLocalFile = useLocal
            Log.d(TAG, "Режим загрузки доменов изменен на: ${if (useLocal) "локальный файл" else "интернет"}")
        }
        
        /**
         * Получение текущего режима загрузки
         */
        fun isUsingLocalFile(): Boolean {
            return useLocalFile
        }
        
        /**
         * Инициализация с учетом сохраненных настроек
         */
        suspend fun initializeWithSettings(context: Context) {
            val settingsManager = com.example.reyohoho.ui.SettingsManager.getInstance(context)
            
            if (settingsManager.isAdblockRememberChoiceEnabled()) {
                // Если включено запоминание выбора, используем сохраненный источник
                val preferredSource = settingsManager.getAdblockPreferredSource()
                useLocalFile = (preferredSource == com.example.reyohoho.ui.SettingsManager.ADBLOCK_SOURCE_LOCAL)
                Log.d(TAG, "Используем сохраненный источник: $preferredSource")
            } else {
                // Если запоминание отключено, проверяем доступность источников
                val internetAvailable = checkInternetSourceAvailable()
                val localAvailable = checkLocalFileAvailable(context)
                
                if (internetAvailable) {
                    useLocalFile = false
                    Log.d(TAG, "Интернет доступен, используем онлайн-источник")
                } else if (localAvailable) {
                    useLocalFile = true
                    Log.d(TAG, "Интернет недоступен, используем локальный файл")
                } else {
                    useLocalFile = false
                    Log.w(TAG, "Оба источника недоступны, используем резервный список")
                }
            }
            
            initialize(context)
        }
        
        /**
         * Установка предпочтительного источника с сохранением в настройках
         */
        fun setPreferredSource(context: Context, useLocal: Boolean) {
            val settingsManager = com.example.reyohoho.ui.SettingsManager.getInstance(context)
            val source = if (useLocal) {
                com.example.reyohoho.ui.SettingsManager.ADBLOCK_SOURCE_LOCAL
            } else {
                com.example.reyohoho.ui.SettingsManager.ADBLOCK_SOURCE_INTERNET
            }
            
            settingsManager.setAdblockPreferredSource(source)
            setUseLocalFile(useLocal)
            Log.d(TAG, "Установлен предпочтительный источник: $source")
        }
        
        /**
         * Проверка доступности интернет-источника
         */
        suspend fun checkInternetSourceAvailable(): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val connection = URL(EASYLIST_URL).openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 10000
                    connection.connect()
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Интернет-источник недоступен: ${e.message}")
                    false
                }
            }
        }
        
        /**
         * Проверка доступности локального файла
         */
        fun checkLocalFileAvailable(context: Context): Boolean {
            return try {
                context.assets.open(LOCAL_DOMAINS_FILE).use { inputStream ->
                    inputStream.available() > 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Локальный файл недоступен: ${e.message}")
                false
            }
        }
        
        /**
         * Принудительная перезагрузка доменов
         */
        suspend fun reloadDomains(context: Context) {
            try {
                Log.d(TAG, "Принудительная перезагрузка доменов")
                
                // Очищаем текущий список доменов
                blockingDomains.clear()
                isInitialized = false
                
                // Переинициализируем с новыми настройками
                initialize(context)
                
                Log.d(TAG, "Домены успешно перезагружены")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при перезагрузке доменов: ${e.message}")
                loadFallbackDomains()
            }
        }
        
        /**
         * Загрузка доменов из локального файла assets
         */
        private fun loadLocalDomains(context: Context): Set<String> {
            val domains = HashSet<String>()
            var totalLines = 0
            
            try {
                context.assets.open(LOCAL_DOMAINS_FILE).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String?
                        
                        while (reader.readLine().also { line = it } != null) {
                            totalLines++
                            line?.let { l ->
                                // Проходим только стандартные правила блокировки
                                if (!l.startsWith("!") && !l.startsWith("[") && 
                                    !l.startsWith("@@") && l.isNotBlank()) {
                                    
                                    // Попытка извлечь домен из правила
                                    extractDomain(l)?.let { domain ->
                                        domains.add(domain)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Обработано $totalLines строк из локального файла")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при загрузке локального файла: ${e.message}")
            }
            
            return domains
        }
        
        /**
         * Загрузка резервного списка доменов в случае ошибки загрузки основного списка
         */
        private fun loadFallbackDomains() {
            Log.d(TAG, "Загрузка резервного списка доменов")
            
            val fallbackDomains = listOf(
                "an.yandex.ru", "mc.yandex.ru", "ads.yandex.ru", "adfox.ru", 
                "ad.mail.ru", "ads.vk.com", "adriver.ru", "doubleclick.net", 
                "googleadservices.com", "googlesyndication.com", "adocean.pl",
                "smi2.ru", "smi2.net", "marketgid.com", "relap.io", "lentainform.com", 
                "directadvert.ru", "adhigh.net", "adnetic.io", "adwizard.ru", 
                "bidswitch.net", "buzzoola.com", "nativeroll.tv", "adspend.ru", 
                "adlift.ru", "sovetnik.net", "nativeads.ru", "mediametrics.ru", 
                "mytarget.ru", "ad.rambler.ru"
            )
            
            fallbackDomains.forEach { domain ->
                blockingDomains[domain] = true
            }
            
            isInitialized = true
        }
        
        /**
         * Загрузка и парсинг фильтр-листа для извлечения доменов
         */
        private fun fetchFilterList(url: String): Set<String> {
            val domains = HashSet<String>()
            var totalLines = 0
            
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                
                BufferedReader(InputStreamReader(connection.getInputStream())).use { reader ->
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        totalLines++
                        line?.let { l ->
                            // Проходим только стандартные правила блокировки
                            if (!l.startsWith("!") && !l.startsWith("[") && 
                                !l.startsWith("@@") && l.isNotBlank()) {
                                
                                // Попытка извлечь домен из правила
                                extractDomain(l)?.let { domain ->
                                    domains.add(domain)
                                }
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Обработано $totalLines строк из фильтр-листа")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при загрузке фильтр-листа: ${e.message}")
            }
            
            return domains
        }
        
        /**
         * Извлечение домена из правила фильтра
         */
        private fun extractDomain(rule: String): String? {
            // Ищем точное соответствие домена (||example.com^)
            val exactMatcher = EXACT_DOMAIN_PATTERN.matcher(rule)
            if (exactMatcher.find()) {
                return exactMatcher.group(1)
            }
            
            // Ищем любое соответствие домена
            val domainMatcher = DOMAIN_PATTERN.matcher(rule)
            if (domainMatcher.find()) {
                return domainMatcher.group(1)
            }
            
            // Проверка на простую запись домена
            if (!rule.contains("/") && !rule.contains("*") && !rule.contains("?")) {
                val subdomainMatcher = SUBDOMAIN_PATTERN.matcher(rule)
                if (subdomainMatcher.matches()) {
                    return rule
                }
            }
            
            return null
        }
        
        /**
         * Проверка URL на принадлежность к рекламным ресурсам
         */
        fun isAd(url: String): Boolean {
            try {
                // Если блокировщик не был инициализирован - блокируем только очевидную рекламу
                if (!isInitialized) {
                    return isObviousAdUrl(url)
                }
                
                // Нормализуем URL для проверки
                val normalizedUrl = url.lowercase()
                
                // Логируем URL для отладки (можно убрать на продакшене)
                // Log.d(TAG, "Проверка URL: $normalizedUrl")
                
                // Извлекаем домен из URL
                val domain = extractUrlDomain(normalizedUrl) ?: return false
                
                // Проверка на безопасные домены (никогда не блокировать)
                for (safeDomain in SAFE_DOMAINS) {
                    if (domain.endsWith(safeDomain)) {
                        return false
                    }
                }
                
                // Проверяем домен в нашем списке блокировки
                if (blockingDomains.containsKey(domain)) {
                    Log.d(TAG, "Блокировка по домену: $domain, URL: $normalizedUrl")
                    return true
                }
                
                // Если домен не найден напрямую, проверяем его родительский домен
                // Например, если ads.example.com не найден, проверяем example.com
                val parts = domain.split('.')
                if (parts.size > 2) {
                    for (i in 1 until parts.size - 1) {
                        val parentDomain = parts.subList(i, parts.size).joinToString(".")
                        if (blockingDomains.containsKey(parentDomain)) {
                            Log.d(TAG, "Блокировка по родительскому домену: $parentDomain, URL: $normalizedUrl")
                    return true
                        }
                    }
                }
                
                // Проверка на очевидные рекламные URL в качестве дополнительной защиты
                return isObviousAdUrl(normalizedUrl)
                
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при проверке URL: ${e.message}")
                return false
            }
        }
        
        /**
         * Извлечение домена из URL
         */
        private fun extractUrlDomain(url: String): String? {
            try {
                val cleanUrl = url.lowercase()
                    .replace("http://", "")
                    .replace("https://", "")
                    .replace("www.", "")
                
                // Отделяем домен от пути
                val domainEnd = cleanUrl.indexOf('/')
                val domain = if (domainEnd > 0) cleanUrl.substring(0, domainEnd) else cleanUrl
                
                // Обрабатываем порт, если он есть
                val portIndex = domain.indexOf(':')
                return if (portIndex > 0) domain.substring(0, portIndex) else domain
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при извлечении домена: ${e.message}")
                return null
            }
        }
        
        /**
         * Проверка на очевидные рекламные URL
         */
        private fun isObviousAdUrl(url: String): Boolean {
            // Проверка на очевидные рекламные индикаторы
            val adIndicators = listOf(
                "/ads/", "/ad/", "/adv/", "/advert", "/advertising/", 
                "/banners/", "/banner/", "ad.js", "ads.js", "analytics", 
                "tracker", "pixel.gif", "ad_", "ads_", "adserv", "adserver", 
                "adsystem", "adtech", "adform", "popunder", "clickunder",
                "vast.xml", "ad-rotator", "stats?viewId"
            )
            
            for (indicator in adIndicators) {
                if (url.contains(indicator)) {
                    return true
                }
            }
            
            return false
        }
        
        /**
         * Получение количества загруженных доменов для отладки
         */
        fun getLoadedDomainsCount(): Int {
            return blockingDomains.size
        }
    }
} 