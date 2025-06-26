package com.example.reyohoho.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Менеджер настроек приложения
 */
class SettingsManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "reyohoho_settings"
        private const val KEY_REMOVE_TOP_SPACING = "remove_top_spacing"
        private const val KEY_FULLSCREEN_MODE = "fullscreen_mode"
        private const val KEY_DEVICE_TYPE = "device_type"
        private const val KEY_SITE_MIRROR = "site_mirror"
        private const val KEY_PULL_TO_REFRESH = "pull_to_refresh"
        private const val KEY_DISABLE_ZOOM = "disable_zoom"
        private const val KEY_NOTIFY_ON_UPDATE = "notify_on_update"
        private const val KEY_DOWNLOADED_UPDATE_VERSION = "downloaded_update_version"
        private const val KEY_DOWNLOADED_UPDATE_ID = "downloaded_update_id"
        private const val KEY_IGNORED_UPDATE_VERSION = "ignored_update_version"
        private const val KEY_SHOW_DOWNLOAD_SPEED = "show_download_speed"
        private const val KEY_SHOW_REMAINING_TIME = "show_remaining_time"
        private const val KEY_PROGRESS_DISPLAY_MODE = "progress_display_mode"
        private const val KEY_DOWNLOAD_NOTIFICATIONS = "download_notifications"
        private const val KEY_AUTO_OPEN_DOWNLOADS = "auto_open_downloads"
        private const val KEY_SHOW_DOWNLOAD_CONFIRMATION = "show_download_confirmation"
        const val DEVICE_TYPE_ANDROID = "android"
        const val DEVICE_TYPE_ANDROID_TV = "android_tv"

        // Синглтон для доступа к настройкам из любой части приложения
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // StateFlow для отслеживания изменений настройки отступа
    private val _removeTopSpacingFlow = MutableStateFlow(isTopSpacingRemoved())
    val removeTopSpacingFlow: StateFlow<Boolean> = _removeTopSpacingFlow.asStateFlow()
    
    // StateFlow для отслеживания изменений настройки полноэкранного режима
    private val _fullscreenModeFlow = MutableStateFlow(isFullscreenModeEnabled())
    val fullscreenModeFlow: StateFlow<Boolean> = _fullscreenModeFlow.asStateFlow()
    
    // StateFlow для отслеживания типа устройства
    private val _deviceTypeFlow = MutableStateFlow(getDeviceType())
    val deviceTypeFlow: StateFlow<String> = _deviceTypeFlow.asStateFlow()
    
    // StateFlow для отслеживания выбранного зеркала сайта
    private val _siteMirrorFlow = MutableStateFlow(getSiteMirror())
    val siteMirrorFlow: StateFlow<String> = _siteMirrorFlow.asStateFlow()
    
    // StateFlow для отслеживания настройки pull to refresh
    private val _pullToRefreshFlow = MutableStateFlow(isPullToRefreshEnabled())
    val pullToRefreshFlow: StateFlow<Boolean> = _pullToRefreshFlow.asStateFlow()
    
    // StateFlow для отслеживания настройки отключения зума
    private val _disableZoomFlow = MutableStateFlow(isZoomDisabled())
    val disableZoomFlow: StateFlow<Boolean> = _disableZoomFlow.asStateFlow()
    
    val showDownloadSpeedFlow = MutableStateFlow(isShowDownloadSpeedEnabled())
    val showRemainingTimeFlow = MutableStateFlow(isShowRemainingTimeEnabled())
    val progressDisplayModeFlow = MutableStateFlow(getProgressDisplayMode())
    val showDownloadConfirmationFlow = MutableStateFlow(isShowDownloadConfirmationEnabled())
    
    /**
     * Проверяет, установлен ли тип устройства
     */
    fun isDeviceTypeSet(): Boolean {
        return prefs.contains(KEY_DEVICE_TYPE)
    }
    
    /**
     * Возвращает тип устройства (Android или Android TV)
     */
    fun getDeviceType(): String {
        return prefs.getString(KEY_DEVICE_TYPE, DEVICE_TYPE_ANDROID) ?: DEVICE_TYPE_ANDROID
    }
    
    /**
     * Устанавливает тип устройства
     */
    fun setDeviceType(deviceType: String) {
        prefs.edit().putString(KEY_DEVICE_TYPE, deviceType).apply()
        _deviceTypeFlow.value = deviceType
    }
    
    /**
     * Проверяет, является ли устройство Android TV
     */
    fun isAndroidTV(): Boolean {
        return getDeviceType() == DEVICE_TYPE_ANDROID_TV
    }
    
    /**
     * Возвращает статус настройки удаления отступа сверху
     */
    fun isTopSpacingRemoved(): Boolean {
        return prefs.getBoolean(KEY_REMOVE_TOP_SPACING, false)
    }
    
    /**
     * Устанавливает статус настройки удаления отступа сверху
     */
    fun setRemoveTopSpacing(remove: Boolean) {
        prefs.edit().putBoolean(KEY_REMOVE_TOP_SPACING, remove).apply()
        _removeTopSpacingFlow.value = remove
    }
    
    /**
     * Переключает настройку отступа сверху
     */
    fun toggleTopSpacing(): Boolean {
        val newValue = !isTopSpacingRemoved()
        setRemoveTopSpacing(newValue)
        return newValue
    }

    /**
     * Возвращает статус настройки полноэкранного режима
     */
    fun isFullscreenModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_FULLSCREEN_MODE, false)
    }
    
    /**
     * Устанавливает статус настройки полноэкранного режима
     */
    fun setFullscreenMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FULLSCREEN_MODE, enabled).apply()
        _fullscreenModeFlow.value = enabled
    }
    
    /**
     * Переключает настройку полноэкранного режима
     */
    fun toggleFullscreenMode(): Boolean {
        val newValue = !isFullscreenModeEnabled()
        setFullscreenMode(newValue)
        return newValue
    }
    
    /**
     * Возвращает выбранное зеркало сайта
     */
    fun getSiteMirror(): String {
        return prefs.getString(KEY_SITE_MIRROR, "https://reyohoho.github.io/reyohoho") ?: "https://reyohoho.github.io/reyohoho"
    }
    
    /**
     * Устанавливает зеркало сайта
     */
    fun setSiteMirror(mirror: String) {
        val oldMirror = getSiteMirror()
        prefs.edit().putString(KEY_SITE_MIRROR, mirror).apply()
        _siteMirrorFlow.value = mirror
        
        // Логируем смену зеркала
        android.util.Log.d("SettingsManager", "Зеркало изменено с '$oldMirror' на '$mirror'")
    }
    
    /**
     * Возвращает статус настройки pull to refresh
     */
    fun isPullToRefreshEnabled(): Boolean {
        return prefs.getBoolean(KEY_PULL_TO_REFRESH, true)
    }
    
    /**
     * Устанавливает статус настройки pull to refresh
     */
    fun setPullToRefresh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PULL_TO_REFRESH, enabled).apply()
        _pullToRefreshFlow.value = enabled
    }
    
    /**
     * Переключает настройку pull to refresh
     */
    fun togglePullToRefresh(): Boolean {
        val newValue = !isPullToRefreshEnabled()
        setPullToRefresh(newValue)
        return newValue
    }
    
    /**
     * Возвращает статус настройки отключения зума
     */
    fun isZoomDisabled(): Boolean {
        return prefs.getBoolean(KEY_DISABLE_ZOOM, true)
    }
    
    /**
     * Устанавливает статус настройки отключения зума
     */
    fun setDisableZoom(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_ZOOM, disabled).apply()
        _disableZoomFlow.value = disabled
    }
    
    /**
     * Переключает настройку отключения зума
     */
    fun toggleDisableZoom(): Boolean {
        val newValue = !isZoomDisabled()
        setDisableZoom(newValue)
        return newValue
    }
    
    /**
     * Возвращает список всех доступных зеркал
     */
    fun getAllMirrors(): List<Pair<String, String>> {
        return listOf(
            "https://reyohoho.github.io/reyohoho" to "GitHub Pages",
            "https://reyohoho-gitlab.vercel.app/" to "Vercel GitLab",
            "https://reyohoho.gitlab.io/reyohoho/" to "GitLab Pages",
            "https://reyohoho.serv00.net/" to "Serv00",
            "https://reyohoho.onrender.com/" to "Render"
        )
    }
    
    /**
     * Обновляет страницу в WebView
     */
    fun refreshPage() {
        // Логируем обновление страницы
        android.util.Log.d("SettingsManager", "Запрошено обновление страницы")
    }

    fun isNotifyOnUpdateEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFY_ON_UPDATE, true)
    }

    fun setNotifyOnUpdate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ON_UPDATE, enabled).apply()
    }

    fun setDownloadedUpdate(version: String, downloadId: Long) {
        prefs.edit()
            .putString(KEY_DOWNLOADED_UPDATE_VERSION, version)
            .putLong(KEY_DOWNLOADED_UPDATE_ID, downloadId)
            .apply()
    }

    fun getDownloadedUpdateVersion(): String? = prefs.getString(KEY_DOWNLOADED_UPDATE_VERSION, null)
    fun getDownloadedUpdateId(): Long = prefs.getLong(KEY_DOWNLOADED_UPDATE_ID, -1L)

    fun clearDownloadedUpdate() {
        prefs.edit()
            .remove(KEY_DOWNLOADED_UPDATE_VERSION)
            .remove(KEY_DOWNLOADED_UPDATE_ID)
            .apply()
    }

    fun setIgnoredUpdate(version: String) {
        prefs.edit().putString(KEY_IGNORED_UPDATE_VERSION, version).apply()
    }

    fun getIgnoredUpdateVersion(): String? = prefs.getString(KEY_IGNORED_UPDATE_VERSION, null)

    fun clearIgnoredUpdate() {
        prefs.edit().remove(KEY_IGNORED_UPDATE_VERSION).apply()
    }

    fun isShowDownloadSpeedEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_DOWNLOAD_SPEED, true)
    fun setShowDownloadSpeed(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DOWNLOAD_SPEED, enabled).apply()
        showDownloadSpeedFlow.value = enabled
    }

    fun isShowRemainingTimeEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_REMAINING_TIME, true)
    fun setShowRemainingTime(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_REMAINING_TIME, enabled).apply()
        showRemainingTimeFlow.value = enabled
    }

    fun getProgressDisplayMode(): String = prefs.getString(KEY_PROGRESS_DISPLAY_MODE, "PERCENT") ?: "PERCENT"
    fun setProgressDisplayMode(mode: String) {
        prefs.edit().putString(KEY_PROGRESS_DISPLAY_MODE, mode).apply()
        progressDisplayModeFlow.value = mode
    }
    
    /**
     * Возвращает статус уведомлений о загрузках
     */
    fun isDownloadNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_DOWNLOAD_NOTIFICATIONS, true)
    }
    
    /**
     * Устанавливает статус уведомлений о загрузках
     */
    fun setDownloadNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOWNLOAD_NOTIFICATIONS, enabled).apply()
    }
    
    /**
     * Возвращает статус автоматического открытия загруженных файлов
     */
    fun isAutoOpenDownloadsEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_OPEN_DOWNLOADS, false)
    }
    
    /**
     * Устанавливает статус автоматического открытия загруженных файлов
     */
    fun setAutoOpenDownloads(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_OPEN_DOWNLOADS, enabled).apply()
    }
    
    /**
     * Возвращает папку загрузок
     */
    fun getDownloadFolder(): String {
        return "Downloads"
    }

    fun isShowDownloadConfirmationEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_DOWNLOAD_CONFIRMATION, true)
    fun setShowDownloadConfirmation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DOWNLOAD_CONFIRMATION, enabled).apply()
        showDownloadConfirmationFlow.value = enabled
    }
} 