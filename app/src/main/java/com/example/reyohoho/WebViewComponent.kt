package com.example.reyohoho

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.reyohoho.ui.SettingsManager
import java.io.ByteArrayInputStream
import kotlinx.coroutines.launch

/**
 * JavaScript-интерфейс для связи между WebView и Java-кодом
 */
class AdBlockerJSInterface(private val context: Context) {
    private val TAG = "AdBlocker"
    
    @JavascriptInterface
    fun onAdDetected(message: String): Boolean {
        Log.d(TAG, "Реклама обнаружена: $message")
        return true
    }
    
    @JavascriptInterface
    fun logMessage(message: String) {
        Log.d(TAG, message)
    }
    
    @JavascriptInterface
    fun openExternalUrl(url: String) {
        try {
            // Запускаем в UI-потоке, т.к. JS-интерфейс вызывается в JS-потоке
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Открыта внешняя ссылка: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при открытии внешней ссылки: ${e.message}")
        }
    }
    
    @JavascriptInterface
    fun enterPictureInPictureMode(widthStr: String = "16", heightStr: String = "9") {
        try {
            // Преобразуем строковые параметры в целые числа, с защитой от ошибок
            val width = widthStr.toIntOrNull() ?: 16
            val height = heightStr.toIntOrNull() ?: 9
            
            Log.d(TAG, "JS вызвал переход в режим PiP с соотношением $width:$height")
            
            // Поскольку JS-интерфейс вызывается в JS-потоке, но UI-операции должны выполняться в основном потоке
            val activity = context as? MainActivity
            activity?.runOnUiThread {
                activity.enterPictureInPictureMode(width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при переходе в режим PiP из JS: ${e.message}")
        }
    }
}

/**
 * Класс для обработки полноэкранного режима в WebView
 */
class FullScreenWebChromeClient(private val activity: Activity) : WebChromeClient() {
    private var mCustomView: View? = null
    private var mCustomViewCallback: WebChromeClient.CustomViewCallback? = null
    private var mOriginalOrientation: Int = 0
    private var mOriginalSystemUiVisibility: Int = 0
    // Состояние блокировщика фона
    private var mOverlayView: View? = null
    
    @Suppress("DEPRECATION")
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (mCustomView != null) {
            // Если уже в полноэкранном режиме, сначала закрываем его
            onHideCustomView()
            return
        }
        
        try {
            // Сохраняем текущее состояние
            mCustomView = view
            mOriginalSystemUiVisibility = activity.window.decorView.systemUiVisibility
            mOriginalOrientation = activity.requestedOrientation
            mCustomViewCallback = callback
            
            // Получаем корневой контейнер для размещения полноэкранного вида
            val decorView = activity.window.decorView as FrameLayout
            
            // Очищаем любые предыдущие view, если они есть
            if (mOverlayView != null) {
                decorView.removeView(mOverlayView)
                mOverlayView = null
            }
            
            // Устанавливаем черный фон для view
            view.setBackgroundColor(Color.BLACK)
            
            // Устанавливаем важные параметры для избегания проблем с оверлеем
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            
            // Добавляем полноэкранный вид с корректными параметрами
            decorView.addView(view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ))
            
            // Скрываем системную навигацию в полноэкранном режиме
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
            
            // --- Исправление: повторное скрытие системных панелей при возврате фокуса ---
            view.setOnSystemUiVisibilityChangeListener {
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
            // --- конец исправления ---
            
            // Устанавливаем ориентацию на альбомную для видео
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            
            // Даем фокус полноэкранному view для обработки нажатий
            view.requestFocus()
            
            // Добавляем logger
            Log.d("FullScreen", "Вход в полноэкранный режим успешно выполнен")
        } catch (e: Exception) {
            Log.e("WebView", "Ошибка при показе полноэкранного режима: ${e.message}")
            e.printStackTrace()
            // В случае ошибки, пытаемся очистить состояние
            onHideCustomView()
        }
    }
    
    @Suppress("DEPRECATION")
    override fun onHideCustomView() {
        try {
            // Проверяем, что мы действительно находимся в полноэкранном режиме
            if (mCustomView == null) {
                return
            }
            
            // Восстанавливаем системный интерфейс
            activity.window.decorView.systemUiVisibility = mOriginalSystemUiVisibility
            
            // Восстанавливаем исходную ориентацию
            activity.requestedOrientation = mOriginalOrientation
            
            // Удаляем полноэкранный вид
            val decorView = activity.window.decorView as FrameLayout
            decorView.removeView(mCustomView)
            
            // Уведомляем WebView о завершении полноэкранного режима
            mCustomViewCallback?.onCustomViewHidden()
            
            // Очищаем переменные
            mCustomView = null
            mCustomViewCallback = null
            
            Log.d("FullScreen", "Выход из полноэкранного режима успешно выполнен")
        } catch (e: Exception) {
            Log.e("WebView", "Ошибка при выходе из полноэкранного режима: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Переопределяем дополнительный метод для совместимости с некоторыми видеоплеерами
    override fun getVideoLoadingProgressView(): View? {
        val frameLayout = FrameLayout(activity)
        frameLayout.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        frameLayout.setBackgroundColor(Color.BLACK)
        return frameLayout
    }
    
    fun isInFullScreen(): Boolean = mCustomView != null
}

class CustomWebViewClient(
    private val onUrlChanged: (String) -> Unit,
    private val onPageFinished: () -> Unit,
    private val context: Context,
    private val settingsManager: SettingsManager?
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        
        // Всегда перехватываем загрузку видеофайлов и любых файлов для скачивания
        if (url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".avi") || 
            url.endsWith(".mov") || url.endsWith(".wmv") || url.endsWith(".flv") ||
            url.contains("download", ignoreCase = true)) {
            
            // Получаем название со страницы с помощью JS
            view?.evaluateJavascript(
                """
                (function() {
                    try {
                        const strongs = Array.from(document.querySelectorAll('strong'));
                        const titleStrong = strongs.find(s => s.textContent.trim().includes('Название:'));
                        if (titleStrong && titleStrong.parentElement) {
                            return titleStrong.parentElement.textContent.replace('Название:', '').trim();
                        }
                        // Попробуем найти заголовок h1 как запасной вариант
                        const h1 = document.querySelector('h1');
                        if (h1) {
                            return h1.textContent.trim();
                        }
                    } catch (e) {
                        return null;
                    }
                    return null;
                })();
                """.trimIndent()
            ) { title ->
                // title будет в кавычках, если не null, убираем их
                val pageTitle = title?.removeSurrounding("\"")?.takeIf { it != "null" }
                
                try {
                    val downloadManager = AppDownloadManager.getInstance(context)
                    val userAgent = view.settings?.userAgentString
                    downloadManager.downloadFile(url, userAgent, null, "video/mp4", pageTitle)
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Загрузка файла начата", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("WebView", "Ошибка при загрузке файла: ${e}")
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            
            return true
        }
        
        // Проверяем, является ли URL прямым файлом для скачивания
        if (url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".avi") || 
            url.endsWith(".mov") || url.endsWith(".wmv") || url.endsWith(".flv")) {
            try {
                // Используем наш собственный менеджер загрузок вместо внешней активности
                val downloadManager = AppDownloadManager.getInstance(context)
                val userAgent = view?.settings?.userAgentString
                downloadManager.downloadFile(url, userAgent, null, "video/mp4")
                
                // Показываем уведомление о начатой загрузке
                Toast.makeText(context, "Загрузка файла начата", Toast.LENGTH_SHORT).show()
                
                return true
            } catch (e: Exception) {
                Log.e("WebView", "Ошибка при загрузке файла: ${e.message}")
                // Если не удалось загрузить через наш менеджер, используем системный
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                } catch (e2: Exception) {
                    Log.e("WebView", "Ошибка при открытии внешней ссылки: ${e2.message}")
                }
            }
        }
        
        // Проверяем, является ли URL внешней ссылкой
        val baseDomains = listOf(
            "reyohoho.github.io",
            "reyohoho-gitlab.vercel.app",
            "reyohoho.gitlab.io",
            "reyohoho.serv00.net",
            "reyohoho.onrender.com",
            "reyohoho.vercel.app",
            "reyohoho.surge.sh"
        )
        
        val isExternalLink = !baseDomains.any { url.contains(it) }
        
        if (isExternalLink) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.e("WebView", "Ошибка при открытии внешней ссылки: "+e.message)
            }
        }
        
        // Для внутренних ссылок используем стандартное поведение WebView (чтобы работала история)
        return false
    }
    
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (AdBlocker.isAd(url)) {
            Log.d("AdBlocker", "Заблокирован: $url")
            // Возвращаем пустой ответ для блокировки ресурса
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream("".toByteArray())
            )
        }
        return super.shouldInterceptRequest(view, request)
    }
    
    override fun onPageStarted(view: WebView, loadUrl: String, favicon: Bitmap?) {
        super.onPageStarted(view, loadUrl, favicon)
        Log.d("AdBlocker", "Загрузка страницы: $loadUrl")
        // Не сохраняем URL здесь, чтобы избежать сохранения редиректов
    }
    
    override fun onPageFinished(view: WebView, loadUrl: String) {
        super.onPageFinished(view, loadUrl)
        onPageFinished()
        Log.d("AdBlocker", "Страница загружена: $loadUrl")
        
        // Сохраняем текущий URL страницы (может отличаться от loadUrl из-за редиректов)
        val currentUrl = view.url ?: loadUrl
        onUrlChanged(currentUrl)
        
        // Применяем блокировщик рекламы
        injectRussianAdBlocker(view)
        
        // Добавляем поддержку полноэкранного режима для видео
        injectFullscreenVideoSupport(view)
        
        // Добавляем поддержку Picture-in-Picture (PiP)
        injectPipSupport(view)
        
        // Исправляем работу кнопки удаления истории
        injectDeleteHistoryButtonFix(view)
        
        // Применяем настройки отступа
        val removeSpacing = settingsManager?.isTopSpacingRemoved() ?: false
        if (!removeSpacing) {
            injectHeaderSpacing(view)
        } else {
            removeHeaderSpacing(view)
        }
        // --- ДОБАВЛЕНО: повторное внедрение pull to refresh ---
        val pullToRefreshEnabled = settingsManager?.isPullToRefreshEnabled() ?: true
        if (pullToRefreshEnabled) {
            injectPullToRefresh(view)
        } else {
            removePullToRefresh(view)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdBlockWebView(
    url: String, 
    modifier: Modifier = Modifier,
    settingsManager: SettingsManager? = null,
    onUrlChanged: (String) -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    
    // Получаем SharedPreferences для сохранения URL
    val prefs = remember { context.getSharedPreferences("WebViewPrefs", Context.MODE_PRIVATE) }
    
    val settingsManagerInstance = settingsManager ?: remember {
        try {
            SettingsManager.getInstance(context)
        } catch (e: Exception) {
            Log.e("AdBlockWebView", "Ошибка получения SettingsManager: ", e)
            null
        }
    }
    
    // Состояние настройки загрузки на главную страницу
    val loadOnMainPage = settingsManagerInstance?.loadOnMainPageFlow?.collectAsState()
    
    // Определяем URL для загрузки
    val urlToLoad = if (loadOnMainPage?.value == true) {
        // Если включен переключатель "Загружать на последней странице", используем последний сохраненный URL
        prefs.getString("last_url", url) ?: url
    } else {
        // Если выключен, всегда загружаем главную страницу зеркала
        url
    }
    
    // Состояние удаления отступа сверху
    val removeTopSpacing = settingsManagerInstance?.removeTopSpacingFlow?.collectAsState()
    
    // Состояние полноэкранного режима
    val fullscreenMode = settingsManagerInstance?.fullscreenModeFlow?.collectAsState()
    
    // Состояние pull to refresh
    val pullToRefresh = settingsManagerInstance?.pullToRefreshFlow?.collectAsState()
    
    // Состояние отключения зума
    val disableZoom = settingsManagerInstance?.disableZoomFlow?.collectAsState()
    
    // Применяем изменение иммерсивного режима при изменении настройки
    LaunchedEffect(fullscreenMode?.value) {
        try {
            (activity as? MainActivity)?.setupImmersiveMode()
        } catch (e: Exception) {
            Log.e("AdBlockWebView", "Ошибка применения полноэкранного режима: ${e.message}")
        }
    }
    
    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            (activity as? Activity)?.finish()
        }
    }
    
    // Используем черный фон для корректного отображения в темной теме
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.Black)
    ) {
        AndroidView(
            factory = { ctx: Context ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    // Настройка WebView
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        setDatabasePath(context.getDir("databases", Context.MODE_PRIVATE).path)
                        saveFormData = true
                        savePassword = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        
                        // Настройки зума в зависимости от настроек
                        val zoomDisabled = disableZoom?.value ?: true
                        setSupportZoom(!zoomDisabled)
                        builtInZoomControls = !zoomDisabled
                        displayZoomControls = false
                        
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = settings.userAgentString.replace("; wv", "")
                    }
                    
                    // Включение поддержки темной темы
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                    }
                    
                    // Настройка обработки файлов
                    webViewClient = CustomWebViewClient(
                        onUrlChanged = { newUrl ->
                            onUrlChanged(newUrl)
                            prefs.edit().putString("last_url", newUrl).apply()
                        },
                        onPageFinished = { isLoading = false },
                        context = context,
                        settingsManager = settingsManagerInstance
                    )
                    
                    // Настройка обработки полноэкранного режима
                    val chromeClient = activity?.let { FullScreenWebChromeClient(it) } ?: FullScreenWebChromeClient(context as Activity)
                    webChromeClient = chromeClient
                    if (activity is MainActivity) {
                        activity.fullScreenWebChromeClient = chromeClient
                    }
                    
                    // Добавляем JavaScript интерфейс
                    val adBlockerInterface = AdBlockerJSInterface(context)
                    addJavascriptInterface(adBlockerInterface, "Android")
                    addJavascriptInterface(adBlockerInterface, "NativeAdBlocker")
                    
                    // Регистрация интерфейса для обработки вызовов из плеера
                    try {
                        // Пытаемся получить доступ к MainActivity
                        val mainActivity = context as? MainActivity
                        if (mainActivity != null) {
                            // Регистрируем саму активность как JavaScriptInterface для доступа к её методам
                            addJavascriptInterface(mainActivity, "AndroidActivity")
                            Log.d("WebView", "MainActivity успешно зарегистрирована как JavaScriptInterface")
                        }
                    } catch (e: Exception) {
                        Log.e("WebView", "Ошибка при регистрации JavaScriptInterface для MainActivity: ${e.message}")
                    }
                    
                    // Обработчик консольных сообщений
                    // ... existing code ...
                    
                    // Загружаем URL
                    loadUrl(urlToLoad)
                    
                    // Сохраняем ссылку на WebView
                }.also { webView = it }
            },
            update = { view: WebView ->
                // Обновляем WebView при изменении настроек отступа
                val currentRemoveSpacing = removeTopSpacing?.value ?: false
                if (currentRemoveSpacing) {
                    removeHeaderSpacing(view)
                } else {
                    injectHeaderSpacing(view)
                }
                
                // Обновляем настройки зума
                val zoomDisabled = disableZoom?.value ?: true
                view.settings.setSupportZoom(!zoomDisabled)
                view.settings.builtInZoomControls = !zoomDisabled
                
                // Применяем pull to refresh если включено
                val pullToRefreshEnabled = pullToRefresh?.value ?: true
                if (pullToRefreshEnabled) {
                    injectPullToRefresh(view)
                } else {
                    removePullToRefresh(view)
                }
                
                // Принудительно применяем pull to refresh через небольшую задержку
                if (pullToRefreshEnabled) {
                    view.postDelayed({
                        injectPullToRefresh(view)
                    }, 500)
                }
                
                // Если изменился urlToLoad, и он отличается от текущего, загружаем его
                if (view.url != urlToLoad) {
                    view.loadUrl(urlToLoad)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        if (isLoading) {
            // Показываем индикатор загрузки с белым цветом на черном фоне
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = ComposeColor.White // Белый индикатор для темного фона
            )
        }
    }
}

/**
 * Класс для сохранения состояния WebView
 */
class WebViewState(val url: String) {
    val bundle = Bundle()
}

/**
 * Внедряет JavaScript для поддержки полноэкранного режима видео
 */
private fun injectFullscreenVideoSupport(webView: WebView) {
    val videoFullscreenJS = """
        (function() {
            console.log('[VideoHelper] Добавление улучшенной поддержки полноэкранного режима');
            
            // Исправление серого полупрозрачного оверлея
            function fixVideoOverlayIssues() {
                // Удаление любых накладывающихся элементов, которые могут блокировать интерфейс
                const overlays = document.querySelectorAll('.overlay, .modal-overlay, .video-overlay, [class*="overlay"], [class*="modal"], [style*="position: fixed"], [style*="z-index: 9999"]');
                overlays.forEach(function(overlay) {
                    // Проверяем, содержит ли оверлей интерактивные элементы
                    const hasInteractiveElements = overlay.querySelector('button, a, input, select, textarea, [role="button"], .deleteButton, .logout-btn, .rating-link, .number-btn');
                    
                    // Если элемент содержит интерактивные элементы, не применяем pointer-events: none
                    if (hasInteractiveElements) {
                        console.log('[VideoHelper] Пропускаем оверлей с интерактивными элементами:', overlay);
                        return;
                    }
                    
                    // Проверяем классы элемента на наличие ключевых слов интерактивных элементов
                    const className = overlay.className || '';
                    if (className.includes('button') || className.includes('btn') || className.includes('rating') || 
                        className.includes('control') || className.includes('interactive') || className.includes('logout') || 
                        className.includes('delete')) {
                        console.log('[VideoHelper] Пропускаем интерактивный элемент по классу:', overlay);
                        return;
                    }
                    
                    // Проверяем, может ли это быть блокирующий оверлей
                    const styles = window.getComputedStyle(overlay);
                    if ((styles.position === 'fixed' || styles.position === 'absolute') && 
                        styles.zIndex > 100 && 
                        (styles.backgroundColor.includes('rgba') || parseFloat(styles.opacity) < 1)) {
                        // Только логируем, не удаляем, чтобы не нарушить работу сайта
                        console.log('[VideoHelper] Обнаружен потенциальный блокирующий оверлей:', overlay);
                        // Делаем элемент полностью прозрачным и некликабельным
                        overlay.style.backgroundColor = 'transparent';
                        overlay.style.pointerEvents = 'none';
                        overlay.style.opacity = '0';
                    }
                });
            }
            
            // Найти все видео на странице и добавить атрибуты для полноэкранного режима
            function enableFullscreenForVideos() {
                const videos = document.querySelectorAll('video');
                videos.forEach(function(video) {
                    // Убедимся, что мы еще не обрабатывали это видео
                    if (!video.hasAttribute('data-fullscreen-handled')) {
                        video.setAttribute('data-fullscreen-handled', 'true');
                        
                        // Необходимые атрибуты для мобильных устройств
                        video.setAttribute('webkit-playsinline', 'true');
                        video.setAttribute('playsinline', 'true');
                        video.setAttribute('x-webkit-airplay', 'allow');
                        
                        // Добавляем контроллеры, если их нет
                        if (!video.hasAttribute('controls')) {
                            video.setAttribute('controls', 'true');
                        }
                        
                        // Разрешаем переход в полноэкранный режим
                        video.setAttribute('allowfullscreen', 'true');
                        
                        // Обработчик клика для мобильных устройств
                        video.addEventListener('click', function() {
                            // Применяем исправление оверлеев при клике
                            fixVideoOverlayIssues();
                        });
                        
                        // Обработчик начала воспроизведения
                        video.addEventListener('play', function() {
                            console.log('[VideoHelper] Видео начало воспроизводиться - исправляем возможные проблемы с оверлеем');
                            // Применяем исправление оверлеев при воспроизведении
                            fixVideoOverlayIssues();
                            // Вызываем метод requestFullscreen для некоторых видео
                            setTimeout(function() {
                                try {
                                    // В большинстве случаев мы не хотим автоматически переходить в полноэкранный режим,
                                    // поэтому закомментировано, но может быть полезно для отладки
                                    // if (video.videoWidth > video.videoHeight && video.duration > 60) {
                                    //     video.requestFullscreen();
                                    // }
                                } catch(e) {
                                    console.log('[VideoHelper] Ошибка при запросе полноэкранного режима:', e);
                                }
                            }, 300);
                        });
                        
                        // Находим родительские элементы видео и убеждаемся, что они не блокируют события
                        let parent = video.parentElement;
                        for (let i = 0; i < 5 && parent; i++) {
                            // Проверяем, есть ли у родителя CSS, который может блокировать события
                            const style = window.getComputedStyle(parent);
                            if (style.pointerEvents === 'none') {
                                parent.style.pointerEvents = 'auto';
                            }
                            parent = parent.parentElement;
                        }
                        
                        // Для видео в iframe тоже добавляем поддержку
                        const parentIframe = video.closest('iframe');
                        if (parentIframe) {
                            parentIframe.setAttribute('allowfullscreen', 'true');
                            parentIframe.setAttribute('webkitallowfullscreen', 'true');
                            parentIframe.setAttribute('mozallowfullscreen', 'true');
                        }
                    }
                });
                
                // Найти все iframe с видео и добавить им атрибуты
                const videoIframes = document.querySelectorAll('iframe[src*="youtube"], iframe[src*="vimeo"], iframe[src*="rutube"], iframe[src*="video"], iframe[src*="player"]');
                videoIframes.forEach(function(iframe) {
                    if (!iframe.hasAttribute('data-fullscreen-handled')) {
                        iframe.setAttribute('data-fullscreen-handled', 'true');
                        iframe.setAttribute('allowfullscreen', 'true');
                        iframe.setAttribute('webkitallowfullscreen', 'true');
                        iframe.setAttribute('mozallowfullscreen', 'true');
                        
                        // Находим родительские контейнеры iframe и исправляем возможные проблемы с z-index
                        let parent = iframe.parentElement;
                        for (let i = 0; i < 5 && parent; i++) {
                            if (parent.style.zIndex && parseInt(parent.style.zIndex) < 0) {
                                parent.style.zIndex = '1';
                            }
                            parent = parent.parentElement;
                        }
                    }
                });
                
                // Отдельно обрабатываем кнопки воспроизведения видео
                const playButtons = document.querySelectorAll('.play-button, [class*="play"], button[class*="play"], [id*="play"], [aria-label*="play"]');
                playButtons.forEach(function(button) {
                    if (!button.hasAttribute('data-play-button-handled')) {
                        button.setAttribute('data-play-button-handled', 'true');
                        button.addEventListener('click', function() {
                            console.log('[VideoHelper] Нажата кнопка воспроизведения - исправляем возможные проблемы с оверлеем');
                            setTimeout(fixVideoOverlayIssues, 300);
                        });
                    }
                });
            }
            
            // Функция для восстановления интерактивности элементов управления
            function restoreInteractiveElements() {
                // Восстанавливаем интерактивность для всех кнопок и элементов управления
                const interactiveElements = document.querySelectorAll('button, a, [role="button"], .deleteButton, .logout-btn, .rating-link, .number-btn, [class*="button"], [class*="btn"]');
                interactiveElements.forEach(function(element) {
                    // Проверяем, не имеет ли элемент атрибут pointer-events
                    const style = window.getComputedStyle(element);
                    if (style.pointerEvents === 'none') {
                        element.style.pointerEvents = 'auto';
                        console.log('[VideoHelper] Восстановлена интерактивность для элемента:', element);
                    }
                    
                    // Восстанавливаем прозрачность, если она была изменена
                    if (parseFloat(style.opacity) < 0.5) {
                        element.style.opacity = '1';
                    }
                    
                    // Проверяем родителей на 3 уровня вверх
                    let parent = element.parentElement;
                    for (let i = 0; i < 3 && parent; i++) {
                        const parentStyle = window.getComputedStyle(parent);
                        if (parentStyle.pointerEvents === 'none') {
                            parent.style.pointerEvents = 'auto';
                            console.log('[VideoHelper] Восстановлена интерактивность для родителя элемента:', parent);
                        }
                        parent = parent.parentElement;
                    }
                });
                
                // Специально проверяем конкретные классы кнопок из задания
                const specialButtons = document.querySelectorAll('.deleteButton, .logout-btn, .rating-container, .rating-link, .rating-numbers, .number-btn');
                specialButtons.forEach(function(button) {
                    button.style.pointerEvents = 'auto';
                    button.style.opacity = '1';
                    console.log('[VideoHelper] Восстановлена интерактивность для специальной кнопки:', button);
                });
            }
            
            // Запускаем функцию сразу
            enableFullscreenForVideos();
            
            // Запускаем исправление оверлеев сразу
            fixVideoOverlayIssues();
            
            // Восстанавливаем интерактивность кнопок
            restoreInteractiveElements();
            
            // И через небольшие интервалы, чтобы обработать асинхронно загруженные элементы
            setTimeout(enableFullscreenForVideos, 1000);
            setTimeout(fixVideoOverlayIssues, 1500);
            setTimeout(restoreInteractiveElements, 1600);
            setTimeout(enableFullscreenForVideos, 2500);
            setTimeout(fixVideoOverlayIssues, 3000);
            setTimeout(restoreInteractiveElements, 3100);
            
            // Наблюдаем за изменениями DOM
            const observer = new MutationObserver(function(mutations) {
                let hasNewNodes = false;
                
                mutations.forEach(function(mutation) {
                    if (mutation.addedNodes.length > 0) {
                        hasNewNodes = true;
                    }
                });
                
                if (hasNewNodes) {
                    enableFullscreenForVideos();
                    fixVideoOverlayIssues();
                    restoreInteractiveElements();
                }
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            // Экспортируем функции в глобальную область
            window.ReYoHoHoVideoHelper = {
                enableFullscreenForVideos: enableFullscreenForVideos,
                fixVideoOverlayIssues: fixVideoOverlayIssues,
                restoreInteractiveElements: restoreInteractiveElements
            };
        })();
    """.trimIndent()
    
    // Добавляем обработчик внешних ссылок через JavaScript
    val externalLinksHandlerJS = """
        (function() {
            console.log('[ExternalLinksHandler] Добавление обработчика внешних ссылок');
            
            // Базовые домены приложения
            const baseDomains = [
                'reyohoho-gitlab.vercel.app',
                'reyohoho.gitlab.io',
                'reyohoho.serv00.net',
                'reyohoho.onrender.com'
            ];
            
            // Функция для обработки внешних ссылок
            function handleExternalLinks() {
                const links = document.querySelectorAll('a[href]');
                
                links.forEach(function(link) {
                    if (!link.__externalLinkHandled) {
                        link.__externalLinkHandled = true;
                        
                        // Получаем атрибут href
                        const href = link.getAttribute('href');
                        if (!href) return;
                        
                        // Проверяем, является ли ссылка внешней
                        const isExternal = isExternalLink(href);
                        
                        if (isExternal) {
                            // Особая обработка для внешних ссылок
                            link.setAttribute('target', '_blank');
                            link.setAttribute('rel', 'noopener noreferrer');
                            
                            // Добавляем обработчик события
                            link.addEventListener('click', function(e) {
                                e.preventDefault();
                                
                                console.log('[ExternalLinksHandler] Клик по внешней ссылке: ' + href);
                                
                                // Открываем ссылку через Android Intent
                                if (window.NativeAdBlocker) {
                                    window.NativeAdBlocker.logMessage('Открытие внешней ссылки: ' + href);
                                    window.NativeAdBlocker.openExternalUrl(href);
                                } else {
                                    // Запасной вариант для случаев, если JS-интерфейс недоступен
                                    window.location.href = href;
                                }
                                
                                return false;
                            });
                        }
                    }
                });

                // Специальная обработка для кнопки Telegram
                handleTelegramButton();
            }
            
            // Функция для проверки, является ли ссылка внешней
            function isExternalLink(url) {
                // Проверяем абсолютные ссылки
                if (url.startsWith('http://') || url.startsWith('https://')) {
                    // Проверяем, содержит ли URL один из наших доменов
                    let isInternalDomain = false;
                    for (let i = 0; i < baseDomains.length; i++) {
                        if (url.indexOf(baseDomains[i]) !== -1) {
                            isInternalDomain = true;
                            break;
                        }
                    }
                    
                    return !isInternalDomain;
                }
                
                // Проверяем специальные схемы (mailto, tel и т.д.)
                if (url.startsWith('mailto:') || url.startsWith('tel:') || 
                    url.startsWith('sms:') || url.startsWith('whatsapp:') ||
                    url.startsWith('viber:') || url.startsWith('telegram:')) {
                    return true;
                }
                
                return false;
            }
            
            // Функция для обработки кнопки Telegram
            function handleTelegramButton() {
                const telegramButtons = document.querySelectorAll('.telegram-btn, button.telegram-btn, [class*="telegram-btn"]');
                
                telegramButtons.forEach(function(button) {
                    if (!button.__telegramButtonHandled) {
                        button.__telegramButtonHandled = true;
                        
                        console.log('[ExternalLinksHandler] Найдена кнопка Telegram');

                        // Вместо добавления нашего обработчика, добавим логирование для анализа
                        const originalClickEvent = button.onclick;
                        
                        // Отслеживаем нажатие без перехвата события
                        button.addEventListener('click', function(e) {
                            // НЕ останавливаем событие
                            // e.preventDefault();
                            // e.stopPropagation();
                            
                            console.log('[ExternalLinksHandler] Клик по кнопке Telegram - пропускаем событие через');
                        }, false);
                        
                        // Добавляем отладочный код для получения информации о URL, на который идет редирект
                        // Отслеживаем изменения location
                        const originalAssign = window.location.assign;
                        window.location.assign = function(url) {
                            if (url.includes('telegram') || url.includes('t.me')) {
                                console.log('[ExternalLinksHandler] Перенаправление на URL: ' + url);
                                if (window.NativeAdBlocker) {
                                    window.NativeAdBlocker.logMessage('Попытка перенаправления на: ' + url);
                                    window.NativeAdBlocker.openExternalUrl(url);
                                    return; // Прерываем оригинальное перенаправление
                                }
                            }
                            return originalAssign.apply(this, arguments);
                        };
                        
                        // Также перехватываем window.open
                        const originalOpen = window.open;
                        window.open = function(url, target, features) {
                            if (url && (url.includes('telegram') || url.includes('t.me'))) {
                                console.log('[ExternalLinksHandler] window.open вызван с URL: ' + url);
                                if (window.NativeAdBlocker) {
                                    window.NativeAdBlocker.logMessage('Попытка открытия окна: ' + url);
                                    window.NativeAdBlocker.openExternalUrl(url);
                                    return null; // Прерываем оригинальное открытие
                                }
                            }
                            return originalOpen.apply(this, arguments);
                        };

                        // Перехватываем прямые изменения window.location.href
                        Object.defineProperty(window.location, 'href', {
                            set: function(url) {
                                if (url && (url.includes('telegram') || url.includes('t.me'))) {
                                    console.log('[ExternalLinksHandler] Установка location.href: ' + url);
                                    if (window.NativeAdBlocker) {
                                        window.NativeAdBlocker.logMessage('Перехват location.href: ' + url);
                                        window.NativeAdBlocker.openExternalUrl(url);
                                        return; // Прерываем оригинальное присваивание
                                    }
                                }
                                // По умолчанию используем оригинальное поведение
                                const oldHref = this.href;
                                delete window.location.href;
                                window.location.href = url;
                                Object.defineProperty(window.location, 'href', arguments.callee);
                                return url;
                            }
                        });

                        // Специальное отслеживание для Telegram OAuth
                        const observer = new MutationObserver(function(mutations) {
                            // Проверяем все добавленные скрипты
                            mutations.forEach(function(mutation) {
                                if (mutation.type === 'childList') {
                                    mutation.addedNodes.forEach(function(node) {
                                        if (node.tagName === 'SCRIPT' && node.src && 
                                            (node.src.includes('telegram') || node.src.includes('t.me'))) {
                                            console.log('[ExternalLinksHandler] Обнаружен скрипт Telegram: ' + node.src);
                                        }
                                        
                                        // Проверяем также добавленные iframe
                                        if (node.tagName === 'IFRAME' && node.src && 
                                            (node.src.includes('telegram') || node.src.includes('t.me'))) {
                                            console.log('[ExternalLinksHandler] Обнаружен iframe Telegram: ' + node.src);
                                            // Перенаправляем iframe на внешний браузер
                                            if (window.NativeAdBlocker) {
                                                window.NativeAdBlocker.openExternalUrl(node.src);
                                                // Скрываем iframe, чтобы не мешал
                                                node.style.display = 'none';
                                            }
                                        }
                                    });
                                }
                            });
                        });

                        // Запускаем специальный наблюдатель для скриптов и iframe
                        observer.observe(document.documentElement, {
                            childList: true,
                            subtree: true
                        });
                    }
                });
                
                // Мониторинг всех форм для обнаружения телеграм-редиректов
                document.querySelectorAll('form').forEach(form => {
                    const action = form.action || '';
                    if (action.includes('telegram') || action.includes('t.me')) {
                        console.log('[ExternalLinksHandler] Найдена форма с действием на Telegram: ' + action);
                        form.addEventListener('submit', function(e) {
                            e.preventDefault();
                            if (window.NativeAdBlocker) {
                                window.NativeAdBlocker.openExternalUrl(action);
                            }
                        });
                    }
                });
            }
            
            // Запускаем функцию сразу
            handleExternalLinks();
            
            // И через задержку для асинхронно загруженных элементов
            setTimeout(handleExternalLinks, 1000);
            setTimeout(handleExternalLinks, 3000);
            
            // Наблюдаем за изменениями DOM для обработки новых ссылок
            const observer = new MutationObserver(function() {
                handleExternalLinks();
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            // Экспортируем функцию в глобальную область
            window.handleExternalLinks = handleExternalLinks;
            window.handleTelegramButton = handleTelegramButton;
            
            console.log('[ExternalLinksHandler] Обработчик внешних ссылок и Telegram активирован');
        })();
    """.trimIndent()
    
    // Дополнительный скрипт для анализа и обнаружения метода Telegram авторизации
    val telegramAnalysisJS = """
        (function() {
            console.log('[TelegramAnalyzer] Запуск анализатора авторизации Telegram');
            
            // Отслеживаем инициализацию Telegram WebApp
            function checkTelegramWebApp() {
                // Проверка наличия глобальной переменной Telegram
                if (window.Telegram) {
                    console.log('[TelegramAnalyzer] Обнаружен Telegram объект в window');
                    
                    // Если есть WebApp
                    if (window.Telegram.WebApp) {
                        console.log('[TelegramAnalyzer] Обнаружен Telegram.WebApp');
                        
                        // Проверяем, есть ли методы инициализации
                        if (typeof window.Telegram.WebApp.initData !== 'undefined') {
                            console.log('[TelegramAnalyzer] Telegram.WebApp.initData: ' + window.Telegram.WebApp.initData);
                        }
                        
                        if (typeof window.Telegram.WebApp.initDataUnsafe !== 'undefined') {
                            console.log('[TelegramAnalyzer] Telegram.WebApp имеет данные инициализации');
                            
                            // Лог информации о пользователе, если доступна
                            if (window.Telegram.WebApp.initDataUnsafe.user) {
                                const user = window.Telegram.WebApp.initDataUnsafe.user;
                                console.log('[TelegramAnalyzer] Пользователь: ' + 
                                    JSON.stringify({
                                        id: user.id,
                                        firstName: user.first_name,
                                        lastName: user.last_name,
                                        username: user.username
                                    }));
                            }
                        }
                    }
                    
                    // Если есть Login Widget
                    if (window.Telegram.Login) {
                        console.log('[TelegramAnalyzer] Обнаружен Telegram.Login');
                    }
                }
                
                // Проверяем наличие скриптов Telegram на странице
                const telegramScripts = document.querySelectorAll('script[src*="telegram"], script[src*="t.me"]');
                telegramScripts.forEach(function(script) {
                    console.log('[TelegramAnalyzer] Обнаружен скрипт Telegram: ' + script.src);
                });
                
                // Проверяем наличие data-атрибутов, связанных с Telegram
                const telegramElements = document.querySelectorAll('[data-telegram], [data-tgauth]');
                telegramElements.forEach(function(element) {
                    console.log('[TelegramAnalyzer] Обнаружен элемент с Telegram атрибутами: ');
                    Array.from(element.attributes).forEach(function(attr) {
                        if (attr.name.includes('data-')) {
                            console.log(' - ' + attr.name + ': ' + attr.value);
                        }
                    });
                });
            }
            
            // Проверяем наличие кнопки Telegram и добавляем функцию инспекции событий
            function inspectTelegramButton() {
                const telegramButtons = document.querySelectorAll('.telegram-btn, button.telegram-btn, [class*="telegram-btn"]');
                
                telegramButtons.forEach(function(button) {
                    // Инспектируем атрибуты кнопки
                    console.log('[TelegramAnalyzer] Кнопка Telegram с атрибутами:');
                    Array.from(button.attributes).forEach(function(attr) {
                        console.log(' - ' + attr.name + ': ' + attr.value);
                    });
                    
                    // Анализируем обработчики событий
                    if (button.onclick) {
                        console.log('[TelegramAnalyzer] Кнопка имеет обработчик onclick');
                    }
                    
                    // Создаем копию событий через клонирование и клик
                    const clone = button.cloneNode(true);
                    clone.style.display = 'none';
                    clone.id = 'telegram-inspector-clone';
                    clone.addEventListener('click', function(e) {
                        e.stopPropagation();
                        console.log('[TelegramAnalyzer] Симуляция клика на кнопке Telegram');
                        return false;
                    });
                    
                    // Проверяем родительские элементы для поиска обработчиков событий
                    let parent = button.parentNode;
                    while (parent && parent !== document.body) {
                        // Проверяем наличие data-атрибутов, связанных с Telegram
                        Array.from(parent.attributes).forEach(function(attr) {
                            if (attr.name.includes('data-') && 
                                (attr.value.includes('telegram') || attr.value.includes('t.me'))) {
                                console.log('[TelegramAnalyzer] Родитель содержит Telegram атрибут: ' + 
                                    attr.name + '=' + attr.value);
                            }
                        });
                        
                        parent = parent.parentNode;
                    }
                });
                
                // Проверяем скрипты, которые могут содержать логику авторизации Telegram
                const allScripts = document.querySelectorAll('script:not([src])');
                allScripts.forEach(function(script) {
                    const content = script.textContent || '';
                    if (content.includes('telegram') || content.includes('t.me')) {
                        console.log('[TelegramAnalyzer] Скрипт с содержимым о Telegram обнаружен');
                        
                        // Ищем ключевые паттерны авторизации
                        if (content.includes('Telegram.Login') || 
                            content.includes('Telegram.WebApp') || 
                            content.includes('TelegramLogin') || 
                            content.includes('tgAuthResult')) {
                            console.log('[TelegramAnalyzer] Скрипт содержит код авторизации Telegram');
                        }
                    }
                });
            }
            
            // Запускаем проверки с задержкой для полной загрузки страницы
            setTimeout(checkTelegramWebApp, 1000);
            setTimeout(inspectTelegramButton, 1500);
            setTimeout(checkTelegramWebApp, 3000);
        })();
    """.trimIndent()
    
    // Внедряем JavaScript для поддержки полноэкранного режима
    webView.evaluateJavascript(videoFullscreenJS, null)
    
    // Внедряем JavaScript для обработки внешних ссылок
    webView.evaluateJavascript(externalLinksHandlerJS, null)
    
    // Внедряем анализатор Telegram авторизации
    webView.evaluateJavascript(telegramAnalysisJS, null)
}

/**
 * Внедряет мощный блокировщик рекламы на основе uBlock Origin и RuAdList
 */
private fun injectRussianAdBlocker(webView: WebView) {
    // Мощный CSS блокировщик на основе RuAdList и EasyList
    val advancedAdBlockerCSS = """
        (function() {
            console.log('[UBlockLite] Внедрение мощного CSS-блокировщика');
            var style = document.createElement('style');
            style.type = 'text/css';
            style.innerHTML = `
                /* Блокировка рекламных контейнеров по классам (RuAdList) */
                div[class*="yandex_rtb"],
                div[class*="ya_partner"],
                div[class*="ya-partner"],
                div[id*="yandex_rtb"],
                div[id*="adfox"],
                div[class*="adfox"],
                iframe[src*="adfox"],
                iframe[src*="ads."],
                iframe[src*="/ads/"],
                iframe[src*="an.yandex.ru"],
                iframe[src*="ad.mail.ru"],
                iframe[src*="r.mradx.net"],
                iframe[src*="ads.vk.com"],
                iframe[id*="ads"],
                div[id*="ads"],
                div[id*="ad-"],
                div[class*="ads-"],
                div[class*="-ads-"],
                div[id*="adv-"],
                div[class*="adv-"],
                div[class*="-adv-"],
                div[class*="banner"],
                div[class*="banners"],
                div[id*="banner"],
                div[id*="banners"],
                
                /* Блокировка букмекерской рекламы */
                iframe[src*="betera"],
                iframe[src*="betcity"],
                iframe[src*="1xbet"],
                iframe[src*="1xstavka"],
                iframe[src*="leon"],
                iframe[src*="winline"],
                iframe[src*="fonbet"],
                iframe[src*="marathon"],
                iframe[src*="parimatch"],
                iframe[src*="betboom"],
                iframe[src*="mostbet"],
                iframe[src*="ligastavok"],
                iframe[src*="bet"],
                iframe[src*="bukmeker"],
                iframe[src*="bookmaker"],
                iframe[src*="betting"],
                
                /* Видеореклама */
                div[class*="video"][class*="ads"],
                div[class*="video"][class*="ad-"],
                div[class*="video-ad"],
                div[class*="video_ad"],
                div[class*="player"][class*="ads"],
                div[class*="player"][class*="ad-"],
                div[class*="player-ad"],
                div[class*="player_ad"],
                
                /* Популярные российские рекламные блоки */
                .b-banner,
                .b-banner__content,
                .b-banner__hor,
                .b-banner__vert,
                .banner__content,
                .banner-content,
                .banner-side,
                .banner-wrapper,
                .banner-placeholder,
                .b-advert,
                .b-adv,
                .b-advert-box,
                .b-advert-banner,
                .b-advblock,
                .advertblock,
                .advert-block,
                .adv-block,
                .top-banner-block,
                .side-banner-block,
                .fixed-banner,
                .floating-banner,
                
                /* Яндекс.Директ */
                .direct,
                .direct-block,
                .direct-banner,
                .yandex-direct,
                .ya-direct,
                
                /* Прочие российские рекламные блоки */
                .smi2-block,
                .smi2__widget,
                .mediakit-widget,
                .relap-block,
                .relap-widget,
                .relap__item,
                .mgid-wrapper,
                .mgid-container,
                .gnezdo,
                .gnezdo-block,
                .gnezdo__widget,
                .trafmag-widget,
                .tizer-block,
                .tizer__wrapper,
                .tizer-container,
                .tgb-container,
                
                /* Блоки букмекерских контор */
                div[class*="betera"],
                div[id*="betera"],
                div[class*="betcity"],
                div[id*="betcity"],
                div[class*="winline"],
                div[id*="winline"],
                div[class*="fonbet"],
                div[id*="fonbet"],
                div[class*="parimatch"],
                div[id*="parimatch"],
                div[class*="betting"],
                div[id*="betting"],
                div[class*="bet-"],
                div[id*="bet-"],
                
                /* Всплывающие и фиксированные баннеры */
                .popup-banner,
                .popup-ads,
                .popup-adv,
                .overlay-banner,
                .overlay-ads,
                .modal-banner,
                .modal-ads,
                .fixed-bottom-banner,
                .fixed-top-banner,
                .sticky-banner,
                
                /* Антиблокировщики */
                .adblock-notification,
                .adblock-warning,
                .adblock-msg,
                .adblock-placeholder,
                .adb-msg
                { display: none !important; }
                
                /* Скрытие рекламных оверлеев на видео */
                .video-player-overlay,
                .video-ad-overlay,
                .video-adv-overlay,
                .player-overlay[class*="ad"],
                .player-overlay[class*="banner"]
                { display: none !important; }
            `;
            document.head.appendChild(style);
        })();
    """.trimIndent()
    
    // Продвинутый JavaScript блокировщик рекламы
    val advancedAdBlockerJS = """
        (function() {
            console.log('[UBlockLite] Запуск мощного JavaScript-блокировщика');
            
            // Конфигурация блокировщика
            const BLOCK_CONFIG = {
                // Частота проверки
                checkIntervalMs: 800,
                
                // Рекламные слова для поиска в URL
                adKeywords: [
                    'ads', 'ad.', '/ad/', 'advert', 'banner', 'adv.', 'adsrv', 'adserv', 
                    'adfox', 'adtech', 'adhigh', 'yandex_rtb', 'direct'
                ],
                
                // Букмекерские ключевые слова
                betKeywords: [
                    'betera', 'betcity', '1xbet', '1xstavka', 'leon', 'winline', 'fonbet', 
                    'marathon', 'parimatch', 'betboom', 'mostbet', 'ligastavok', 'bet', 
                    'bukmeker', 'bookmaker', 'betting', 'casino', 'poker', 'stavka'
                ],
                
                // Классы для блокировки (точные или частичные)
                adClasses: [
                    'ads', 'banner', 'advert', 'ya_partner', 'ya-partner', 'yandex_rtb',
                    'direct', 'smi2', 'relap', 'mgid', 'gnezdo', 'tizer'
                ],
                
                // Идентификаторы для блокировки (точные или частичные)
                adIds: [
                    'ads', 'banner', 'advert', 'ya_partner', 'ya-partner', 'yandex_rtb',
                    'direct', 'smi2', 'relap', 'mgid', 'gnezdo', 'tizer'
                ],
                
                // Селекторы для скрытия элементов
                hideSelectors: [
                    'iframe[src*="ads."]',
                    'iframe[src*="ad."]',
                    'iframe[src*="adfox"]',
                    'iframe[src*="banner"]',
                    'iframe[src*="betera"]',
                    'iframe[src*="betcity"]',
                    'iframe[src*="winline"]',
                    'iframe[src*="fonbet"]',
                    'iframe[src*="marathon"]',
                    'iframe[src*="1xbet"]',
                    'iframe[src*="1xstavka"]',
                    'iframe[src*="parimatch"]',
                    'iframe[src*="bet"]',
                    'div[class*="banner"]',
                    'div[class*="advert"]',
                    'div[class*="ads-"]',
                    'div[class*="-ads"]',
                    'div[class*="adv-"]',
                    'div[class*="-adv"]'
                ],
                
                // Селекторы для специальной проверки содержимого
                contentCheckSelectors: [
                    '.player-container *',
                    '.video-container *',
                    '[class*="player"] *',
                    '[id*="player"] *',
                    '[class*="video"] *',
                    '[id*="video"] *'
                ]
            };
            
            // Счетчик заблокированной рекламы
            let blockCounter = {
                iframes: 0,
                elements: 0,
                playerAds: 0,
                skipButtons: 0
            };
            
            // Функция логирования
            function log(message) {
                if (window.NativeAdBlocker) {
                    window.NativeAdBlocker.logMessage(message);
                } else {
                    console.log('[UBlockLite] ' + message);
                }
            }
            
            // Функция проверки наличия ключевых слов в тексте
            function containsAnyKeyword(text, keywords) {
                const lowerText = text.toLowerCase();
                for (let i = 0; i < keywords.length; i++) {
                    if (lowerText.indexOf(keywords[i].toLowerCase()) !== -1) {
                        return true;
                    }
                }
                return false;
            }
            
            // Функция проверки атрибутов на рекламные ключевые слова
            function checkAttributesForAds(element) {
                const attrs = ['id', 'class', 'name', 'data-id', 'data-name', 'data-widget', 'data-block'];
                
                for (let i = 0; i < attrs.length; i++) {
                    const attr = element.getAttribute(attrs[i]);
                    if (attr && (
                        containsAnyKeyword(attr, BLOCK_CONFIG.adClasses) ||
                        containsAnyKeyword(attr, BLOCK_CONFIG.betKeywords)
                    )) {
                        return true;
                    }
                }
                
                return false;
            }
            
            // Функция блокировки рекламных iframe
            function blockAdIframes() {
                const iframes = document.querySelectorAll('iframe');
                
                iframes.forEach(function(iframe) {
                    const src = iframe.src || '';
                    
                    // Проверка на рекламные URL
                    if (containsAnyKeyword(src, BLOCK_CONFIG.adKeywords) || 
                        containsAnyKeyword(src, BLOCK_CONFIG.betKeywords)) {
                        
                        log('Блокировка рекламного iframe: ' + src);
                        iframe.style.display = 'none';
                        iframe.src = 'about:blank';
                        
                        // Скрыть родительский элемент тоже
                        if (iframe.parentNode) {
                            iframe.parentNode.style.display = 'none';
                        }
                        
                        blockCounter.iframes++;
                    }
                });
            }
            
            // Функция блокировки рекламных элементов по селекторам
            function blockAdElements() {
                // Блокировка по готовым селекторам
                BLOCK_CONFIG.hideSelectors.forEach(function(selector) {
                    const elements = document.querySelectorAll(selector);
                    if (elements.length > 0) {
                        elements.forEach(function(element) {
                            element.style.display = 'none';
                            blockCounter.elements++;
                        });
                    }
                });
                
                // Проверка элементов по атрибутам
                const allElements = document.querySelectorAll('div, section, aside, article');
                allElements.forEach(function(element) {
                    if (checkAttributesForAds(element)) {
                        element.style.display = 'none';
                        blockCounter.elements++;
                    }
                });
            }
            
            // Функция блокировки рекламы в плеерах
            function blockPlayerAds() {
                // Проверяем элементы внутри плееров
                BLOCK_CONFIG.contentCheckSelectors.forEach(function(selector) {
                    const elements = document.querySelectorAll(selector);
                    elements.forEach(function(element) {
                        // Проверка на рекламные классы и идентификаторы
                        if (checkAttributesForAds(element)) {
                            element.style.display = 'none';
                            blockCounter.playerAds++;
                            return;
                        }
                        
                        // Проверка на текстовое содержимое букмекеров
                        const textContent = element.textContent || '';
                        const innerHTML = element.innerHTML || '';
                        const combinedText = textContent + ' ' + innerHTML;
                        
                        if (containsAnyKeyword(combinedText, BLOCK_CONFIG.betKeywords)) {
                            // Только если элемент видимый
                            if (element.offsetParent !== null) {
                                element.style.display = 'none';
                                blockCounter.playerAds++;
                            }
                        }
                    });
                });
                
                // Нажатие на кнопки пропуска рекламы
                const skipSelectors = [
                    '[class*="skip"]', 
                    '[id*="skip"]',
                    '[title*="Пропустить"]',
                    '[aria-label*="Пропустить"]',
                    '.yandex-skip-button',
                    '.skip-add-button',
                    '.skip-ads-button',
                    '.skip-ad-button'
                ];
                
                skipSelectors.forEach(function(selector) {
                    const buttons = document.querySelectorAll(selector);
                    buttons.forEach(function(button) {
                        if (button && button.offsetParent !== null) {
                            try {
                                button.click();
                                blockCounter.skipButtons++;
                            } catch(e) {}
                        }
                    });
                });
            }
            
            // Функция удаления рекламных оверлеев
            function removeAdOverlays() {
                // Селекторы оверлеев
                const overlaySelectors = [
                    '.player-overlay', 
                    '.video-overlay', 
                    '.overlay-container',
                    '[class*="overlay"][class*="ad"]',
                    '[class*="overlay"][class*="banner"]',
                    '[class*="player"][class*="overlay"]'
                ];
                
                overlaySelectors.forEach(function(selector) {
                    const overlays = document.querySelectorAll(selector);
                    overlays.forEach(function(overlay) {
                        // Проверяем содержимое и атрибуты
                        if (checkAttributesForAds(overlay) ||
                            containsAnyKeyword(overlay.innerHTML, BLOCK_CONFIG.adKeywords) ||
                            containsAnyKeyword(overlay.innerHTML, BLOCK_CONFIG.betKeywords)) {
                            overlay.style.display = 'none';
                            blockCounter.playerAds++;
                        }
                    });
                });
            }
            
            // Главная функция блокировки рекламы
            function blockAllAds() {
                const startTime = new Date().getTime();
                
                // Сброс счетчиков на каждой итерации
                const prevBlockCount = {...blockCounter};
                blockCounter = {
                    iframes: 0,
                    elements: 0,
                    playerAds: 0,
                    skipButtons: 0
                };
                
                // Запуск блокировок
                blockAdIframes();
                blockAdElements();
                blockPlayerAds();
                removeAdOverlays();
                
                // Логируем статистику только при изменениях
                const totalBlocked = blockCounter.iframes + blockCounter.elements + 
                                     blockCounter.playerAds + blockCounter.skipButtons;
                                     
                const prevTotalBlocked = prevBlockCount.iframes + prevBlockCount.elements + 
                                        prevBlockCount.playerAds + prevBlockCount.skipButtons;
                
                if (totalBlocked > 0 && totalBlocked !== prevTotalBlocked) {
                    const endTime = new Date().getTime();
                    log('Заблокировано: ' + 
                        blockCounter.iframes + ' iframe, ' + 
                        blockCounter.elements + ' элементов, ' +
                        blockCounter.playerAds + ' рекламы в плеере, ' +
                        blockCounter.skipButtons + ' кнопок пропуска ' +
                        '(за ' + (endTime - startTime) + 'мс)');
                }
            }
            
            // Запускаем блокировщик сразу
            blockAllAds();
            
            // Запускаем периодически
            const interval = setInterval(blockAllAds, BLOCK_CONFIG.checkIntervalMs);
            
            // Наблюдатель за изменениями DOM
            const observer = new MutationObserver(function(mutations) {
                let hasNewNodes = false;
                
                for (let i = 0; i < mutations.length; i++) {
                    if (mutations[i].addedNodes.length > 0) {
                        hasNewNodes = true;
                        break;
                    }
                }
                
                if (hasNewNodes) {
                    blockAllAds();
                }
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            // Экспортируем функцию в глобальную область
            window.uBlockLite = {
                blockAllAds: blockAllAds,
                stats: function() { return blockCounter; }
            };
            
            log('UBlockLite активирован');
        })();
    """.trimIndent()
    
    // Модуль для внедрения специфического блокировщика рекламы букмекеров
    val bookmakerSpecificJS = """
        (function() {
            console.log('[BookmakerBlocker] Запуск специализированного блокировщика букмекерской рекламы');
            
            // Букмекерские домены (расширенный список)
            const BOOKMAKER_DOMAINS = [
                'betera.ru', 'betera.com', 'betcity.ru', '1xbet', '1xstavka', 'leon.ru',
                'winline.ru', 'fonbet.ru', 'marathon', 'parimatch', 'betboom', 'bwin.ru',
                'mostbet', 'ligastavok', 'tennisi.bet', 'pin-up.ru', 'melbet', 'ggbet',
                'zenitbet', 'sbobet', 'williamhill', 'betway', 'unibet', 'cloudvideocontent.ru'
            ];
            
            // Функция блокировки содержимого букмекеров в плеерах и видео
            function blockBookmakerInPlayers() {
                // Находим все плееры
                const playerContainers = document.querySelectorAll(
                    '.player-container, .video-container, [class*="player"], [id*="player"], [class*="video"], [id*="video"]'
                );
                
                playerContainers.forEach(function(container) {
                    // Ищем внутри контейнеров элементы с рекламой букмекеров
                    const elements = container.querySelectorAll('*');
                    elements.forEach(function(element) {
                        // Пропускаем основные элементы плеера
                        if (element.tagName === 'VIDEO' || 
                            element.tagName === 'BUTTON' && element.className.includes('control') ||
                            element.tagName === 'DIV' && element.className.includes('control')) {
                            return;
                        }
                        
                        // Проверяем класс и ID на наличие букмекерских слов
                        const classNames = element.className || '';
                        const id = element.id || '';
                        
                        for (let i = 0; i < BOOKMAKER_DOMAINS.length; i++) {
                            const domain = BOOKMAKER_DOMAINS[i];
                            if (classNames.includes(domain) || id.includes(domain)) {
                                element.style.display = 'none';
                                if (window.NativeAdBlocker) {
                                    window.NativeAdBlocker.logMessage('Блокировка элемента букмекера: ' + domain);
                                }
                                break;
                            }
                        }
                        
                        // Проверяем содержимое на букмекерские слова
                        const innerText = element.innerText || '';
                        const innerHTML = element.innerHTML || '';
                        
                        if (innerText && innerText.length < 100) { // Проверяем только короткие тексты
                            for (let i = 0; i < BOOKMAKER_DOMAINS.length; i++) {
                                const domain = BOOKMAKER_DOMAINS[i].replace('.ru', '').replace('.com', '');
                                if (innerText.toLowerCase().includes(domain)) {
                                    element.style.display = 'none';
                                    if (window.NativeAdBlocker) {
                                        window.NativeAdBlocker.logMessage('Блокировка текста букмекера: ' + domain);
                                    }
                                    break;
                                }
                            }
                        }
                        
                        // Ищем ссылки на букмекерские сайты
                        const links = element.querySelectorAll('a');
                        links.forEach(function(link) {
                            const href = link.href || '';
                            
                            for (let i = 0; i < BOOKMAKER_DOMAINS.length; i++) {
                                if (href.includes(BOOKMAKER_DOMAINS[i])) {
                                    link.style.display = 'none';
                                    // Скрываем родителя ссылки тоже
                                    if (link.parentNode) {
                                        link.parentNode.style.display = 'none';
                                    }
                                    if (window.NativeAdBlocker) {
                                        window.NativeAdBlocker.logMessage('Блокировка ссылки букмекера: ' + href);
                                    }
                                    break;
                                }
                            }
                        });
                    });
                });
            }
            
            // Запускаем блокировщик букмекеров сразу
            blockBookmakerInPlayers();
            
            // Запускаем периодически
            setInterval(blockBookmakerInPlayers, 1000);
        })();
    """.trimIndent()
    
    // Внедряем CSS
    webView.evaluateJavascript(advancedAdBlockerCSS, null)
    
    // Внедряем основной JavaScript блокировщик
    webView.evaluateJavascript(advancedAdBlockerJS, null)
    
    // Внедряем дополнительный блокировщик букмекеров
    webView.evaluateJavascript(bookmakerSpecificJS, null)
}

/**
 * Добавляет отступ для контента страницы и черную полосу сверху
 */
private fun injectHeaderSpacing(webView: WebView) {
    val headerSpacingJs = """
        (function() {
            // Создаем стиль для отступа контента
            const style = document.createElement('style');
            style.textContent = `
                /* Черная полоса сверху */
                html:before {
                    content: '';
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    height: 24px;
                    background-color: #000000;
                    z-index: 10000;
                    pointer-events: none;
                }
                
                /* Основной контейнер: сместить вниз */
                .container, 
                .main-container, 
                main, 
                #app, 
                .app, 
                header,
                nav,
                .header,
                .navbar,
                .navbar-container,
                .main-header,
                .top-menu,
                .navigation {
                    margin-top: 24px !important;
                }
                
                /* Для фиксированных элементов */
                header.fixed, 
                .header.fixed, 
                .navbar.fixed, 
                nav.fixed,
                .navigation.fixed,
                [style*="position: fixed"],
                [style*="position:fixed"],
                .fixed-top,
                .sticky-top {
                    top: 24px !important;
                }
            `;
            
            // Добавляем стиль в head
            document.head.appendChild(style);
            
            console.log('[HeaderSpacing] Добавлена черная полоса сверху и отступ для контента');
        })();
    """.trimIndent()
    
    webView.evaluateJavascript(headerSpacingJs, null)
}



/**
 * Удаляет отступ для контента страницы
 */
private fun removeHeaderSpacing(webView: WebView) {
    val removeSpacingJs = """
        (function() {
            // Удаляем существующие стили для отступа
            const existingStyles = document.querySelectorAll('style');
            for (let i = 0; i < existingStyles.length; i++) {
                const style = existingStyles[i];
                if (style.textContent.includes('html:before') || 
                    style.textContent.includes('margin-top: 24px')) {
                    style.parentNode.removeChild(style);
                }
            }
            
            // Создаем новый стиль для сброса отступов
            const style = document.createElement('style');
            style.textContent = `
                /* Удаляем черную полосу сверху */
                html:before {
                    display: none !important;
                }
                
                /* Возвращаем контейнеры в исходное положение */
                .container, 
                .main-container, 
                main, 
                #app, 
                .app, 
                header,
                nav,
                .header,
                .navbar,
                .navbar-container,
                .main-header,
                .top-menu,
                .navigation {
                    margin-top: 0 !important;
                }
                
                /* Для фиксированных элементов */
                header.fixed, 
                .header.fixed, 
                .navbar.fixed, 
                nav.fixed,
                .navigation.fixed,
                [style*="position: fixed"],
                [style*="position:fixed"],
                .fixed-top,
                .sticky-top {
                    top: 0 !important;
                }
            `;
            
            // Добавляем стиль в head
            document.head.appendChild(style);
            
            console.log('[HeaderSpacing] Удалены отступы сверху');
        })();
    """.trimIndent()
    
    webView.evaluateJavascript(removeSpacingJs, null)
}

/**
 * Исправляет работу кнопки удаления истории на главной странице
 */
private fun injectDeleteHistoryButtonFix(webView: WebView) {
    val deleteHistoryButtonFixJs = """
        (function() {
            console.log('[HistoryFix] Запуск исправления для кнопки удаления истории');
            
                         // Функция для добавления обработчиков на кнопку удаления истории
             function fixDeleteHistoryButton() {
                 // Находим все кнопки удаления (по селектору из примера)
                 const deleteButtons = document.querySelectorAll('.deleteButton, button[data-v-cd7aaf9f].deleteButton');
                
                if (deleteButtons.length > 0) {
                    console.log('[HistoryFix] Найдено кнопок удаления: ' + deleteButtons.length);
                    
                    deleteButtons.forEach(function(button, index) {
                        // Проверяем, не добавляли ли мы уже обработчик
                        if (!button.getAttribute('data-fixed')) {
                            console.log('[HistoryFix] Исправление кнопки #' + index);
                            
                                                         // Не добавляем новый обработчик, а улучшаем существующий
                             // Делаем кнопку полностью интерактивной
                             button.style.pointerEvents = 'auto';
                             
                             // Находим все элементы внутри кнопки и делаем их кликабельными
                             const allElements = button.querySelectorAll('*');
                             allElements.forEach(function(el) {
                                 el.style.pointerEvents = 'auto';
                             });
                             
                             // Добавляем обработчик только для отладки и визуального эффекта
                             button.addEventListener('click', function(event) {
                                 console.log('[HistoryFix] Клик по кнопке удаления истории');
                                 
                                 // НЕ останавливаем стандартное поведение, чтобы работал нативный обработчик
                                 // event.preventDefault();
                                 // event.stopPropagation();
                                 
                                 // Добавляем визуальный эффект при клике
                                 button.style.transform = 'scale(1.2)';
                                 setTimeout(function() {
                                     button.style.transform = 'scale(1)';
                                 }, 150);
                                 
                                 // Не возвращаем false, чтобы событие продолжило распространение
                             });
                            
                                                         // Помечаем кнопку как исправленную
                             button.setAttribute('data-fixed', 'true');
                             
                             // Делаем кнопку более заметной
                             button.style.cursor = 'pointer';
                             button.style.pointerEvents = 'auto';
                             button.style.transition = 'transform 0.15s ease';
                             button.style.outline = 'none';
                             
                             // Находим SVG внутри кнопки и делаем его кликабельным
                             const svgElement = button.querySelector('svg');
                             if (svgElement) {
                                 svgElement.style.pointerEvents = 'auto';
                             }
                        }
                    });
                }
            }
            
            // Выполняем функцию сразу после загрузки
            fixDeleteHistoryButton();
            
            // Запускаем её через 1 секунду для подгруженного динамически контента
            setTimeout(fixDeleteHistoryButton, 1000);
            
            // Настраиваем MutationObserver для отслеживания новых кнопок
            const observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    if (mutation.addedNodes.length > 0) {
                        fixDeleteHistoryButton();
                    }
                });
            });
            
            // Наблюдаем за изменениями в DOM
            observer.observe(document.body, { 
                childList: true, 
                subtree: true 
            });
        })();
    """.trimIndent()
    
    webView.evaluateJavascript(deleteHistoryButtonFixJs, null)
} 

/**
 * Внедряет JavaScript для поддержки pull to refresh
 */
private fun injectPullToRefresh(webView: WebView) {
    val pullToRefreshJs = """
        (function() {
            console.log('[PullToRefresh] Добавление поддержки pull to refresh');
            
            let isRefreshing = false;
            let startY = 0;
            let pullDistance = 0;
            let refreshThreshold = 50;
            
            // Создаем элемент индикатора обновления
            let refreshIndicator = document.createElement('div');
            refreshIndicator.id = 'pull-to-refresh-indicator';
            refreshIndicator.style.cssText = `
                position: fixed;
                top: -60px;
                left: 50%;
                transform: translateX(-50%);
                width: 50px;
                height: 50px;
                background: rgba(0, 0, 0, 0.9);
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 999999;
                transition: top 0.2s ease;
                border: 2px solid rgba(255, 255, 255, 0.5);
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
            `;
            
            // Добавляем иконку обновления
            refreshIndicator.innerHTML = `
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
                    <path d="M23 4v6h-6M1 20v-6h6M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"/>
                </svg>
            `;
            
            document.body.appendChild(refreshIndicator);
            
            // Обработчик начала касания
            function handleTouchStart(e) {
                if (isRefreshing) return;
                
                const touch = e.touches[0];
                startY = touch.clientY;
                
                // Проверяем, что мы в самом верху страницы
                if (window.scrollY > 2) return;
                
                document.addEventListener('touchmove', handleTouchMove, { passive: false });
                document.addEventListener('touchend', handleTouchEnd, { passive: false });
            }
            
            // Обработчик движения пальца
            function handleTouchMove(e) {
                if (isRefreshing) return;
                
                const touch = e.touches[0];
                pullDistance = Math.max(0, touch.clientY - startY);
                
                // Если тянем вниз и мы в верху страницы
                if (pullDistance > 0 && window.scrollY <= 2) {
                    e.preventDefault();
                    
                    const indicatorTop = Math.min(pullDistance / 2, refreshThreshold);
                    refreshIndicator.style.top = (indicatorTop - 60) + 'px';
                    
                    // Поворачиваем иконку
                    const rotation = Math.min(pullDistance / refreshThreshold * 360, 360);
                    refreshIndicator.querySelector('svg').style.transform = 'rotate(' + rotation + 'deg)';
                }
            }
            
            // Обработчик окончания касания
            function handleTouchEnd(e) {
                if (isRefreshing) return;
                
                document.removeEventListener('touchmove', handleTouchMove);
                document.removeEventListener('touchend', handleTouchEnd);
                
                if (pullDistance >= refreshThreshold) {
                    // Запускаем обновление
                    isRefreshing = true;
                    refreshIndicator.style.top = '10px';
                    refreshIndicator.querySelector('svg').style.animation = 'spin 1s linear infinite';
                    
                    // Добавляем CSS анимацию
                    if (!document.querySelector('#pull-to-refresh-styles')) {
                        const style = document.createElement('style');
                        style.id = 'pull-to-refresh-styles';
                        style.textContent = `
                            @keyframes spin {
                                from { transform: rotate(0deg); }
                                to { transform: rotate(360deg); }
                            }
                        `;
                        document.head.appendChild(style);
                    }
                    
                    // Обновляем страницу
                    setTimeout(() => {
                        window.location.reload();
                    }, 100);
                } else {
                    // Возвращаем индикатор в исходное положение
                    refreshIndicator.style.top = '-60px';
                    refreshIndicator.querySelector('svg').style.transform = 'rotate(0deg)';
                }
                
                pullDistance = 0;
            }
            
            // Добавляем обработчик события
            document.addEventListener('touchstart', handleTouchStart, { passive: true });
            
            console.log('[PullToRefresh] Pull to refresh активирован');
        })();
    """.trimIndent()
    
    webView.evaluateJavascript(pullToRefreshJs, null)
}

/**
 * Удаляет поддержку pull to refresh
 */
private fun removePullToRefresh(webView: WebView) {
    val removePullToRefreshJs = """
        (function() {
            console.log('[PullToRefresh] Удаление поддержки pull to refresh');
            
            // Удаляем индикатор
            const indicator = document.getElementById('pull-to-refresh-indicator');
            if (indicator) {
                indicator.remove();
            }
            
            // Удаляем стили
            const styles = document.getElementById('pull-to-refresh-styles');
            if (styles) {
                styles.remove();
            }
            
            // Удаляем обработчики событий (они будут пересозданы при следующем включении)
            console.log('[PullToRefresh] Pull to refresh деактивирован');
        })();
    """.trimIndent()
    
    webView.evaluateJavascript(removePullToRefreshJs, null)
}

/**
 * Внедряет JavaScript для поддержки режима Picture-in-Picture
 * Включая специальную поддержку для плеера Lumex
 * 
 * Как работает поддержка Lumex:
 * 1. Распознаем плеер по селекторам #vjs_video_3 или .video-js
 * 2. Находим кнопку PiP по специфическим селекторам для Lumex плеера
 * 3. Ищем видеоэлемент с id #vjs_video_3_html5_api
 * 4. При переходе в PiP режим:
 *    - Активно ищем видео элементы по всей странице, включая iframe
 *    - Подготавливаем видео к PiP (устанавливаем громкость, запускаем воспроизведение)
 *    - Добавляем отслеживание состояния видео с передачей в MediaSession
 * 5. Регистрируем JavaScriptInterface для обеспечения коммуникации JS-Android
 */
private fun injectPipSupport(webView: WebView) {
    val pipSupportJS = """
        (function() {
            console.log('[PipHelper] Добавление улучшенной поддержки Picture-in-Picture режима');
            
            // Проверяем, что мы не уже добавили поддержку PiP
            if (window.ReYoHoHoPipHelper) {
                console.log('[PipHelper] Поддержка Picture-in-Picture уже добавлена');
                return;
            }
            
            // Проверяем, есть ли встроенная поддержка PiP в браузере
            const hasBrowserPipSupport = document.pictureInPictureEnabled || 
                                        !!document.querySelector('video')?.webkitSupportsPresentationMode;
                                        
            console.log('[PipHelper] Встроенная поддержка PiP обнаружена:', hasBrowserPipSupport);
            
            // Функция для эмуляции клика по кнопке PiP в различных плеерах
            function findAndClickPipButton() {
                console.log('[PipHelper] Поиск кнопки PiP в плеерах...');
                
                // Селекторы кнопок PiP, используемых в различных плеерах
                const pipButtonSelectors = [
                    // Специфические селекторы для Lumex плеера
                    'button[class^="vjs-pip"]', 
                    'button.vjs-pip-button',
                    'button.vjs-picture-in-picture-control',
                    'button[title*="картинка"]', 
                    'button[aria-label*="Картинка"]',
                    'button:has(svg[viewBox*="24"][data-id="pip"])',  
                    
                    // Общие селекторы PiP кнопок
                    '[aria-label*="картинка в картинке"]',
                    '[aria-label*="picture in picture"]', 
                    '[aria-label*="pip"]',
                    '[title*="картинка в картинке"]',
                    '[title*="picture in picture"]',
                    '[title*="pip"]',
                    '[data-tooltip-content*="картинка в картинке"]',
                    '[data-tooltip-content*="picture in picture"]',
                    '[data-tooltip*="картинка в картинке"]',
                    '[data-tooltip*="picture in picture"]',
                    
                    // Кнопки по классам и ID
                    '.vjs-pip-button',
                    '.ytp-pip-button',
                    '.pip-button',
                    '.picture-in-picture',
                    '#pip-button',
                    '[class*="pip"]',
                    '[id*="pip"]',
                    
                    // Кнопки по содержимому иконок (SVG)
                    'button:has(svg[viewBox*="24"][class*="pip"])',
                    'button:has(path[d*="M21,3H3"])',
                    'button:has(path[d*="M19 7h-8v6h8V7zm-2 4h-4V9h4v2z"])'
                ];
                
                // Перебираем все возможные селекторы
                for (const selector of pipButtonSelectors) {
                    try {
                        const pipButton = document.querySelector(selector);
                        if (pipButton) {
                            console.log('[PipHelper] Найдена кнопка PiP по селектору:', selector);
                            pipButton.click();
                            return true;
                        }
                    } catch (e) {
                        console.log('[PipHelper] Ошибка при проверке селектора:', e);
                    }
                }
                
                return false;
            }
            
            // Функция для использования встроенного API браузера для PiP
            async function useNativePipAPI(video) {
                if (!video) return false;
                
                try {
                    // Проверяем поддержку стандартного PiP API
                    if (document.pictureInPictureElement) {
                        // Если видео уже в PiP режиме, выходим из него
                        await document.exitPictureInPicture();
                        return true;
                    } else if (document.pictureInPictureEnabled && video.readyState > 0) {
                        // Если поддерживается PiP и видео готово, входим в режим PiP
                        await video.requestPictureInPicture();
                        return true;
                    }
                    
                    // Проверяем поддержку Safari/WebKit PiP
                    if (video.webkitSupportsPresentationMode && 
                        typeof video.webkitSetPresentationMode === 'function') {
                        // Переключаем режим отображения между 'inline' и 'picture-in-picture'
                        const currentMode = video.webkitPresentationMode;
                        video.webkitSetPresentationMode(currentMode === 'picture-in-picture' ? 'inline' : 'picture-in-picture');
                        return true;
                    }
                } catch (e) {
                    console.log('[PipHelper] Ошибка при использовании нативного PiP API:', e);
                }
                
                return false;
            }
            
            // Основная функция для активации PiP
            async function activatePipMode(videoWidth, videoHeight) {
                console.log('[PipHelper] Активация режима PiP...');
                let success = false;
                
                // Специальная обработка для плеера Lumex
                if (document.querySelector('#vjs_video_3') || document.querySelector('.video-js')) {
                    console.log('[PipHelper] Обнаружен плеер Lumex');
                    
                    // Находим кнопку PiP в Lumex плеере
                    const lumexPipButton = document.querySelector('button.vjs-picture-in-picture-control') || 
                                          document.querySelector('button.vjs-pip-button') ||
                                          document.querySelector('button[aria-label="Картинка в картинке"]');
                    
                    if (lumexPipButton) {
                        console.log('[PipHelper] Нажатие на кнопку PiP в Lumex плеере');
                        lumexPipButton.click();
                        return true;
                    } else {
                        // Если кнопка не найдена, пробуем активировать PiP через API для видео в Lumex плеере
                        const lumexVideo = document.querySelector('#vjs_video_3_html5_api') || 
                                          document.querySelector('.video-js video');
                        
                        if (lumexVideo) {
                            console.log('[PipHelper] Используем API для видео в Lumex плеере');
                            if (await useNativePipAPI(lumexVideo)) {
                                return true;
                            }
                        }
                    }
                }
                
                // 1. Сначала пробуем найти и нажать имеющуюся кнопку PiP
                success = findAndClickPipButton();
                if (success) {
                    console.log('[PipHelper] PiP активирован через кнопку плеера');
                    return true;
                }
                
                // 2. Затем пробуем использовать нативное API браузера
                const video = document.querySelector('video');
                if (video) {
                    success = await useNativePipAPI(video);
                    if (success) {
                        console.log('[PipHelper] PiP активирован через нативное API браузера');
                        return true;
                    }
                    
                    // Получаем соотношение сторон видео
                    if (!videoWidth || !videoHeight) {
                        if (video.videoWidth && video.videoHeight) {
                            videoWidth = video.videoWidth;
                            videoHeight = video.videoHeight;
                        }
                    }
                }
                
                // 3. В крайнем случае, используем нативный API приложения
                try {
                    console.log('[PipHelper] Вызов нативного метода Android для PiP');
                    Android.enterPictureInPictureMode(String(videoWidth || 16), String(videoHeight || 9));
                    return true;
                } catch (e) {
                    console.log('[PipHelper] Ошибка при использовании Android PiP API:', e);
                }
                
                return false;
            }
            
            // Функция для добавления кнопки PiP к видео
            function addPipButtonToVideos() {
                const videos = document.querySelectorAll('video');
                videos.forEach((video, index) => {
                    // Проверяем, что мы еще не добавили кнопку к этому видео
                    if (video.getAttribute('data-pip-button-added') !== 'true') {
                        video.setAttribute('data-pip-button-added', 'true');
                        
                        // Создаем контейнер для кнопки PiP
                        const pipButtonContainer = document.createElement('div');
                        pipButtonContainer.style.cssText = `
                            position: absolute;
                            bottom: 10px;
                            right: 10px;
                            z-index: 9999;
                            opacity: 0;
                            transition: opacity 0.3s;
                            background: rgba(0, 0, 0, 0.5);
                            border-radius: 50%;
                            width: 36px;
                            height: 36px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            cursor: pointer;
                        `;
                        
                        // Показываем кнопку при наведении
                        video.addEventListener('mouseover', () => {
                            pipButtonContainer.style.opacity = '1';
                        });
                        
                        video.addEventListener('mouseout', () => {
                            pipButtonContainer.style.opacity = '0';
                        });
                        
                        // Создаем SVG иконку для кнопки PiP
                        pipButtonContainer.innerHTML = `
                            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="white">
                                <path d="M19 7h-8v6h8V7zm-2 4h-4V9h4v2zm4-8H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16.01H3V4.99h18v14.02z"/>
                            </svg>
                        `;
                        
                        // Добавляем обработчик нажатия на кнопку
                        pipButtonContainer.addEventListener('click', async (e) => {
                            e.stopPropagation();
                            console.log('[PipHelper] Нажата кнопка PiP');
                            
                            // Получаем пропорции видео
                            const videoWidth = video.videoWidth || 16;
                            const videoHeight = video.videoHeight || 9;
                            
                            await activatePipMode(videoWidth, videoHeight);
                        });
                        
                        // Добавляем контейнер рядом с видео
                        if (video.parentElement) {
                            video.parentElement.style.position = 'relative';
                            video.parentElement.appendChild(pipButtonContainer);
                        }
                    }
                });
            }
            
            // Поиск видео в iframe и проверка их для добавления кнопки PiP
            function findIframedVideos() {
                const iframes = document.querySelectorAll('iframe');
                iframes.forEach(iframe => {
                    try {
                        // Пытаемся найти видео в iframe
                        const iframeDocument = iframe.contentDocument || iframe.contentWindow?.document;
                        if (iframeDocument) {
                            const videos = iframeDocument.querySelectorAll('video');
                            if (videos.length > 0) {
                                console.log('[PipHelper] Найдено видео в iframe:', iframe.src);
                            }
                        }
                    } catch (e) {
                        // Ошибка при доступе к iframe из-за политики CORS
                        console.log('[PipHelper] Невозможно получить доступ к iframe:', iframe.src);
                    }
                });
            }
            
            // Добавляем слушатель для двойного нажатия на видео для активации PiP
            function addDoubleTapToPipListener() {
                const videos = document.querySelectorAll('video');
                videos.forEach(video => {
                    if (!video.getAttribute('data-pip-double-tap')) {
                        video.setAttribute('data-pip-double-tap', 'true');
                        
                        let lastTap = 0;
                        video.addEventListener('touchend', function(e) {
                            const currentTime = new Date().getTime();
                            const tapLength = currentTime - lastTap;
                            if (tapLength < 500 && tapLength > 0) {
                                e.preventDefault();
                                activatePipMode(video.videoWidth, video.videoHeight);
                            }
                            lastTap = currentTime;
                        });
                    }
                });
            }
            
            // Инициализация обработки событий
            function initPipHelper() {
                // Запускаем добавление кнопок PiP
                addPipButtonToVideos();
                
                // Запускаем поиск видео в iframe
                findIframedVideos();
                
                // Добавляем слушатель для двойного нажатия
                addDoubleTapToPipListener();
                
                // Добавляем двойную клавишу "p" для активации PiP
                let lastPKeyTime = 0;
                document.addEventListener('keydown', function(e) {
                    if (e.key === 'p' || e.key === 'P' || e.keyCode === 80) {
                        const currentTime = new Date().getTime();
                        const keyDelay = currentTime - lastPKeyTime;
                        if (keyDelay < 500 && keyDelay > 0) {
                            console.log('[PipHelper] Обнаружено двойное нажатие P - активируем PiP');
                            
                            // Находим первое воспроизводящееся видео или просто первое видео
                            const videos = Array.from(document.querySelectorAll('video'));
                            const playingVideo = videos.find(v => !v.paused) || videos[0];
                            
                            if (playingVideo) {
                                activatePipMode(playingVideo.videoWidth, playingVideo.videoHeight);
                            } else {
                                // Если видео не найдено, активируем PiP с пропорциями 16:9
                                activatePipMode();
                            }
                        }
                        lastPKeyTime = currentTime;
                    }
                });
            }
            
            // Регулярное обновление для обработки асинхронно загружаемых видео
            function scheduleUpdateChecks() {
                // Начальная инициализация
                initPipHelper();
                
                // Регулярные проверки
                setTimeout(initPipHelper, 1000);
                setTimeout(initPipHelper, 3000);
                setTimeout(initPipHelper, 6000);
                
                // Наблюдатель за изменениями DOM для отслеживания новых видео
                const observer = new MutationObserver((mutations) => {
                    let videoAdded = false;
                    mutations.forEach((mutation) => {
                        if (mutation.addedNodes.length > 0) {
                            mutation.addedNodes.forEach((node) => {
                                // Проверяем, является ли добавленный узел видео или содержит видео
                                if (node.nodeName === 'VIDEO' || 
                                    (node.nodeType === Node.ELEMENT_NODE && node.querySelector('video'))) {
                                    videoAdded = true;
                                }
                            });
                        }
                    });
                    
                    // Если добавлено новое видео, инициализируем PiP-хелпер
                    if (videoAdded) {
                        console.log('[PipHelper] Обнаружено новое видео - обновляем');
                        initPipHelper();
                    }
                });
                
                // Запускаем наблюдатель
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
            }
            
            // Колбэки для обработки входа/выхода из режима PiP
            function onEnterPipMode() {
                console.log('[PipHelper] Приложение вошло в режим PiP');
                // Можно добавить специфичную логику при переходе в PiP
            }
            
            function onExitPipMode() {
                console.log('[PipHelper] Приложение вышло из режима PiP');
                // Можно добавить специфичную логику при выходе из PiP
            }
            
            // Экспортируем функции в глобальную область
            window.ReYoHoHoPipHelper = {
                activatePipMode: activatePipMode,
                onEnterPipMode: onEnterPipMode,
                onExitPipMode: onExitPipMode,
                addPipButtonToVideos: addPipButtonToVideos,
                findAndClickPipButton: findAndClickPipButton
            };
            
            // Запускаем планировщик проверок
            scheduleUpdateChecks();
            
            console.log('[PipHelper] Поддержка Picture-in-Picture успешно добавлена');
        })();
    """.trimIndent()
    
    // Внедряем JavaScript в WebView
    try {
        webView.evaluateJavascript(pipSupportJS, null)
        Log.d("PipHelper", "JavaScript для поддержки Picture-in-Picture успешно внедрен")
    } catch (e: Exception) {
        Log.e("PipHelper", "Ошибка при внедрении JavaScript для поддержки Picture-in-Picture: ${e.message}")
    }
}