package com.example.reyohoho

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.app.PictureInPictureParams
import android.util.Rational
import android.content.res.Configuration
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.platform.LocalContext
import com.example.reyohoho.ui.SettingsManager
import com.example.reyohoho.ui.SettingsScreen
import com.example.reyohoho.ui.theme.AppBackgroundColor
import com.example.reyohoho.ui.theme.ReYohohoTheme
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextAlign
import android.webkit.WebView
import android.content.pm.PackageManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import android.Manifest
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import android.content.Context
import android.app.PendingIntent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.webkit.JavascriptInterface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Dispatchers
import com.example.reyohoho.ui.TorrServeManager

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        
        // Массив доступных доменов (обновлен с новыми зеркалами)
        private val AVAILABLE_DOMAINS = arrayOf(
            "https://reyohoho.github.io/reyohoho",
            "https://reyohoho-gitlab.vercel.app/",
            "https://reyohoho.gitlab.io/reyohoho/",
            "https://reyohoho.serv00.net/",
            "https://reyohoho.onrender.com/",
            "https://reyohoho.vercel.app/",
            "https://reyohoho.surge.sh/"
        )
        
        // URL для приложений-курсоров для Android TV
        val TV_CURSOR_APP_URLS = arrayOf(
            "https://github.com/virresh/matvt/releases/tag/v1.0.6"
        )
    }
    
    // Менеджер настроек приложения
    private lateinit var settingsManager: SettingsManager
    
    // Для обработки двойного нажатия кнопки "Назад"
    private var doubleBackToExitPressedOnce = false
    private var currentWebView: WebView? = null
    
    private val STORAGE_PERMISSION_CODE = 1001
    
    // Переменная для хранения состояния PiP
    private var isInPipMode = false
    
    // Медиа-сессия для улучшения PiP режима
    private var mediaSession: MediaSession? = null
    
    var fullScreenWebChromeClient: FullScreenWebChromeClient? = null
    
    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Запрос разрешения на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 2001)
            }
        }
        // Запуск фоновой проверки обновлений
        com.example.reyohoho.ui.scheduleUpdateCheckWorker(this)
        
        // Проверяем и запрашиваем разрешения
        checkAndRequestPermissions()
        
        val openAbout = intent.getBooleanExtra("open_about", false)
        if (openAbout) {
            startUpdateDownload(this)
        }
        
        try {
            // Инициализация менеджера настроек
            settingsManager = SettingsManager.getInstance(this)
            
            // Настройка правильного отображения системных кнопок
            setupImmersiveMode()
            
            // Инициализация MediaSession для PiP режима
            setupMediaSession()
            
            // По умолчанию включаем настройку "убрать отступ сверху" при первом запуске
            if (!settingsManager.isDeviceTypeSet()) {
                settingsManager.setRemoveTopSpacing(true)
            }
            
            // Следим за изменениями настройки полноэкранного режима в отдельном scope
            lifecycleScope.launch {
                try {
                    settingsManager.fullscreenModeFlow.collect { fullscreenEnabled ->
                        try {
                            setupImmersiveMode()
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка при обновлении иммерсивного режима: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при сборе fullscreenModeFlow: ${e.message}")
                }
            }
            
            // Инициализация блокировщика рекламы
            initializeAdBlocker()
            
            // Проверяем, есть ли входящий URL-интент
            val urlToLoad = getIncomingUrl()
            
            // Если нет входящего URL, используем выбранное зеркало из настроек
            val finalUrl = urlToLoad ?: settingsManager.getSiteMirror()
            Log.d(TAG, "Загружаем URL: $finalUrl")
            
            val appVersion = packageManager.getPackageInfo(packageName, 0).versionName!!
            
            setContent {
                // Всегда используем темную тему независимо от настроек системы
                ReYohohoTheme(darkTheme = true) {
                    var showJacredDialog by remember { mutableStateOf(false) }
                    var jacredKpId by remember { mutableStateOf("") }
                    
                    // Состояние для отображения настроек
                    var showSettings by remember { mutableStateOf(false) }
                    
                    // Состояние для отображения первоначального выбора платформы
                    var showDeviceTypeSelection by remember { mutableStateOf(!settingsManager.isDeviceTypeSet()) }
                    
                    // Состояние для отображения диалога установки курсора на TV
                    var showTVCursorPrompt by remember { mutableStateOf(false) }
                    
                    // Используем простой подход - храним WebView в состоянии
                    var webView by remember { mutableStateOf<WebView?>(null) }
                    var currentWebViewUrl by remember { mutableStateOf("") }
                    
                    // Функция для обновления страницы
                    val refreshPage: () -> Unit = {
                        webView?.reload()
                        Log.d(TAG, "Страница обновлена")
                    }
                    
                    // Следим за изменениями зеркала сайта
                    val selectedMirror by settingsManager.siteMirrorFlow.collectAsState()
                    val showSettingsButtonOnlyOnSettingsPage by settingsManager.showSettingsButtonOnlyOnSettingsPageFlow.collectAsState()
                    var currentUrl by remember { mutableStateOf(finalUrl) }
                    
                    // Обновляем URL при изменении зеркала
                    LaunchedEffect(selectedMirror) {
                        if (urlToLoad == null) { // Только если нет входящего URL
                            currentUrl = selectedMirror
                            webView?.let { webView ->
                                Log.d(TAG, "Загружаем новое зеркало: $selectedMirror")
                                webView.loadUrl(selectedMirror)
                                
                                // Принудительно обновляем через небольшую задержку
                                kotlinx.coroutines.delay(100)
                                if (webView.url != selectedMirror) {
                                    Log.d(TAG, "Повторная загрузка зеркала: $selectedMirror")
                                    webView.loadUrl(selectedMirror)
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Если необходимо выбрать тип устройства при первом запуске
                        if (showDeviceTypeSelection) {
                            DeviceTypeSelectionScreen(
                                onSelectDeviceType = { deviceType ->
                                    settingsManager.setDeviceType(deviceType)
                                    showDeviceTypeSelection = false
                                    
                                    // Если выбран Android TV, показываем предложение установить курсор
                                    if (deviceType == SettingsManager.DEVICE_TYPE_ANDROID_TV) {
                                        showTVCursorPrompt = true
                                    }
                                }
                            )
                        } else if (showTVCursorPrompt) {
                            TVCursorPromptScreen(
                                onClose = { showTVCursorPrompt = false }
                            )
                        } else {
                            // Основной контент с WebView
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                // WebView всегда отображается под экраном настроек
                                                                    AdBlockWebView(
                                        url = currentUrl,
                                        settingsManager = settingsManager,
                                        onWebViewCreated = { 
                                            webView = it
                                            currentWebView = it
                                        },
                                        onUrlChanged = { url ->
                                            currentWebViewUrl = url
                                        }
                                    )
                            }
                            
                            // Если показываем настройки, отображаем их поверх WebView
                            if (showSettings) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                ) {
                                    SettingsScreen(
                                        settingsManager = settingsManager,
                                        onClose = { showSettings = false },
                                        appVersion = appVersion
                                    )
                                }
                            }
                            }
                            
                        // Кнопка настроек в правом нижнем углу
                        val showSettingsButton = if (settingsManager.isShowSettingsButtonOnlyOnSettingsPageEnabled()) {
                            // Если включена настройка "только на /settings", проверяем текущий URL
                            currentWebViewUrl.contains("/settings")
                        } else {
                            // Если настройка выключена, показываем всегда
                            true
                        }
                        // --- ДОБАВЛЕНО: Кнопка торрента ---
                        // Проверяем, что это страница фильма: /movie/ и цифры
                        val movieIdRegex = Regex("/movie/(\\d+)")
                        val match = movieIdRegex.find(currentWebViewUrl)
                        val showTorrentButton = match != null
                        val kpId = match?.groupValues?.getOrNull(1)
                        
                        val settingsButtonPadding = settingsManager.settingsButtonPaddingFlow.collectAsState().value
                        val torrentButtonPadding = settingsManager.torrentButtonPaddingFlow.collectAsState().value
                        val torrentsEnabled = settingsManager.torrentsEnabledFlow.collectAsState().value
                        val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels
                        val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels
                        // Кнопка торрентов
                        if (torrentsEnabled && !showSettings && !showDeviceTypeSelection && !showTVCursorPrompt && showTorrentButton && kpId != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = torrentButtonPadding.first.dp, bottom = torrentButtonPadding.second.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Button(
                                    onClick = {
                                        showJacredDialog = true
                                        jacredKpId = kpId
                                    },
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Black,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Торрент",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        // Кнопка настроек всегда в одном месте, кроме экрана настроек
                        if (!showSettings && !showDeviceTypeSelection && !showTVCursorPrompt && showSettingsButton) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = settingsButtonPadding.first.dp, bottom = settingsButtonPadding.second.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Button(
                                    onClick = { showSettings = true },
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Black,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Настройки",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        // JacredScreen поверх всего
                        if (torrentsEnabled && showJacredDialog && jacredKpId.isNotEmpty()) {
                            JacredScreen(kpId = jacredKpId, onClose = { showJacredDialog = false }, settingsManager = settingsManager)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при инициализации приложения: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Ошибка при запросе разрешения на доступ к файлам: ${e.message}")
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_CODE
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Разрешение на доступ к хранилищу получено")
            } else {
                Log.e("MainActivity", "Разрешение на доступ к хранилищу отклонено")
            }
        }
    }
    
    /**
     * Получение URL из входящего интента
     */
    private fun getIncomingUrl(): String? {
        // Получаем данные из интента
        val action = intent.action
        val data: Uri? = intent.data
        
        // Проверяем, что интент - для просмотра URL и содержит данные
        if (Intent.ACTION_VIEW == action && data != null) {
            val url = data.toString()
            Log.d(TAG, "Получен входящий URL: $url")
            return url
        }
        
        return null
    }
    
    /**
     * Инициализация блокировщика рекламы
     */
    private fun initializeAdBlocker() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Начинаем инициализацию блокировщика рекламы...")
                AdBlocker.initializeWithSettings(this@MainActivity)
                Log.d(TAG, "Блокировщик рекламы успешно инициализирован")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при инициализации блокировщика рекламы: ${e.message}")
            }
        }
    }
    
    /**
     * Проверка, запущено ли приложение на Android TV
     */
    private fun isRunningOnAndroidTV(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    
    /**
     * Настройка иммерсивного режима для лучшего отображения системных кнопок
     * Публичный метод для возможности вызова из других мест
     */
    @Suppress("DEPRECATION")
    fun setupImmersiveMode() {
        // Защита от исключений
        try {
            val fullscreenEnabled = if (this::settingsManager.isInitialized) {
                settingsManager.isFullscreenModeEnabled()
            } else {
                false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11 (API 30) и выше
                window.setDecorFitsSystemWindows(false)
                
                if (fullscreenEnabled) {
                    // Полностью скрываем все системные панели
                    window.insetsController?.let { controller ->
                        controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        controller.systemBarsBehavior = 
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    // Показываем системные панели
                    window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
            } else {
                // Для более старых версий Android
                if (fullscreenEnabled) {
                    window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
                } else {
                    window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
                }
            }
            
            // Делаем строку состояния полностью прозрачной
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            
            // Всегда используем черную навигационную панель независимо от темы системы
            window.navigationBarColor = android.graphics.Color.BLACK
            
            // Устанавливаем иконки в системных панелях для темной темы
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Убираем флаг светлых иконок на навигационной панели
                    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and 
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    
                    // Убираем флаг светлых иконок на строке состояния
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при настройке системных иконок: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при настройке иммерсивного режима: ${e.message}")
        }
    }
    
    /**
     * Вызывается при возвращении из полноэкранного режима
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (fullScreenWebChromeClient?.isInFullScreen() == true) {
                // Не трогаем системные панели, плеер сам их скроет
                return
            }
            setupImmersiveMode()
        }
    }
    
    /**
     * Вызывается при уничтожении активности
     */
    override fun onDestroy() {
        super.onDestroy()
        
        // TorrServeManager при закрытии приложения не требует специальной очистки
        // Т.к. используется только внешний TorrServe
        
        // Закрываем MediaSession
        try {
            mediaSession?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при закрытии MediaSession: ${e.message}")
        }
        
        // Сохраняем cookie перед закрытием приложения
        try {
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении cookie: ${e.message}")
        }
    }

    private fun startUpdateDownload(context: Context) {
        lifecycleScope.launch {
            try {
                val result = com.example.reyohoho.ui.UpdateChecker.checkForUpdate(context)
                if (result.isUpdateAvailable) {
                    com.example.reyohoho.ui.UpdateChecker.downloadAndInstallApk(context, result.downloadUrl, result.latestVersion, autoInstall = true)
                }
            } catch (e: Exception) {
                // No logging needed here
            }
        }
    }

    // Метод для перехода в режим PiP для API 26+
    @Suppress("DEPRECATION")
    fun enterPictureInPictureMode(videoWidth: Int = 16, videoHeight: Int = 9) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Log.d(TAG, "Попытка входа в режим PiP с пропорциями $videoWidth:$videoHeight")
                
                // Находим текущее видео и подготавливаем его к PiP
                currentWebView?.evaluateJavascript("""
                    (function() {
                        // Функция для поиска всех возможных видео элементов
                        function findAllVideoElements() {
                            // Прямой поиск видео на странице
                            let videos = Array.from(document.querySelectorAll('video'));
                            
                            // Поиск видео в Lumex плеере
                            const lumexVideo = document.querySelector('#vjs_video_3_html5_api');
                            if (lumexVideo && !videos.includes(lumexVideo)) {
                                videos.push(lumexVideo);
                            }
                            
                            // Поиск видео во всех iframe
                            document.querySelectorAll('iframe').forEach(function(iframe) {
                                try {
                                    const iframeDocument = iframe.contentDocument || iframe.contentWindow && iframe.contentWindow.document;
                                    if (iframeDocument) {
                                        iframeDocument.querySelectorAll('video').forEach(function(video) {
                                            videos.push(video);
                                        });
                                    }
                                } catch (e) {
                                    console.log('Не удалось получить доступ к видео в iframe: ' + e);
                                }
                            });
                            
                            return videos;
                        }
                        
                        // Находим все видео на странице
                        const videos = findAllVideoElements();
                        console.log('Найдено видео элементов: ' + videos.length);
                        
                        // Находим воспроизводящееся видео или первое видео
                        const activeVideo = videos.find(v => !v.paused && v.currentTime > 0) || videos[0];
                        
                        // Если нашли видео, делаем его на весь экран и скрываем элементы управления
                        if (activeVideo) {
                            console.log('Подготовка видео к режиму PiP');
                            
                            // Сохраняем текущее состояние видео
                            window._pipVideoState = {
                                wasPlaying: !activeVideo.paused,
                                currentTime: activeVideo.currentTime
                            };
                            
                            // Запускаем воспроизведение, если видео было на паузе
                            if (activeVideo.paused) {
                                activeVideo.play().catch(e => console.log('Не удалось запустить воспроизведение: ' + e));
                            }
                            
                            // Увеличиваем громкость, если она была приглушена
                            if (activeVideo.volume < 0.1) {
                                activeVideo.volume = 0.5;
                            }
                            
                            // Убираем mute, если звук был выключен
                            if (activeVideo.muted) {
                                activeVideo.muted = false;
                            }
                            
                            // Попробуем эмулировать нажатие на элементы управления
                            if (window.ReYoHoHoPipHelper && typeof window.ReYoHoHoPipHelper.activatePipMode === 'function') {
                                window.ReYoHoHoPipHelper.activatePipMode(activeVideo.videoWidth, activeVideo.videoHeight);
                            }
                        }
                        
                        // Скрываем элементы управления UI перед переходом в PiP
                        document.querySelectorAll('.pip-hide, [data-pip-hide="true"], .control-bar, .player-controls, .vjs-control-bar')
                            .forEach(function(el) {
                                el.style.visibility = 'hidden';
                            });
                            
                        // Для Lumex плеера
                        const lumexPlayer = document.querySelector('#vjs_video_3');
                        if (lumexPlayer) {
                            // Удаляем класс, который может блокировать PiP
                            lumexPlayer.classList.remove('vjs-hidden');
                            
                            // Проверяем, есть ли нативная кнопка PiP в плеере
                            const pipButton = lumexPlayer.querySelector('.vjs-picture-in-picture-control') || 
                                              lumexPlayer.querySelector('[aria-label="Картинка в картинке"]');
                            
                            if (pipButton && !pipButton.disabled) {
                                console.log('Нажимаем на кнопку PiP в Lumex плеере');
                                pipButton.click();
                            }
                        }
                    })();
                """.trimIndent(), null)

                // Короткая задержка, чтобы JS успел выполниться
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Ошибка при задержке: ${e.message}")
                }
                
                // Создаем параметры для PiP
                val builder = PictureInPictureParams.Builder()
                
                // Устанавливаем соотношение сторон (по умолчанию 16:9, но может быть изменено)
                val rational = Rational(videoWidth, videoHeight)
                builder.setAspectRatio(rational)
                
                // Добавляем информацию о прямоугольнике источника (для плавной анимации)
                currentWebView?.let { webView ->
                    val rect = Rect()
                    webView.getGlobalVisibleRect(rect)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setSourceRectHint(rect)
                    }
                    
                    // Устанавливаем автоматический вход в режим PiP при совместимых действиях
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setAutoEnterEnabled(true)
                    }
                }
                
                // Переходим в режим PiP
                enterPictureInPictureMode(builder.build())
                
                Log.d(TAG, "Успешный переход в режим PiP")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при переходе в режим PiP: ${e.message}")
                e.printStackTrace()
                
                // Показываем расширенную информацию об ошибке
                val errorMessage = "Ошибка PiP: ${e.message}\nВам может потребоваться включить режим «Картинка в картинке» для приложения в настройках Android."
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                
                // Пробуем открыть настройки приложения
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", packageName, null)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } catch (ex: Exception) {
                    Log.e(TAG, "Не удалось открыть настройки приложения: ${ex.message}")
                }
            }
        } else {
            // Для старых версий Android показываем уведомление
            Toast.makeText(
                this,
                "Режим «Картинка в картинке» требует Android 8.0 (Oreo) или выше. У вас Android ${Build.VERSION.RELEASE}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Инициализация и настройка MediaSession для улучшения работы PiP режима
     */
    @SuppressLint("InlinedApi")
    private fun setupMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                // Создаем медиа-сессию
                mediaSession = MediaSession(this, "ReYohohoMediaSession")
                
                // Настраиваем колбэки для медиа-сессии
                mediaSession?.setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        super.onPlay()
                        Log.d(TAG, "MediaSession: onPlay вызван")
                        sendJavaScriptToWebView("if(document.querySelector('video')){document.querySelector('video').play();}")
                    }
                    
                    override fun onPause() {
                        super.onPause()
                        Log.d(TAG, "MediaSession: onPause вызван")
                        sendJavaScriptToWebView("if(document.querySelector('video')){document.querySelector('video').pause();}")
                    }
                    
                    override fun onStop() {
                        super.onStop()
                        Log.d(TAG, "MediaSession: onStop вызван")
                        sendJavaScriptToWebView("if(document.querySelector('video')){document.querySelector('video').pause();}")
                    }
                    
                    override fun onSkipToNext() {
                        super.onSkipToNext()
                        Log.d(TAG, "MediaSession: onSkipToNext вызван")
                        sendJavaScriptToWebView("""
                            (function() {
                                // Имитируем нажатие на кнопку "Следующая серия" в плеере
                                const nextButtons = document.querySelectorAll('[class*="next"], [title*="След"], [aria-label*="след"], .next-episode, .next-button');
                                if (nextButtons.length > 0) {
                                    nextButtons[0].click();
                                    return true;
                                }
                                return false;
                            })();
                        """.trimIndent())
                    }
                    
                    override fun onSkipToPrevious() {
                        super.onSkipToPrevious()
                        Log.d(TAG, "MediaSession: onSkipToPrevious вызван")
                        sendJavaScriptToWebView("""
                            (function() {
                                // Имитируем нажатие на кнопку "Предыдущая серия" в плеере
                                const prevButtons = document.querySelectorAll('[class*="prev"], [title*="Пред"], [aria-label*="пред"], .prev-episode, .prev-button');
                                if (prevButtons.length > 0) {
                                    prevButtons[0].click();
                                    return true;
                                }
                                return false;
                            })();
                        """.trimIndent())
                    }
                    
                    override fun onSeekTo(pos: Long) {
                        super.onSeekTo(pos)
                        Log.d(TAG, "MediaSession: onSeekTo вызван: $pos")
                        sendJavaScriptToWebView("if(document.querySelector('video')){document.querySelector('video').currentTime = ${pos / 1000.0};}")
                    }
                })
                
                // Активируем медиа-сессию
                mediaSession?.isActive = true
                
                // Настраиваем состояние воспроизведения
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val stateBuilder = PlaybackState.Builder()
                        .setActions(
                            PlaybackState.ACTION_PLAY or 
                            PlaybackState.ACTION_PAUSE or 
                            PlaybackState.ACTION_SKIP_TO_NEXT or 
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or 
                            PlaybackState.ACTION_SEEK_TO or 
                            PlaybackState.ACTION_PLAY_PAUSE
                        )
                        .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                    
                    mediaSession?.setPlaybackState(stateBuilder.build())
                }
                
                Log.d(TAG, "MediaSession успешно настроена")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при настройке MediaSession: ${e.message}")
            }
        }
    }
    
    /**
     * Метод для отправки JavaScript в WebView
     */
    private fun sendJavaScriptToWebView(jsCode: String) {
        try {
            currentWebView?.post {
                try {
                    currentWebView?.evaluateJavascript(jsCode, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при выполнении JavaScript: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отправке JavaScript: ${e.message}")
        }
    }
    
    /**
     * Обновляет состояние MediaSession на основе состояния воспроизведения видео
     */
    private fun updateMediaSessionState(isPlaying: Boolean, position: Long, duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            try {
                val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
                
                val stateBuilder = PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or 
                        PlaybackState.ACTION_PAUSE or 
                        PlaybackState.ACTION_SKIP_TO_NEXT or 
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or 
                        PlaybackState.ACTION_SEEK_TO or 
                        PlaybackState.ACTION_PLAY_PAUSE
                    )
                    .setState(state, position, 1.0f)
                
                mediaSession?.setPlaybackState(stateBuilder.build())
                
                // Создаем PendingIntent для возврата в приложение из PiP режима
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                    
                    val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
                    
                    // Обновляем PiP действия
                    val builder = PictureInPictureParams.Builder()
                    mediaSession?.setPlaybackState(stateBuilder.build())
                    
                    try {
                        setPictureInPictureParams(builder.build())
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка при обновлении PiP параметров: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обновлении состояния MediaSession: ${e.message}")
            }
        }
    }
    
    // Обработка изменения конфигурации (включая вход/выход из режима PiP)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Проверяем, находимся ли мы в режиме PiP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val wasPipMode = isInPipMode
            isInPipMode = isInPictureInPictureMode
            
            // Событие входа в режим PiP
            if (isInPipMode && !wasPipMode) {
                Log.d(TAG, "Приложение вошло в режим PiP")
                
                // Скрываем элементы UI, которые не нужны в режиме PiP
                try {
                    currentWebView?.evaluateJavascript("""
                        (function() {
                            // Отправляем событие, что мы вошли в режим PiP
                            document.documentElement.setAttribute('data-pip-mode', 'true');
                            
                            // Если есть наша функция обработки PiP, вызываем её
                            if (window.ReYoHoHoPipHelper && window.ReYoHoHoPipHelper.onEnterPipMode) {
                                window.ReYoHoHoPipHelper.onEnterPipMode();
                            }
                            
                            // Проверяем состояние видео каждую секунду и обновляем MediaSession
                            window._pipVideoMonitorInterval = setInterval(function() {
                                const video = document.querySelector('video');
                                if (video) {
                                    try {
                                        // Передаем состояние видео в Android
                                        AndroidActivity.updateMediaSessionState(
                                            String(!video.paused), 
                                            String(Math.floor(video.currentTime * 1000)), 
                                            String(Math.floor(video.duration * 1000))
                                        );
                                    } catch(e) {
                                        console.log('Ошибка при обновлении MediaSession:', e);
                                    }
                                }
                            }, 1000);
                        })();
                    """.trimIndent(), null)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при обработке входа в режим PiP: ${e.message}")
                }
            } else if (!isInPipMode && wasPipMode) {
                // Событие выхода из режима PiP
                Log.d(TAG, "Приложение вышло из режима PiP")
                
                // Восстанавливаем нормальный интерфейс
                try {
                    currentWebView?.evaluateJavascript("""
                        (function() {
                            // Отправляем событие, что мы вышли из режима PiP
                            document.documentElement.removeAttribute('data-pip-mode');
                            
                            // Очищаем интервал мониторинга
                            if (window._pipVideoMonitorInterval) {
                                clearInterval(window._pipVideoMonitorInterval);
                                window._pipVideoMonitorInterval = null;
                            }
                            
                            // Если есть наша функция обработки PiP, вызываем её
                            if (window.ReYoHoHoPipHelper && window.ReYoHoHoPipHelper.onExitPipMode) {
                                window.ReYoHoHoPipHelper.onExitPipMode();
                            }
                        })();
                    """.trimIndent(), null)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при обработке выхода из режима PiP: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Обновляет состояние MediaSession (вызывается из JavaScript)
     */
    @JavascriptInterface
    fun updateMediaSessionState(isPlayingStr: String, positionStr: String, durationStr: String) {
        try {
            val isPlaying = isPlayingStr.toBoolean()
            val position = positionStr.toLong()
            val duration = durationStr.toLong()
            
            Log.d(TAG, "Обновление состояния MediaSession: isPlaying=$isPlaying, position=$position, duration=$duration")
            
            // Выполняем на основном потоке
            runOnUiThread {
                updateMediaSessionState(isPlaying, position, duration)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при обновлении состояния MediaSession из JS: ${e.message}")
        }
    }
}

/**
 * Экран выбора типа устройства при первом запуске
 */
@Composable
fun DeviceTypeSelectionScreen(
    onSelectDeviceType: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Добро пожаловать в ReYohoho!",
                color = Color.White,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            Text(
                text = "На каком устройстве вы используете приложение?",
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Кнопка для Android с эффектами взаимодействия
            val androidInteractionSource = remember { MutableInteractionSource() }
            val isAndroidHovered by androidInteractionSource.collectIsHoveredAsState()
            val isAndroidFocused by androidInteractionSource.collectIsFocusedAsState()
            val isAndroidPressed by androidInteractionSource.collectIsPressedAsState()
            
            // Определяем активное состояние для кнопки Android
            val isAndroidActive = isAndroidHovered || isAndroidFocused || isAndroidPressed
            val androidBgColor = when {
                isAndroidPressed || isAndroidFocused -> Color(0xFF4CAF50) // Зеленый цвет при нажатии или фокусе
                isAndroidHovered -> Color.White
                else -> Color.Black
            }
            val androidTextColor = when {
                isAndroidActive -> Color.White
                else -> Color.White
            }
            val androidBorderColor = when {
                isAndroidPressed || isAndroidFocused -> Color(0xFF4CAF50) // Зеленый цвет при нажатии или фокусе
                isAndroidHovered -> Color.White
                else -> Color.DarkGray
            }
            
            Button(
                onClick = { onSelectDeviceType(SettingsManager.DEVICE_TYPE_ANDROID) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(2.dp, androidBorderColor, shape = MaterialTheme.shapes.medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidBgColor,
                    contentColor = androidTextColor
                ),
                interactionSource = androidInteractionSource
            ) {
                Text(
                    text = "Android телефон / планшет",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Кнопка для Android TV с эффектами взаимодействия
            val androidTvInteractionSource = remember { MutableInteractionSource() }
            val isAndroidTvHovered by androidTvInteractionSource.collectIsHoveredAsState()
            val isAndroidTvFocused by androidTvInteractionSource.collectIsFocusedAsState()
            val isAndroidTvPressed by androidTvInteractionSource.collectIsPressedAsState()
            
            // Определяем активное состояние для кнопки Android TV
            val isAndroidTvActive = isAndroidTvHovered || isAndroidTvFocused || isAndroidTvPressed
            val androidTvBgColor = when {
                isAndroidTvPressed || isAndroidTvFocused -> Color(0xFF4CAF50) // Зеленый цвет при нажатии или фокусе
                isAndroidTvHovered -> Color.White
                else -> Color.Black
            }
            val androidTvTextColor = when {
                isAndroidTvActive -> Color.White
                else -> Color.White
            }
            val androidTvBorderColor = when {
                isAndroidTvPressed || isAndroidTvFocused -> Color(0xFF4CAF50) // Зеленый цвет при нажатии или фокусе
                isAndroidTvHovered -> Color.White
                else -> Color.DarkGray
            }
            
            Button(
                onClick = { onSelectDeviceType(SettingsManager.DEVICE_TYPE_ANDROID_TV) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(2.dp, androidTvBorderColor, shape = MaterialTheme.shapes.medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidTvBgColor,
                    contentColor = androidTvTextColor
                ),
                interactionSource = androidTvInteractionSource
            ) {
                Text(
                    text = "Android TV",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Экран предложения установить приложение-курсор для Android TV
 */
@Composable
fun TVCursorPromptScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Для удобства использования на Android TV",
                color = Color.White,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Рекомендуем установить приложение для управления виртуальным курсором",
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Кнопка для скачивания приложения с GitHub с эффектами взаимодействия
            val githubInteractionSource = remember { MutableInteractionSource() }
            val isGithubHovered by githubInteractionSource.collectIsHoveredAsState()
            val isGithubFocused by githubInteractionSource.collectIsFocusedAsState()
            val isGithubPressed by githubInteractionSource.collectIsPressedAsState()
            
            // Определяем активное состояние для кнопки GitHub
            val isGithubActive = isGithubHovered || isGithubFocused || isGithubPressed
            val githubBgColor = when {
                isGithubPressed || isGithubFocused -> Color(0xFF4CAF50) // Зеленый цвет при нажатии или фокусе
                isGithubHovered -> Color.White
                else -> Color.Black
            }
            val githubTextColor = when {
                isGithubPressed || isGithubFocused -> Color.White
                isGithubHovered -> Color.Black
                else -> Color.White
            }
            val githubBorderColor = when {
                isGithubPressed || isGithubFocused -> Color(0xFF4CAF50) // Зеленый цвет при нажатии или фокусе
                isGithubHovered -> Color.White
                else -> Color.DarkGray
            }
            
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.TV_CURSOR_APP_URLS[0]))
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(2.dp, githubBorderColor, shape = MaterialTheme.shapes.medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = githubBgColor,
                    contentColor = githubTextColor
                ),
                interactionSource = githubInteractionSource
            ) {
                Text(
                    text = "Скачать MATVT v1.0.6 с GitHub",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            // Кнопка закрытия с эффектами взаимодействия
            val closeInteractionSource = remember { MutableInteractionSource() }
            val isCloseHovered by closeInteractionSource.collectIsHoveredAsState()
            val isCloseFocused by closeInteractionSource.collectIsFocusedAsState()
            val isClosePressed by closeInteractionSource.collectIsPressedAsState()
            
            // Определяем активное состояние для кнопки закрытия
            val isCloseActive = isCloseHovered || isCloseFocused || isClosePressed
            val closeBgColor = when {
                isClosePressed || isCloseFocused -> Color(0xFF4CAF50) // Зеленый цвет при нажатии или фокусе
                isCloseHovered -> Color.White
                else -> Color.Black
            }
            val closeTextColor = when {
                isClosePressed || isCloseFocused -> Color.White
                isCloseHovered -> Color.Black
                else -> Color.White
            }
            val closeBorderColor = when {
                isClosePressed || isCloseFocused -> Color(0xFF4CAF50) // Зеленый цвет при нажатии или фокусе
                isCloseHovered -> Color.White
                else -> Color.DarkGray
            }
            
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(2.dp, closeBorderColor, shape = MaterialTheme.shapes.medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = closeBgColor,
                    contentColor = closeTextColor
                ),
                interactionSource = closeInteractionSource
            ) {
                Text(
                    text = "Продолжить без установки",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun JacredScreen(
    kpId: String,
    onClose: () -> Unit,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    val disableZoom = settingsManager.disableZoomFlow.collectAsState().value
    val torrServeManager = remember { TorrServeManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var urlLoaded by remember { mutableStateOf(false) }

    // BackHandler для jacred
    BackHandler(enabled = true) { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.setSupportZoom(!disableZoom)
                    settings.builtInZoomControls = !disableZoom
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    setInitialScale(100)
                    loadUrl("https://jacred.xyz/")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!urlLoaded) {
                                urlLoaded = true
                                view?.evaluateJavascript("document.getElementById('s').value = 'kp$kpId'; document.getElementById('submitButton').click();", null)
                            }

                            // Добавляем JavaScript для добавления кнопки "Смотреть" к результатам
                            view?.evaluateJavascript("""
                                (function() {
                                    // Функция для добавления кнопок к результатам
                                    function addWatchButtons() {
                                        // Находим все результаты с магнет-ссылками
                                        const results = document.querySelectorAll('.webResult');
                                        
                                        results.forEach(result => {
                                            // Проверяем, есть ли уже кнопка "Смотреть"
                                            if (result.querySelector('.watch-button')) {
                                                return;
                                            }
                                            
                                            // Находим магнет-ссылку
                                            const magnetLink = result.querySelector('.magneto');
                                            if (!magnetLink || !magnetLink.href || !magnetLink.href.startsWith('magnet:')) {
                                                return;
                                            }
                                            
                                            // Находим блок с размером для позиционирования кнопки
                                            const sizeElement = result.querySelector('.size');
                                            if (!sizeElement) {
                                                return;
                                            }
                                            
                                            // Создаем кнопку "Смотреть"
                                            const watchButton = document.createElement('a');
                                            watchButton.className = 'watch-button';
                                            watchButton.href = '#';
                                            watchButton.textContent = 'Смотреть';
                                            watchButton.style.marginRight = '10px';
                                            watchButton.style.color = '#4CAF50';
                                            watchButton.style.fontWeight = 'bold';
                                            
                                            // Добавляем обработчик события клика
                                            watchButton.onclick = function(e) {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                
                                                // Передаем магнет-ссылку в Android
                                                if (window.Android) {
                                                    window.Android.addMagnetToTorrServe(magnetLink.href);
                                                }
                                                
                                                return false;
                                            };
                                            
                                            // Вставляем кнопку перед элементом размера
                                            sizeElement.parentNode.insertBefore(watchButton, sizeElement);
                                        });
                                    }
                                    
                                    // Запускаем функцию сразу
                                    addWatchButtons();
                                    
                                    // И через интервал, так как результаты могут загружаться асинхронно
                                    setInterval(addWatchButtons, 1000);
                                    
                                    // Стили для кнопки
                                    const style = document.createElement('style');
                                    style.textContent = `
                                        .watch-button {
                                            display: inline-block;
                                            padding: 2px 8px;
                                            background-color: rgba(0, 0, 0, 0.7);
                                            border-radius: 4px;
                                            text-decoration: none !important;
                                            transition: all 0.3s ease;
                                            z-index: 1000;
                                        }
                                        .watch-button:hover {
                                            background-color: #4CAF50;
                                            color: white !important;
                                        }
                                    `;
                                    document.head.appendChild(style);
                                })();
                            """, null)
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            return if (!url.contains("jacred.xyz")) {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(intent)
                                true
                            } else {
                                false
                            }
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            if (url == null) return false
                            return if (!url.contains("jacred.xyz")) {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(intent)
                                true
                            } else {
                                false
                            }
                        }
                    }
                    
                    // Добавляем JavaScript интерфейс для работы с TorrServe
                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun addMagnetToTorrServe(magnetUrl: String) {
                            Log.d("JacredScreen", "Получена магнет-ссылка: $magnetUrl")
                            coroutineScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "Добавление торрента...", Toast.LENGTH_SHORT).show()
                                val success = torrServeManager.addAndPlay(magnetUrl)
                                if (!success) {
                                    Toast.makeText(context, "Ошибка при добавлении торрента", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }, "Android")
                }
            },
            update = { view ->
                view.settings.setSupportZoom(!disableZoom)
                view.settings.builtInZoomControls = !disableZoom
            },
            modifier = Modifier.fillMaxSize()
        )
        // Кнопка назад
        androidx.compose.material3.IconButton(
            onClick = { onClose() },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .size(48.dp)
                .background(Color(0xAA222222), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Назад",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}