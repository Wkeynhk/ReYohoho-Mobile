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
            
            setContent {
                // Всегда используем темную тему независимо от настроек системы
                ReYohohoTheme(darkTheme = true) {
                    // Состояние для отображения настроек
                    var showSettings by remember { mutableStateOf(false) }
                    
                    // Состояние для отображения первоначального выбора платформы
                    var showDeviceTypeSelection by remember { mutableStateOf(!settingsManager.isDeviceTypeSet()) }
                    
                    // Состояние для отображения диалога установки курсора на TV
                    var showTVCursorPrompt by remember { mutableStateOf(false) }
                    
                    // Используем простой подход - храним WebView в состоянии
                    var webView by remember { mutableStateOf<WebView?>(null) }
                    
                    // Функция для обновления страницы
                    val refreshPage: () -> Unit = {
                        webView?.reload()
                        Log.d(TAG, "Страница обновлена")
                    }
                    
                    // Следим за изменениями зеркала сайта
                    val selectedMirror by settingsManager.siteMirrorFlow.collectAsState()
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

                    val appVersion = "3.11"

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
                            
                            // Кнопка настроек отображается только когда не показаны настройки
                            if (!showSettings) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Button(
                                        onClick = { showSettings = true },
                                        modifier = Modifier
                                            .padding(end = 24.dp, bottom = 80.dp)
                                            .size(56.dp),
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Black,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "⚙",
                                            fontSize = 28.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
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
     * Инициализация блокировщика рекламы при запуске приложения
     */
    private fun initializeAdBlocker() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Начинаем инициализацию блокировщика рекламы...")
                AdBlocker.initialize()
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
            setupImmersiveMode()
        }
    }
    
    /**
     * Обработка кнопки "Назад" для корректной навигации в WebView
     */
    @Deprecated("Заменено на OnBackPressedDispatcher", level = DeprecationLevel.WARNING)
    override fun onBackPressed() {
        // Сначала проверяем, можно ли вернуться назад в WebView
        if (currentWebView?.canGoBack() == true) {
            currentWebView?.goBack()
            return
        }
        
        // Требуем двойное нажатие для выхода из приложения
        if (doubleBackToExitPressedOnce) {
            // Принудительно сохраняем cookie перед выходом
            CookieManager.getInstance().flush()
            super.onBackPressed()
            return
        }
        
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, "Нажмите ещё раз для выхода", Toast.LENGTH_SHORT).show()
        
        // Сбрасываем флаг через 2 секунды
        Handler(Looper.getMainLooper()).postDelayed({
            doubleBackToExitPressedOnce = false
        }, 2000)
    }
    
    /**
     * Вызывается при уничтожении активности
     */
    override fun onDestroy() {
        // Сохраняем cookie перед закрытием приложения
        try {
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении cookie: ${e.message}")
        }
        
        super.onDestroy()
    }

    private fun startUpdateDownload(context: Context) {
        lifecycleScope.launch {
            try {
                val result = com.example.reyohoho.ui.UpdateChecker.checkForUpdate()
                if (result.isUpdateAvailable) {
                    com.example.reyohoho.ui.UpdateChecker.downloadAndInstallApk(context, result.downloadUrl, result.latestVersion, autoInstall = true)
                }
            } catch (e: Exception) {
                // No logging needed here
            }
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