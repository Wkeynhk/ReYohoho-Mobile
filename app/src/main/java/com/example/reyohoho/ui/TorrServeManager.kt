package com.example.reyohoho.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import com.example.reyohoho.ui.logToFile
import kotlinx.coroutines.CompletableDeferred

/**
 * Класс для управления взаимодействием с TorrServe
 * Адаптирован на основе новой реализации
 */
class TorrServeManager(private val context: Context) {

    companion object {
        private const val TAG = "TorrServeManager"
        private const val CONNECTION_TIMEOUT = 5000 // 5 секунд
        private const val READ_TIMEOUT = 30000 // 30 секунд

        // Типы API TorrServe
        enum class ApiVersion {
            V1, V2, CUSTOM, MATRIX, UNKNOWN
        }

        @Volatile
        private var INSTANCE: TorrServeManager? = null

        fun getInstance(context: Context): TorrServeManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TorrServeManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val settingsManager = SettingsManager.getInstance(context)
    private var isDebugEnabled = true // Включаем расширенное логирование
    private var detectedApiVersion: ApiVersion = ApiVersion.UNKNOWN
    private var isMatrixServer = false
    private val isNetworkRequestInProgress = AtomicBoolean(false)

    /**
     * Возвращает базовый URL TorrServe
     */
    private fun getTorrServeBaseUrl(): String {
        val externalUrl = settingsManager.getExternalTorrServeUrl()
        return if (externalUrl.endsWith("/")) {
            externalUrl.dropLast(1)
        } else {
            externalUrl
        }
    }

    /**
     * Формирует полный URL API
     */
    private fun getApiUrl(path: String): String {
        val baseUrl = getTorrServeBaseUrl()
        return if (path.startsWith("/")) {
            "$baseUrl$path"
        } else {
            "$baseUrl/$path"
        }
    }

    /**
     * Логирование с детальной информацией - в Logcat и в файл
     */
    private fun logDebug(message: String, data: Any? = null) {
        if (isDebugEnabled) {
            val logMsg = if (data != null) {
                "$message: $data"
            } else {
                message
            }
            Log.d(TAG, logMsg)
            
            // Логируем в файл с префиксом TorrServe
            logToFile("[TorrServe] $logMsg")
        }
    }

    /**
     * Проверяет, есть ли интернет-соединение
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            // Для старых версий Android
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    /**
     * Определяет версию API TorrServe с расширенными попытками
     */
    suspend fun detectApiVersion(): ApiVersion = withContext(Dispatchers.IO) {
        val baseUrl = getTorrServeBaseUrl()
        logDebug("Определение версии API TorrServe по адресу: $baseUrl")
        
        if (!isNetworkAvailable()) {
            logDebug("Нет подключения к интернету при определении версии API")
            return@withContext ApiVersion.UNKNOWN
        }

        try {
            // Сначала проверим, что сервер вообще доступен
            val serverAvailable = checkBasicConnection(baseUrl)
            if (!serverAvailable) {
                logDebug("Базовое соединение с сервером недоступно: $baseUrl")
                return@withContext ApiVersion.UNKNOWN
            }
            
            logDebug("Начинаем определение версии API TorrServe (базовое соединение установлено)")
            
            // Проверка Matrix API
            try {
                // Сначала пробуем через /echo - это работает для Matrix.134
                val echoResponse = executeRequest("GET", getApiUrl("/echo"), timeout = 3000)
                if (echoResponse != null && echoResponse.lowercase().contains("matrix")) {
                    logDebug("Обнаружен Matrix API через /echo (response=${echoResponse})")
                    isMatrixServer = true
                    detectedApiVersion = ApiVersion.MATRIX
                    return@withContext ApiVersion.MATRIX
                }
                
                // Если /echo не содержит "matrix", пробуем /settings
                val response = executeRequest("GET", getApiUrl("/settings"), timeout = 5000)
                if (response != null) {
                    logDebug("Обнаружен Matrix API через /settings (response=${response.substring(0, minOf(100, response.length))})")
                    isMatrixServer = true
                    detectedApiVersion = ApiVersion.MATRIX
                    return@withContext ApiVersion.MATRIX
                }
            } catch (e: Exception) {
                logDebug("Ошибка при проверке Matrix API", e.toString())
                // Не Matrix, продолжаем проверки других версий
            }
            
            // Проверка v2 API
            try {
                val response = executeRequest("GET", getApiUrl("/torrents"), timeout = 5000)
                if (response != null) {
                    logDebug("Обнаружен V2 API")
                    detectedApiVersion = ApiVersion.V2
                    return@withContext ApiVersion.V2
                }
            } catch (e: Exception) {
                logDebug("Ошибка при проверке V2 API", e.toString())
            }
            
            // Проверка v1 API
            try {
                val response = executeRequest("GET", getApiUrl("/torrent/list"), timeout = 5000)
                if (response != null) {
                    logDebug("Обнаружен V1 API")
                    detectedApiVersion = ApiVersion.V1
                    return@withContext ApiVersion.V1
                }
            } catch (e: Exception) {
                logDebug("Ошибка при проверке V1 API", e.toString())
            }
            
            // Проверка на нестандартный API
            try {
                val response = executeRequest("GET", getApiUrl("/api/v1/torrents"), timeout = 5000)
                if (response != null) {
                    logDebug("Обнаружен CUSTOM API")
                    detectedApiVersion = ApiVersion.CUSTOM
                    return@withContext ApiVersion.CUSTOM
                }
            } catch (e: Exception) {
                logDebug("Ошибка при проверке CUSTOM API", e.toString())
            }
            
            // Попробуем другие потенциальные эндпоинты, если предыдущие проверки не сработали
            val additionalEndpoints = listOf(
                "/echo", 
                "/", 
                "/index.html", 
                "/torrents/list",
                "/torrents/get"
            )
            
            for (endpoint in additionalEndpoints) {
                try {
                    val response = executeRequest("GET", getApiUrl(endpoint), timeout = 3000)
                    if (response != null) {
                        logDebug("Сервер ответил на запрос к эндпоинту $endpoint, считаем API CUSTOM")
                        detectedApiVersion = ApiVersion.CUSTOM
                        return@withContext ApiVersion.CUSTOM
                    }
                } catch (e: Exception) {
                    logDebug("Ошибка при проверке дополнительного эндпоинта $endpoint", e.toString())
                }
            }
            
            // Если базовое соединение работает, но ни один API не определился,
            // считаем что это какой-то кастомный API
            if (serverAvailable) {
                logDebug("Сервер доступен, но API не определено. Считаем CUSTOM")
                detectedApiVersion = ApiVersion.CUSTOM
                return@withContext ApiVersion.CUSTOM
            }
            
            logDebug("Не удалось определить версию API TorrServe")
            detectedApiVersion = ApiVersion.UNKNOWN
            return@withContext ApiVersion.UNKNOWN
            
        } catch (e: Exception) {
            logDebug("Общая ошибка при определении версии API", e.toString())
            detectedApiVersion = ApiVersion.UNKNOWN
            return@withContext ApiVersion.UNKNOWN
        }
    }
    
    /**
     * Проверяет базовое соединение с сервером TorrServe
     */
    private suspend fun checkBasicConnection(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        logDebug("Проверка базового соединения с $baseUrl")
        try {
            // Пробуем просто подключиться к хосту без запроса к определенному пути
            val uri = Uri.parse(baseUrl)
            val hostname = uri.host ?: return@withContext false
            val port = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
            
            logDebug("Проверка доступности хоста: $hostname:$port")
            
            // Пробуем открыть сокет
            val socket = java.net.Socket()
            socket.soTimeout = 5000
            
            try {
                socket.connect(java.net.InetSocketAddress(hostname, port), 5000)
                val isConnected = socket.isConnected
                socket.close()
                
                if (isConnected) {
                    logDebug("Базовое TCP-соединение установлено успешно")
                    return@withContext true
                }
            } catch (e: Exception) {
                logDebug("Ошибка при проверке TCP-соединения", e.toString())
            }
            
            // Если соединение через сокет не удалось, пробуем HTTP-запрос
            try {
                val connection = URL(baseUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                connection.instanceFollowRedirects = true
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                logDebug("HTTP-запрос вернул код: $responseCode")
                return@withContext responseCode < 400
            } catch (e: Exception) {
                logDebug("Ошибка при выполнении HTTP-запроса", e.toString())
            }
            
            return@withContext false
        } catch (e: Exception) {
            logDebug("Общая ошибка при проверке базового соединения", e.toString())
            return@withContext false
        }
    }

    /**
     * Выполняет HTTP запрос и возвращает ответ в виде строки с подробным логированием
     */
    private suspend fun executeRequest(
        method: String,
        urlString: String,
        body: String? = null,
        contentType: String = "application/json",
        timeout: Int = READ_TIMEOUT
    ): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        
        try {
            // Избегаем одновременных запросов
            var attemptsCount = 0
            while (isNetworkRequestInProgress.getAndSet(true) && attemptsCount < 3) {
                logDebug("Ожидаем завершения предыдущего запроса: $urlString (попытка ${attemptsCount+1})")
                delay(300) // Задержка перед повторной попыткой
                attemptsCount++
            }
            
            if (attemptsCount >= 3) {
                logDebug("Превышено максимальное количество попыток ожидания. Выполняем запрос параллельно.")
            }
            
            logDebug("Выполняем $method запрос: $urlString" + (body?.let { " с телом: ${body.take(200)}${if (body.length > 200) "..." else ""}" } ?: ""))
            
            val startTime = System.currentTimeMillis()
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = timeout
            connection.requestMethod = method
            connection.instanceFollowRedirects = true // Автоматически следовать по редиректам
            
            // Устанавливаем User-Agent, чтобы избежать фильтрации запросов
            connection.setRequestProperty("User-Agent", "ReYohoho/1.0 Android TorrServe Client")
            
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "$contentType; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                
                try {
                    val output: OutputStream = connection.outputStream
                    output.write(body.toByteArray())
                    output.flush()
                    output.close()
                } catch (e: Exception) {
                    logDebug("Ошибка при записи тела запроса", "${e.javaClass.simpleName}: ${e.message}")
                    throw e
                }
            }
            
            // Получение времени соединения
            val connectionTime = System.currentTimeMillis() - startTime
            
            try {
                val responseCode = connection.responseCode
                val responseTime = System.currentTimeMillis() - startTime
                logDebug("Получен код ответа: $responseCode (соединение: ${connectionTime}мс, общее время: ${responseTime}мс)")
                
                // Получение заголовков ответа для отладки
                val headers = connection.headerFields
                val headersLog = StringBuilder("Заголовки ответа: ")
                for ((key, values) in headers) {
                    if (key != null) {
                        headersLog.append("$key: ${values.joinToString(", ")}, ")
                    }
                }
                logDebug(headersLog.toString())
                
                if (responseCode in 200..299) {
                    try {
                        // Проверяем Content-Type и Content-Length
                        val contentType = connection.contentType ?: ""
                        val contentLength = connection.contentLength
                        
                        // Если это видеопоток или большой файл, не читаем содержимое
                        if (contentType.startsWith("video/") || 
                            contentType.startsWith("audio/") || 
                            contentLength > 10 * 1024 * 1024) { // Если больше 10 МБ
                            
                            logDebug("Обнаружен большой контент или медиафайл: $contentType, размер: $contentLength. Не читаем содержимое.")
                            connection.inputStream.close() // Закрываем стрим, но не читаем
                            
                            // Возвращаем специальный маркер вместо содержимого
                            return@withContext "LARGE_CONTENT:$contentType:$contentLength"
                        }
                        
                        // Для обычных ответов читаем содержимое с лимитом в 1 МБ
                        val maxResponseSize = 1 * 1024 * 1024 // 1 МБ
                        val inputStream = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = StringBuilder()
                        var inputLine: String?
                        var bytesRead = 0
                        
                        while (inputStream.readLine().also { inputLine = it } != null) {
                            response.append(inputLine)
                            bytesRead += inputLine?.length ?: 0
                            
                            // Если ответ слишком большой, прерываем чтение
                            if (bytesRead > maxResponseSize) {
                                logDebug("Ответ превысил максимальный размер ($maxResponseSize байт), прерываем чтение")
                                break
                            }
                        }
                        inputStream.close()
                        
                        val responseStr = response.toString()
                        val totalTime = System.currentTimeMillis() - startTime
                        logDebug("Ответ сервера получен за ${totalTime}мс, размер: ${responseStr.length} символов, начало: ${responseStr.substring(0, minOf(100, responseStr.length))}...")
                        return@withContext responseStr
                    } catch (e: Exception) {
                        logDebug("Ошибка при чтении ответа", "${e.javaClass.simpleName}: ${e.message}")
                        throw e
                    }
                } else {
                    // Попробуем прочитать тело ошибки для диагностики
                    try {
                        val errorStream = connection.errorStream
                        if (errorStream != null) {
                            val reader = BufferedReader(InputStreamReader(errorStream))
                            val errorResponse = StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                errorResponse.append(line)
                            }
                            reader.close()
                            
                            logDebug("Тело ответа с ошибкой: ${errorResponse.toString().take(200)}")
                        }
                    } catch (e: Exception) {
                        logDebug("Не удалось прочитать тело ошибки", e.toString())
                    }
                    
                    logDebug("Неуспешный код ответа: $responseCode")
                    return@withContext null
                }
            } catch (e: Exception) {
                logDebug("Ошибка при получении ответа", "${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        } catch (e: Exception) {
            val errorDetails = when (e) {
                is java.net.ConnectException -> "Не удалось установить соединение с сервером"
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от сервера"
                is java.net.UnknownHostException -> "Неизвестный хост или проблемы с DNS"
                is java.io.IOException -> "Ошибка ввода-вывода: ${e.message}"
                else -> "${e.javaClass.simpleName}: ${e.message}"
            }
            
            logDebug("Ошибка при выполнении запроса $method $urlString", errorDetails)
            return@withContext null
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                logDebug("Ошибка при закрытии соединения", e.toString())
            }
            isNetworkRequestInProgress.set(false)
        }
    }

    /**
     * Проверяет доступность сервера TorrServe с расширенной диагностикой
     */
    suspend fun checkTorrServeAvailable(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = getTorrServeBaseUrl()
        logDebug("Проверка доступности TorrServe по адресу: $baseUrl")
        
        if (!isNetworkAvailable()) {
            logDebug("Нет подключения к интернету при проверке доступности TorrServe")
            return@withContext false
        }

        try {
            // Сначала проверяем базовое TCP/HTTP-соединение
            val basicConnectionWorks = checkBasicConnection(baseUrl)
            if (!basicConnectionWorks) {
                logDebug("Ошибка базового соединения с $baseUrl - возможно сервер выключен или адрес неверен")
                return@withContext false
            }
            
            logDebug("Базовое соединение с $baseUrl успешно установлено")
            
            // Затем определяем версию API
            val apiVersion = detectApiVersion()
            
            if (apiVersion == ApiVersion.UNKNOWN) {
                // Если версия API не определена, но базовое соединение работает,
                // попробуем прямую проверку нескольких эндпоинтов
                logDebug("API не определено, проверяем дополнительные эндпоинты")
                
                val commonEndpoints = listOf(
                    "/echo", 
                    "/", 
                    "/index.html", 
                    "/favicon.ico",
                    "/settings",
                    "/torrents",
                    "/torrent/list"
                )
                
                // Проверяем, ответит ли сервер хоть на один запрос
                for (endpoint in commonEndpoints) {
                    try {
                        val url = URL(getApiUrl(endpoint))
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.requestMethod = "HEAD"
                        
                        val responseCode = connection.responseCode
                        connection.disconnect()
                        
                        if (responseCode < 400) {
                            logDebug("Сервер ответил на запрос к $endpoint с кодом $responseCode")
                            return@withContext true
                        }
                    } catch (e: Exception) {
                        logDebug("Ошибка при проверке эндпоинта $endpoint", e.toString())
                    }
                }
                
                // Если ни один эндпоинт не ответил, но TCP-соединение работает,
                // то, возможно, сервер запущен, но API недоступно
                logDebug("TorrServe недоступен через API, хотя TCP-соединение работает. Проверьте настройки сервера.")
                return@withContext false
            }
            
            // Если API определено, всё хорошо
            logDebug("TorrServe доступен, API версия: $apiVersion, isMatrix: $isMatrixServer")
            return@withContext true
        } catch (e: Exception) {
            logDebug("Ошибка при проверке доступности TorrServe", "${e.javaClass.simpleName}: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Метод прямого запуска торрента по хешу
     */
    suspend fun startTorrentByHash(hash: String, fileIndex: Int = 0): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            logDebug("Нет подключения к интернету при запуске торрента по хешу")
            return@withContext false
        }

        try {
            logDebug("Прямой запуск торрента по хешу $hash, файл $fileIndex")
            
            // Для Matrix используем особый URL
            val url = if (isMatrixServer) {
                getApiUrl("/stream?link=$hash&index=$fileIndex&play=true")
            } else {
                getApiUrl("/stream?link=$hash&index=$fileIndex")
            }
            
            val response = executeRequest("GET", url, timeout = 10000)
            return@withContext response != null
            
        } catch (e: Exception) {
            logDebug("Ошибка при прямом запуске торрента", "${e.javaClass.simpleName}: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Добавляет магнет-ссылку в TorrServe
     */
    suspend fun addTorrent(magnetLink: String): String? = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            logDebug("Нет подключения к интернету при добавлении торрента")
            return@withContext null
        }

        logDebug("Добавление торрента: $magnetLink")
        
        // Проверяем, является ли это хешем или магнет-ссылкой
        val isHash = magnetLink.matches(Regex("^[0-9A-Fa-f]{40}$"))
        val hash = if (isHash) magnetLink.uppercase() else extractHashFromMagnet(magnetLink)
        
        if (hash == null) {
            logDebug("Некорректный формат магнет-ссылки или хеша")
            return@withContext null
        }
        
        try {
            // Проверяем версию API, если еще не определена
            if (detectedApiVersion == ApiVersion.UNKNOWN) {
                detectApiVersion()
            }
            
            // Для POST запросов с телом добавляем данные отдельно
            val postData = mapOf(
                "/torrents" to "{\"link\":\"$magnetLink\",\"title\":\"Added from ReYohoho\"}",
                "/torrent/add" to "{\"link\":\"$magnetLink\"}"
            )
            
            // Список всех попыток
            val attempts = mutableListOf<Pair<String, String>>()
            
            // Добавляем попытки в зависимости от типа API
            if (isMatrixServer) {
                logDebug("Используем Matrix API эндпоинты для добавления торрента")
                // Matrix эндпоинты - порядок важен! Сначала самые надежные методы
                attempts.add(Pair("GET", "/stream?link=${Uri.encode(magnetLink)}&preload=1&play=1"))
                attempts.add(Pair("GET", "/stream?link=$hash&preload=1"))
                attempts.add(Pair("GET", "/stream?link=${Uri.encode(magnetLink)}&preload=1"))
                // Для Matrix.134 самый надежный метод
                attempts.add(Pair("GET", "/stream/play?link=${Uri.encode(magnetLink)}&preload=1"))
                // Остальные альтернативные методы
                attempts.add(Pair("POST", "/torrents"))
                attempts.add(Pair("GET", "/add/${Uri.encode(magnetLink)}"))
                attempts.add(Pair("GET", "/play?link=${Uri.encode(magnetLink)}&save=true"))
            } else {
                // Общие попытки для обычных версий TorrServe
                attempts.add(Pair("GET", "/torrent/add?link=${Uri.encode(magnetLink)}"))
                attempts.add(Pair("POST", "/torrent/add"))
                attempts.add(Pair("GET", "/torrents/add?link=${Uri.encode(magnetLink)}"))
            }
            
            // Пробуем все методы по очереди
            var successfulAttempt = false
            
            for ((method, path) in attempts) {
                try {
                    val url = getApiUrl(path)
                    logDebug("Попытка добавления: $method $url")
                    
                    val body = if (method == "POST") {
                        postData[path]
                    } else {
                        null
                    }
                    
                    val actualPath = path
                    
                    // Для Matrix сервера иногда сразу начинается стриминг вместо JSON ответа
                    // Поэтому используем HEAD-запрос для проверки
                    if (isMatrixServer && path.contains("stream") && path.contains("play")) {
                        try {
                            // Сначала проверяем, что сервер возвращает корректный ответ
                            val connection = URL(getApiUrl(actualPath)).openConnection() as HttpURLConnection
                            connection.requestMethod = "HEAD"
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000
                            
                            val responseCode = connection.responseCode
                            connection.disconnect()
                            
                            if (responseCode == 200) {
                                // Если сервер отвечает 200, считаем торрент добавленным
                                logDebug("Matrix API возвращает 200 на HEAD запрос к $actualPath, считаем торрент добавленным")
                                successfulAttempt = true
                                break
                            }
                        } catch (e: Exception) {
                            logDebug("Ошибка при HEAD запросе к $actualPath", e.toString())
                        }
                    }
                    
                    // Для обычных запросов используем стандартный метод
                    val response = executeRequest(method, getApiUrl(actualPath), body)
                    
                    if (response != null) {
                        logDebug("Успешное добавление через $method $actualPath")
                        successfulAttempt = true
                        break
                    }
                } catch (e: Exception) {
                    logDebug("Ошибка при попытке $method $path", e.toString())
                    // Продолжаем со следующей попыткой
                }
            }
            
            // Запускаем предзагрузку, если торрент успешно добавлен
            if (successfulAttempt) {
                logDebug("Торрент был успешно добавлен, запускаем принудительную предзагрузку для хеша $hash")
                forceTorrentPreload(hash)
            }
            
            // Возвращаем хеш в любом случае для возможности прямого воспроизведения
            logDebug("Возвращаем хеш $hash для воспроизведения")
            return@withContext hash
            
        } catch (e: Exception) {
            logDebug("Общая ошибка при добавлении торрента", "${e.javaClass.simpleName}: ${e.message}")
            return@withContext hash
        }
    }

    /**
     * Принудительно запускает предзагрузку торрента
     */
    private suspend fun forceTorrentPreload(hash: String): Boolean = withContext(Dispatchers.IO) {
        logDebug("Принудительная предзагрузка торрента $hash")
        
        // Список методов для предзагрузки
        val methods = mutableListOf<suspend () -> Boolean>()
        
        // Для Matrix используем специфичные методы
        if (isMatrixServer) {
            // Matrix 134 специфичный метод - через /torrent/play
            methods.add {
                try {
                    logDebug("Запускаем загрузку через /torrent/play (Matrix 134)")
                    val body = "{\"Hash\":\"$hash\",\"Index\":0}"
                    val response = executeRequest(
                        "POST", 
                        getApiUrl("/torrent/play"), 
                        body = body, 
                        timeout = 15000
                    )
                    response != null
                } catch (e: Exception) {
                    logDebug("Ошибка при запуске через /torrent/play", e.toString())
                    false
                }
            }
            
            // Метод через GET запрос с play и preload
            methods.add {
                try {
                    logDebug("Запускаем загрузку через /stream?play&preload=1")
                    val response = executeRequest(
                        "GET", 
                        getApiUrl("/stream?link=$hash&index=0&play&preload=1"), 
                        timeout = 15000
                    )
                    response != null
                } catch (e: Exception) {
                    logDebug("Ошибка при запуске через /stream?play&preload=1", e.toString())
                    false
                }
            }
        }
        
        // Общие методы для всех версий TorrServe
        methods.add {
            try {
                logDebug("Запускаем стандартный стриминг")
                val response = executeRequest(
                    "GET", 
                    getApiUrl("/stream?link=$hash&index=0"), 
                    timeout = 10000
                )
                response != null
            } catch (e: Exception) {
                logDebug("Ошибка при стандартном стриминге", e.toString())
                false
            }
        }
        
        // Запускаем методы по очереди, пока один не сработает
        var succeeded = false
        for (method in methods) {
            try {
                val result = method.invoke()
                if (result) {
                    succeeded = true
                    logDebug("Успешно запустили предзагрузку")
                    break
                }
            } catch (e: Exception) {
                logDebug("Ошибка при попытке предзагрузки", e.toString())
            }
        }
        
        return@withContext succeeded
    }

    /**
     * Проверяет статус загрузки торрента
     */
    suspend fun checkTorrentStatus(hash: String): Triple<Boolean, Float, Long?> = withContext(Dispatchers.IO) {
        logDebug("Проверка статуса торрента: $hash")
        
        var ready = false
        var progress = 0f
        var preloadedBytes: Long? = null
        
        // Для Matrix API - специальные методы
        if (isMatrixServer) {
            try {
                // Метод 1: Проверка для Matrix.134 через просто HEAD запрос к /stream
                try {
                    val url = getApiUrl("/stream?link=$hash&index=0")
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    
                    val responseCode = connection.responseCode
                    if (responseCode < 400) {
                        logDebug("Стрим доступен, торрент готов")
                        ready = true
                        progress = 0.5f
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    logDebug("Ошибка при прямой проверке стрима", e.toString())
                }
                
                // Метод 2: Проверка через /stream с параметром stat для более новых версий Matrix
                if (!ready) {
                    try {
                        val response = executeRequest(
                            "GET", 
                            getApiUrl("/stream?link=$hash&index=0&stat=true"), 
                            timeout = 5000
                        )
                        
                        if (response != null) {
                            try {
                                val data = JSONObject(response)
                                val jsonProgress = data.optDouble("progress", 0.0)
                                val jsonPreloadedBytes = data.optLong("preloadedBytes", 0)
                                
                                progress = jsonProgress.toFloat()
                                preloadedBytes = jsonPreloadedBytes
                                
                                // Если идет загрузка или есть прогресс, считаем готовым
                                if (progress > 0.01f || preloadedBytes!! > 1024 * 1024) {
                                    logDebug("Matrix API: торрент имеет прогресс $progress, считаем готовым")
                                    ready = true
                                }
                            } catch (e: JSONException) {
                                // Если ответ не в формате JSON, но что-то вернулось, считаем что торрент готов
                                if (response.isNotEmpty()) {
                                    logDebug("Получен не-JSON ответ от /stream?stat, но торрент, вероятно, готов")
                                    ready = true
                                    progress = 0.5f
                                } else {
                                    logDebug("Ошибка при парсинге JSON ответа от /stream?stat", e.toString())
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logDebug("Ошибка при проверке через /stream?stat", e.toString())
                    }
                }
                
                // Метод 3: Проверка специфичная для Matrix.134
                if (!ready) {
                    try {
                        val response = executeRequest(
                            "GET", 
                            getApiUrl("/stream/status?link=$hash&index=0"), 
                            timeout = 3000
                        )
                        
                        if (response != null) {
                            logDebug("Торрент найден в Matrix.134 через /stream/status")
                            ready = true
                            progress = 0.4f
                        }
                    } catch (e: Exception) {
                        logDebug("Ошибка при проверке через /stream/status", e.toString())
                    }
                }
                
                // Метод 4: Проверка через веб-интерфейс Matrix
                if (!ready) {
                    try {
                        val response = executeRequest(
                            "GET", 
                            getApiUrl("/viewed?hash=$hash"), 
                            timeout = 3000
                        )
                        
                        if (response != null) {
                            logDebug("Торрент найден в Matrix через /viewed")
                            ready = true
                            progress = 0.3f
                        }
                    } catch (e: Exception) {
                        logDebug("Ошибка при проверке через /viewed", e.toString())
                    }
                }
            } catch (e: Exception) {
                logDebug("Общая ошибка при проверке Matrix API", e.toString())
            }
            
            // Для Matrix в большинстве случаев мы можем считать торрент готовым к воспроизведению
            // Даже если ни один из методов не сработал
            if (!ready) {
                logDebug("Для Matrix считаем торрент готовым по умолчанию")
                ready = true
                progress = 0.1f
            }
        } else {
            // Для стандартного API
            try {
                val response = executeRequest(
                    "GET", 
                    getApiUrl("/torrent/status?hash=$hash"), 
                    timeout = 3000
                )
                
                if (response != null) {
                    try {
                        val data = JSONObject(response)
                        val jsonProgress = data.optDouble("progress", 0.0)
                        val jsonPreloadedBytes = data.optLong("preloaded_bytes", 
                            data.optLong("preloadedBytes", 0))
                        val size = data.optLong("torrent_size", 
                            data.optLong("torrentSize", 
                                data.optLong("size", 0)))
                        
                        progress = jsonProgress.toFloat()
                        preloadedBytes = jsonPreloadedBytes
                        
                        // Определяем готовность
                        ready = data.optBoolean("ready", data.optBoolean("isReady", false))
                        
                        // Если указано, что торрент не готов, но загружено достаточно, считаем готовым
                        if (!ready && (progress > 0.05f || (size > 0 && preloadedBytes!! > 1024 * 1024))) {
                            ready = true
                        }
                        
                        logDebug("Статус из стандартного API: ready=$ready, progress=$progress, preloadedBytes=$preloadedBytes")
                    } catch (e: JSONException) {
                        logDebug("Ошибка при парсинге JSON ответа", e.toString())
                    }
                }
            } catch (e: Exception) {
                logDebug("Ошибка при проверке через стандартный API", e.toString())
            }
        }
        
        // Финальная проверка - проверяем доступность стрима через HEAD запрос
        if (!ready) {
            try {
                val url = getPlaybackUrl(hash, 0)
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                
                val responseCode = connection.responseCode
                if (responseCode < 400) {
                    logDebug("Стрим доступен, торрент готов")
                    ready = true
                    progress = 0.5f
                }
                connection.disconnect()
            } catch (e: Exception) {
                logDebug("Ошибка при проверке доступности стрима", e.toString())
            }
        }
        
        return@withContext Triple(ready, progress, preloadedBytes)
    }

    /**
     * Ожидает готовности торрента с таймаутом
     */
    suspend fun waitForTorrentReady(hash: String, maxWaitTimeMs: Long = 60000, checkIntervalMs: Long = 2000): Boolean = withContext(Dispatchers.IO) {
        logDebug("Ожидание готовности торрента $hash, максимальное время: ${maxWaitTimeMs}ms")
        
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < maxWaitTimeMs) {
            val (ready, progress, preloadedBytes) = checkTorrentStatus(hash)
            logDebug("Статус торрента: ready=$ready, progress=$progress, preloadedBytes=$preloadedBytes")
            
            if (ready) {
                logDebug("Торрент готов к воспроизведению!")
                return@withContext true
            }
            
            logDebug("Торрент еще не готов, прогресс: $progress, ожидаем ${checkIntervalMs}ms")
            delay(checkIntervalMs)
        }
        
        logDebug("Время ожидания готовности торрента истекло")
        return@withContext false
    }

    /**
     * Получает URL для воспроизведения с учетом особенностей разных версий TorrServe
     */
    fun getPlaybackUrl(hash: String, fileIndex: Int): String {
        logDebug("Формирование URL для воспроизведения, hash: $hash, fileIndex: $fileIndex, isMatrix: $isMatrixServer")
        
        if (isMatrixServer) {
            // Для Matrix.134 нужно использовать формат с 'play' в конце
            return getApiUrl("/stream?link=$hash&index=$fileIndex&play")
        } else {
            // Для стандартного TorrServe
            return getApiUrl("/stream?link=$hash&index=$fileIndex")
        }
    }

    /**
     * Генерирует URL для воспроизведения с транскодированием
     */
    fun getTranscodedPlaybackUrl(hash: String, fileIndex: Int, filePath: String = ""): Pair<String, List<String>> {
        if (hash.isEmpty()) return Pair("", emptyList())

        // Определяем формат файла
        val fileExt = filePath.substringAfterLast('.', "").lowercase()
        
        // Список форматов, которые обычно нужно транскодировать
        val needsTranscoding = listOf("avi", "mpg", "mpeg", "wmv", "flv", "mov", "divx", "xvid", "rm", "rmvb", "vob")
        
        // Если формат не нуждается в транскодировании, возвращаем обычный URL
        if (fileExt.isEmpty() || !needsTranscoding.contains(fileExt)) {
            return Pair(getPlaybackUrl(hash, fileIndex), emptyList())
        }
        
        logDebug("Применяем транскодирование для формата $fileExt")
        
        // Для Matrix используем специфичные параметры транскодирования
        if (isMatrixServer) {
            // Matrix обычно поддерживает параметры transcode, tr или конвертацию в HLS
            val matrixOptions = listOf(
                // Вариант 1: tr=true
                getApiUrl("/stream?link=$hash&index=$fileIndex&tr=true&play"),
                
                // Вариант 2: transcode=true
                getApiUrl("/stream?link=$hash&index=$fileIndex&transcode=true&play"),
                
                // Вариант 3: для некоторых версий - hls=true
                getApiUrl("/stream?link=$hash&index=$fileIndex&hls=true&play"),
                
                // Вариант 4: для старых версий - форсируем mp4
                getApiUrl("/stream?link=$hash&index=$fileIndex&fmt=mp4&play")
            )
            
            // Возвращаем первый вариант и список всех опций
            return Pair(matrixOptions.first(), matrixOptions)
        }
        
        // Для обычного TorrServe, который может поддерживать транскодирование
        return Pair(
            getApiUrl("/stream?link=$hash&index=$fileIndex&tr=true"),
            emptyList()
        )
    }

    /**
     * Извлекает хеш из магнет-ссылки
     */
    private fun extractHashFromMagnet(magnetLink: String): String? {
        if (!magnetLink.startsWith("magnet:")) {
            return null
        }
        
        val regex = "xt=urn:btih:([a-zA-Z0-9]+)".toRegex(RegexOption.IGNORE_CASE)
        val matchResult = regex.find(magnetLink)
        return matchResult?.groupValues?.getOrNull(1)?.uppercase()
    }

    /**
     * Включает режим отладки
     */
    fun enableDebug() {
        isDebugEnabled = true
        logDebug("Режим отладки включен")
    }

    /**
     * Отключает режим отладки
     */
    fun disableDebug() {
        logDebug("Режим отладки отключен")
        isDebugEnabled = false
    }

    /**
     * Добавляет магнет-ссылку в TorrServe и запускает воспроизведение
     */
    suspend fun addAndPlay(magnetUrl: String): Boolean = withContext(Dispatchers.Main) {
        try {
            if (!isNetworkAvailable()) {
                Toast.makeText(context, "Нет подключения к интернету", Toast.LENGTH_SHORT).show()
                return@withContext false
            }
            // Показываем сообщение о начале процесса
            Toast.makeText(context, "Добавление торрента...", Toast.LENGTH_SHORT).show()
            // Проверяем доступность TorrServe
            val available = withContext(Dispatchers.IO) { checkTorrServeAvailable() }
            if (!available) {
                val message = "TorrServe недоступен по адресу: ${settingsManager.getExternalTorrServeUrl()}"
                logDebug(message)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                return@withContext false
            }
            val hash = withContext(Dispatchers.IO) { addTorrent(magnetUrl) }
            if (hash != null) {
                // Ждем готовности торрента
                val ready = withContext(Dispatchers.IO) { 
                    waitForTorrentReady(hash, 30000, 2000) 
                }
                logDebug("Торрент ${if (ready) "готов" else "не готов"} к воспроизведению, запускаем плеер")
                playTorrent(hash)
                return@withContext true
            } else {
                val message = "Не удалось добавить торрент. Проверьте настройки TorrServe."
                logDebug(message)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                return@withContext false
            }
        } catch (e: Exception) {
            logDebug("Ошибка при добавлении и воспроизведении", "${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            return@withContext false
        }
    }

    /**
     * Запускает воспроизведение по hash торрента
     */
    suspend fun playTorrent(hash: String, fileIndex: Int = 0) {
        try {
            // Проверяем хеш на валидность
            if (hash.isEmpty() || hash.length < 20) {
                logDebug("Неверный хеш для воспроизведения: $hash")
                Toast.makeText(context, "Неверный хеш торрента", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Получаем URL для воспроизведения
            val playUrl = getPlaybackUrl(hash, fileIndex)
            
            logDebug("Запуск воспроизведения: $playUrl")
            
            // Проверка доступности URL перед запуском плеера
            if (isMatrixServer) {
                try {
                    // Для Matrix выполняем HEAD-запрос чтобы убедиться, что URL работает
                    withContext(Dispatchers.IO) {
                        val connection = URL(playUrl).openConnection() as HttpURLConnection
                        connection.requestMethod = "HEAD"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        val responseCode = connection.responseCode
                        connection.disconnect()
                        
                        if (responseCode >= 400) {
                            logDebug("URL для воспроизведения недоступен, код: $responseCode")
                            throw Exception("Сервер вернул ошибку: $responseCode")
                        }
                    }
                } catch (e: Exception) {
                    logDebug("Не удалось проверить URL для воспроизведения", e.toString())
                    // Продолжаем, так как не все серверы поддерживают HEAD-запросы
                }
            }
            
            // Безопасно запускаем плеер на главном потоке
            withContext(Dispatchers.Main) {
                // Создаем Intent для открытия внешнего плеера
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(playUrl), "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                try {
                    // Запускаем плеер
                    context.startActivity(intent)
                    logDebug("Запущен плеер для hash: $hash, fileIndex: $fileIndex")
                } catch (e: Exception) {
                    logDebug("Ошибка при запуске плеера через Intent", e.toString())
                    Toast.makeText(context, "Не найдено приложение для воспроизведения видео", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            logDebug("Ошибка при запуске плеера", "${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            Toast.makeText(context, "Ошибка при запуске плеера: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
} 