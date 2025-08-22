package com.example.reyohoho.ui

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Менеджер для работы с системой автоматического переключения торрент-серверов FreeTorr
 */
class FreeTorrManager(private val context: Context) {

    companion object {
        private const val TAG = "FreeTorrManager"
        private const val FREE_TORR_SERVER_URL = "http://185.87.48.42:8090"
        private const val RANDOM_TORR_ENDPOINT = "/random_torr"
        private const val CONNECTION_TIMEOUT = 5000
        private const val READ_TIMEOUT = 10000
        
        @Volatile
        private var INSTANCE: FreeTorrManager? = null

        fun getInstance(context: Context): FreeTorrManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FreeTorrManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val settingsManager = SettingsManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRequestInProgress = AtomicBoolean(false)
    private var currentServerUrl: String? = null

    /**
     * Получает случайный торрент-сервер от основного сервера
     */
    suspend fun getRandomTorrServer(): String? = withContext(Dispatchers.IO) {
        if (isRequestInProgress.get()) {
            Log.d(TAG, "Запрос уже выполняется, возвращаем текущий сервер")
            return@withContext currentServerUrl
        }

        isRequestInProgress.set(true)
        
        try {
            val url = URL("$FREE_TORR_SERVER_URL$RANDOM_TORR_ENDPOINT")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "ReYohoho/1.0")
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Ответ от сервера: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText().trim()
                reader.close()

                Log.d(TAG, "Получен IP-адрес: $response")
                
                // Проверяем, что получен валидный IP-адрес
                if (response.matches(Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"))) {
                    // Формируем полный URL с портом 8090
                    val serverUrl = "http://$response:8090"
                    currentServerUrl = serverUrl
                    
                    // Сохраняем полученный сервер в настройки FreeTorr
                    settingsManager.setFreeTorrServerUrl(serverUrl)
                    
                    Log.d(TAG, "Установлен новый торрент-сервер: $serverUrl")
                    return@withContext serverUrl
                } else {
                    Log.e(TAG, "Получен некорректный IP-адрес: $response")
                    return@withContext null
                }
            } else {
                Log.e(TAG, "Ошибка получения сервера: HTTP $responseCode")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при получении случайного сервера", e)
            return@withContext null
        } finally {
            isRequestInProgress.set(false)
        }
    }

    /**
     * Автоматически переключает на новый сервер при ошибке
     */
    suspend fun switchToNewServer(): String? {
        Log.d(TAG, "Переключаемся на новый сервер...")
        return getRandomTorrServer()
    }

    /**
     * Проверяет доступность текущего сервера с тщательным тестированием
     */
    suspend fun checkServerAvailability(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Тщательная проверка сервера: $serverUrl")
            
            // Проверяем несколько эндпоинтов для надежности
            val endpoints = listOf("/torrents", "/", "/echo")
            var successfulChecks = 0
            
            for (endpoint in endpoints) {
                try {
                    val url = URL("$serverUrl$endpoint")
                    val connection = url.openConnection() as HttpURLConnection
                    
                    connection.apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 8000
                        setRequestProperty("User-Agent", "ReYohoho/1.0")
                    }

                    val responseCode = connection.responseCode
                    Log.d(TAG, "Проверка $serverUrl$endpoint: HTTP $responseCode")
                    
                    // Считаем успешными коды 200, 404 (сервер работает, но эндпоинт не найден)
                    if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        successfulChecks++
                    }
                    
                    connection.disconnect()
                    
                } catch (e: Exception) {
                    Log.d(TAG, "Ошибка проверки $serverUrl$endpoint: ${e.message}")
                }
            }
            
            // Сервер считается доступным, если хотя бы 2 из 3 проверок прошли успешно
            val isAvailable = successfulChecks >= 2
            Log.d(TAG, "Результат проверки $serverUrl: $successfulChecks/3 успешных проверок, доступен: $isAvailable")
            
            return@withContext isAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Общая ошибка проверки сервера $serverUrl", e)
            return@withContext false
        }
    }

    /**
     * Получает JavaScript код для интеграции с FreeTorr
     */
    fun getFreeTorrJavaScript(): String {
        return """
            // FreeTorr Integration Script
            (function() {
                'use strict';
                
                // Основной сервер FreeTorr
                const FREE_TORR_SERVER = 'http://185.87.48.42:8090';
                const RANDOM_TORR_ENDPOINT = '/random_torr';
                
                // Функция для получения случайного сервера
                async function getRandomTorrServer() {
                    try {
                        const response = await fetch(FREE_TORR_SERVER + RANDOM_TORR_ENDPOINT, {
                            method: 'GET',
                            headers: {
                                'User-Agent': 'ReYohoho/1.0'
                            }
                        });
                        
                        if (response.ok) {
                            const serverIp = await response.text();
                            const serverUrl = 'http://' + serverIp.trim() + ':8090';
                            console.log('FreeTorr: Получен новый сервер:', serverUrl);
                            return serverUrl;
                        } else {
                            console.error('FreeTorr: Ошибка получения сервера:', response.status);
                            return null;
                        }
                    } catch (error) {
                        console.error('FreeTorr: Ошибка запроса:', error);
                        return null;
                    }
                }
                
                // Функция для проверки доступности сервера
                async function checkServerAvailability(serverUrl) {
                    try {
                        const response = await fetch(serverUrl + '/torrents', {
                            method: 'GET',
                            headers: {
                                'User-Agent': 'ReYohoho/1.0'
                            }
                        });
                        return response.ok;
                    } catch (error) {
                        console.error('FreeTorr: Ошибка проверки сервера:', error);
                        return false;
                    }
                }
                
                // Функция для автоматического переключения сервера
                async function switchToNewServer() {
                    console.log('FreeTorr: Переключаемся на новый сервер...');
                    const newServer = await getRandomTorrServer();
                    if (newServer) {
                        // Уведомляем Android приложение о новом сервере
                        if (window.Android && window.Android.onTorrServerChanged) {
                            window.Android.onTorrServerChanged(newServer);
                        }
                        return newServer;
                    }
                    return null;
                }
                
                // Экспортируем функции в глобальную область
                window.FreeTorr = {
                    getRandomTorrServer,
                    checkServerAvailability,
                    switchToNewServer
                };
                
                console.log('FreeTorr: Скрипт загружен');
            })();
        """.trimIndent()
    }

    /**
     * Очищает ресурсы
     */
    fun cleanup() {
        scope.cancel()
    }
}

