package com.example.reyohoho

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
        
        /**
         * Инициализация блокировщика: загрузка списка доменов из RuAdList+EasyList
         */
        suspend fun initialize() {
            if (isInitialized) return
            
            try {
                Log.d(TAG, "Начало инициализации AdBlocker с RuAdList+EasyList")
                
                withContext(Dispatchers.IO) {
                    val domains = fetchFilterList(EASYLIST_URL)
                    Log.d(TAG, "Загружено ${domains.size} доменов из фильтр-листа")
                    
                    domains.forEach { domain ->
                        blockingDomains[domain] = true
                    }
                }
                
                isInitialized = true
                Log.d(TAG, "AdBlocker успешно инициализирован")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при инициализации AdBlocker: ${e.message}")
                // Загружаем базовые домены для блокировки
                loadFallbackDomains()
            }
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
                "adsystem", "adtech", "adform", "popunder", "clickunder"
            )
            
            for (indicator in adIndicators) {
                if (url.contains(indicator)) {
                    return true
                }
            }
            
            return false
        }
    }
} 