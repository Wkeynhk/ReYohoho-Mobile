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
        private const val KEY_TOP_SPACING_SIZE = "top_spacing_size"
        private const val KEY_FULLSCREEN_MODE = "fullscreen_mode"
        private const val KEY_DEVICE_TYPE = "device_type"
        private const val KEY_SITE_MIRROR = "site_mirror"
        private const val KEY_PULL_TO_REFRESH = "pull_to_refresh"
        private const val KEY_DISABLE_ZOOM = "disable_zoom"
        private const val KEY_NOTIFY_ON_UPDATE = "notify_on_update"
        private const val KEY_UPDATE_NOTIFY_MULTIPLE = "update_notify_multiple"
        private const val KEY_UPDATE_NOTIFY_INTERVAL = "update_notify_interval"
        private const val KEY_LAST_UPDATE_NOTIFICATION_TIME = "last_update_notification_time"
        private const val KEY_DOWNLOADED_UPDATE_VERSION = "downloaded_update_version"
        private const val KEY_DOWNLOADED_UPDATE_ID = "downloaded_update_id"
        private const val KEY_IGNORED_UPDATE_VERSION = "ignored_update_version"
        private const val KEY_SHOW_DOWNLOAD_SPEED = "show_download_speed"
        private const val KEY_SHOW_REMAINING_TIME = "show_remaining_time"
        private const val KEY_PROGRESS_DISPLAY_MODE = "progress_display_mode"
        private const val KEY_DOWNLOAD_NOTIFICATIONS = "download_notifications"
        private const val KEY_AUTO_OPEN_DOWNLOADS = "auto_open_downloads"
        private const val KEY_SHOW_DOWNLOAD_CONFIRMATION = "show_download_confirmation"
        private const val KEY_ADBLOCK_REMEMBER_CHOICE = "adblock_remember_choice"
        private const val KEY_ADBLOCK_PREFERRED_SOURCE = "adblock_preferred_source"
        private const val KEY_LOAD_ON_MAIN_PAGE = "load_on_main_page"
        private const val KEY_SHOW_SETTINGS_BUTTON_ONLY_ON_SETTINGS_PAGE = "show_settings_button_only_on_settings_page"
        private const val KEY_SETTINGS_BUTTON_X = "settings_button_x"
        private const val KEY_SETTINGS_BUTTON_Y = "settings_button_y"
        private const val KEY_TORRENT_BUTTON_X = "torrent_button_x"
        private const val KEY_TORRENT_BUTTON_Y = "torrent_button_y"
        private const val KEY_TORRENTS_ENABLED = "torrents_enabled"
        private const val KEY_SETTINGS_BUTTON_END = "settings_button_end"
        private const val KEY_SETTINGS_BUTTON_BOTTOM = "settings_button_bottom"
        private const val KEY_TORRENT_BUTTON_END = "torrent_button_end"
        private const val KEY_TORRENT_BUTTON_BOTTOM = "torrent_button_bottom"
        private const val KEY_USE_INTERNAL_TORRSERVE = "use_internal_torrserve"
        private const val KEY_EXTERNAL_TORRSERVE_URL = "external_torrserve_url"
        private const val KEY_TORR_SERVER_MODE = "torr_server_mode"
        private const val KEY_FREE_TORR_ENABLED = "free_torr_enabled"
        private const val KEY_FREE_TORR_SERVER_URL = "free_torr_server_url"
        private const val KEY_ADBLOCK_DISABLED = "adblock_disabled"
        private const val KEY_HIDE_TORRENT_VIP_INFO = "hide_torrent_vip_info"
        const val DEVICE_TYPE_ANDROID = "android"
        const val DEVICE_TYPE_ANDROID_TV = "android_tv"
        const val ADBLOCK_SOURCE_INTERNET = "internet"
        const val ADBLOCK_SOURCE_LOCAL = "local"
        const val TORR_SERVER_MODE_CUSTOM = "custom"
        const val TORR_SERVER_MODE_FREE_TORR = "free_torr"
        private const val DEFAULT_EXTERNAL_TORRSERVE_URL = "http://localhost:8090/"

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
    
    // StateFlow для отслеживания размера отступа сверху
    private val _topSpacingSizeFlow = MutableStateFlow(getTopSpacingSize())
    val topSpacingSizeFlow: StateFlow<Int> = _topSpacingSizeFlow.asStateFlow()
    
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
    
    // StateFlow для настроек уведомлений об обновлениях
    private val _updateNotifyMultipleFlow = MutableStateFlow(isUpdateNotifyMultipleEnabled())
    val updateNotifyMultipleFlow: StateFlow<Boolean> = _updateNotifyMultipleFlow.asStateFlow()
    
    private val _updateNotifyIntervalFlow = MutableStateFlow(getUpdateNotifyInterval())
    val updateNotifyIntervalFlow: StateFlow<Int> = _updateNotifyIntervalFlow.asStateFlow()
    
    // StateFlow для отслеживания настройки загрузки на главную страницу
    private val _loadOnMainPageFlow = MutableStateFlow(isLoadOnMainPageEnabled())
    val loadOnMainPageFlow: StateFlow<Boolean> = _loadOnMainPageFlow.asStateFlow()
    
    // StateFlow для отслеживания настройки показа кнопки настроек
    private val _showSettingsButtonOnlyOnSettingsPageFlow = MutableStateFlow(isShowSettingsButtonOnlyOnSettingsPageEnabled())
    val showSettingsButtonOnlyOnSettingsPageFlow: StateFlow<Boolean> = _showSettingsButtonOnlyOnSettingsPageFlow.asStateFlow()
    
    // StateFlow для позиции кнопки настроек (end, bottom в dp)
    private val _settingsButtonPaddingFlow = MutableStateFlow(getSettingsButtonPadding())
    val settingsButtonPaddingFlow: StateFlow<Pair<Float, Float>> = _settingsButtonPaddingFlow.asStateFlow()
    fun getSettingsButtonPadding(): Pair<Float, Float> {
        val end = prefs.getFloat(KEY_SETTINGS_BUTTON_END, 16f)
        val bottom = prefs.getFloat(KEY_SETTINGS_BUTTON_BOTTOM, 80f)
        return end to bottom
    }
    fun setSettingsButtonPadding(end: Float, bottom: Float) {
        prefs.edit().putFloat(KEY_SETTINGS_BUTTON_END, end).putFloat(KEY_SETTINGS_BUTTON_BOTTOM, bottom).apply()
        _settingsButtonPaddingFlow.value = end to bottom
    }
    fun resetSettingsButtonPadding() {
        setSettingsButtonPadding(16f, 80f)
    }

    // StateFlow для позиции кнопки торрентов (end, bottom в dp)
    private val _torrentButtonPaddingFlow = MutableStateFlow(getTorrentButtonPadding())
    val torrentButtonPaddingFlow: StateFlow<Pair<Float, Float>> = _torrentButtonPaddingFlow.asStateFlow()
    fun getTorrentButtonPadding(): Pair<Float, Float> {
        val end = prefs.getFloat(KEY_TORRENT_BUTTON_END, 88f)
        val bottom = prefs.getFloat(KEY_TORRENT_BUTTON_BOTTOM, 80f)
        return end to bottom
    }
    fun setTorrentButtonPadding(end: Float, bottom: Float) {
        prefs.edit().putFloat(KEY_TORRENT_BUTTON_END, end).putFloat(KEY_TORRENT_BUTTON_BOTTOM, bottom).apply()
        _torrentButtonPaddingFlow.value = end to bottom
    }
    fun resetTorrentButtonPadding() {
        setTorrentButtonPadding(88f, 80f)
    }

    // StateFlow для переключателя торрентов
    private val _torrentsEnabledFlow = MutableStateFlow(isTorrentsEnabled())
    val torrentsEnabledFlow: StateFlow<Boolean> = _torrentsEnabledFlow.asStateFlow()
    fun isTorrentsEnabled(): Boolean {
        return prefs.getBoolean(KEY_TORRENTS_ENABLED, false)
    }
    fun setTorrentsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TORRENTS_ENABLED, enabled).apply()
        _torrentsEnabledFlow.value = enabled
    }

    // StateFlow для настройки использования внутреннего TorrServe
    private val _useInternalTorrServeFlow = MutableStateFlow(isUseInternalTorrServe())
    val useInternalTorrServeFlow: StateFlow<Boolean> = _useInternalTorrServeFlow.asStateFlow()
    
    // StateFlow для URL внешнего TorrServe
    private val _externalTorrServeUrlFlow = MutableStateFlow(getExternalTorrServeUrl())
    val externalTorrServeUrlFlow: StateFlow<String> = _externalTorrServeUrlFlow.asStateFlow()
    
    /**
     * Проверяет, используется ли встроенный TorrServe
     */
    fun isUseInternalTorrServe(): Boolean {
        return prefs.getBoolean(KEY_USE_INTERNAL_TORRSERVE, true)
    }
    
    /**
     * Устанавливает использование встроенного или внешнего TorrServe
     */
    fun setUseInternalTorrServe(useInternal: Boolean) {
        prefs.edit().putBoolean(KEY_USE_INTERNAL_TORRSERVE, useInternal).apply()
        _useInternalTorrServeFlow.value = useInternal
    }
    
    /**
     * Возвращает URL внешнего TorrServe
     */
    fun getExternalTorrServeUrl(): String {
        return prefs.getString(KEY_EXTERNAL_TORRSERVE_URL, DEFAULT_EXTERNAL_TORRSERVE_URL) 
            ?: DEFAULT_EXTERNAL_TORRSERVE_URL
    }
    
    /**
     * Устанавливает URL внешнего TorrServe
     */
    fun setExternalTorrServeUrl(url: String) {
        prefs.edit().putString(KEY_EXTERNAL_TORRSERVE_URL, url).apply()
        _externalTorrServeUrlFlow.value = url
    }

    // StateFlow для режима торрент-сервера
    private val _torrServerModeFlow = MutableStateFlow(getTorrServerMode())
    val torrServerModeFlow: StateFlow<String> = _torrServerModeFlow.asStateFlow()
    
    // StateFlow для включения FreeTorr
    private val _freeTorrEnabledFlow = MutableStateFlow(isFreeTorrEnabled())
    val freeTorrEnabledFlow: StateFlow<Boolean> = _freeTorrEnabledFlow.asStateFlow()
    
    // StateFlow для URL FreeTorr сервера
    private val _freeTorrServerUrlFlow = MutableStateFlow(getFreeTorrServerUrl())
    val freeTorrServerUrlFlow: StateFlow<String> = _freeTorrServerUrlFlow.asStateFlow()
    

    
    /**
     * Возвращает режим торрент-сервера
     */
    fun getTorrServerMode(): String {
        return prefs.getString(KEY_TORR_SERVER_MODE, TORR_SERVER_MODE_CUSTOM) ?: TORR_SERVER_MODE_CUSTOM
    }
    
    /**
     * Устанавливает режим торрент-сервера
     */
    fun setTorrServerMode(mode: String) {
        prefs.edit().putString(KEY_TORR_SERVER_MODE, mode).apply()
        _torrServerModeFlow.value = mode
    }
    
    /**
     * Проверяет, включен ли FreeTorr
     */
    fun isFreeTorrEnabled(): Boolean {
        return prefs.getBoolean(KEY_FREE_TORR_ENABLED, false)
    }
    
    /**
     * Устанавливает включение FreeTorr
     */
    fun setFreeTorrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FREE_TORR_ENABLED, enabled).apply()
        _freeTorrEnabledFlow.value = enabled
    }
    
    /**
     * Проверяет, включено ли автоматическое переключение серверов (всегда true)
     */
    fun isAutoSwitchServersEnabled(): Boolean {
        return true
    }
    
    /**
     * Возвращает URL FreeTorr сервера
     */
    fun getFreeTorrServerUrl(): String {
        return prefs.getString(KEY_FREE_TORR_SERVER_URL, "http://localhost:8090/") ?: "http://localhost:8090/"
    }
    
    /**
     * Устанавливает URL FreeTorr сервера
     */
    fun setFreeTorrServerUrl(url: String) {
        prefs.edit().putString(KEY_FREE_TORR_SERVER_URL, url).apply()
        _freeTorrServerUrlFlow.value = url
    }
    
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
     * Возвращает размер отступа сверху в пикселях
     */
    fun getTopSpacingSize(): Int {
        return prefs.getInt(KEY_TOP_SPACING_SIZE, 24)
    }
    
    /**
     * Устанавливает размер отступа сверху в пикселях
     */
    fun setTopSpacingSize(size: Int) {
        prefs.edit().putInt(KEY_TOP_SPACING_SIZE, size).apply()
        _topSpacingSizeFlow.value = size
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
    
    /**
     * Возвращает статус настройки множественных уведомлений об обновлениях
     */
    fun isUpdateNotifyMultipleEnabled(): Boolean {
        return prefs.getBoolean(KEY_UPDATE_NOTIFY_MULTIPLE, false)
    }
    
    /**
     * Устанавливает статус настройки множественных уведомлений об обновлениях
     */
    fun setUpdateNotifyMultiple(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_NOTIFY_MULTIPLE, enabled).apply()
        _updateNotifyMultipleFlow.value = enabled
    }
    
    /**
     * Возвращает интервал между уведомлениями об обновлениях в часах
     */
    fun getUpdateNotifyInterval(): Int {
        return prefs.getInt(KEY_UPDATE_NOTIFY_INTERVAL, 24)
    }
    
    /**
     * Устанавливает интервал между уведомлениями об обновлениях в часах
     */
    fun setUpdateNotifyInterval(hours: Int) {
        prefs.edit().putInt(KEY_UPDATE_NOTIFY_INTERVAL, hours).apply()
        _updateNotifyIntervalFlow.value = hours
    }
    
    /**
     * Возвращает время последнего уведомления об обновлении для конкретной версии
     */
    fun getLastUpdateNotificationTime(version: String): Long {
        return prefs.getLong("${KEY_LAST_UPDATE_NOTIFICATION_TIME}_$version", 0L)
    }
    
    /**
     * Устанавливает время последнего уведомления об обновлении для конкретной версии
     */
    fun setLastUpdateNotificationTime(version: String, time: Long) {
        prefs.edit().putLong("${KEY_LAST_UPDATE_NOTIFICATION_TIME}_$version", time).apply()
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

    fun isAdblockRememberChoiceEnabled(): Boolean = prefs.getBoolean(KEY_ADBLOCK_REMEMBER_CHOICE, false)
    fun setAdblockRememberChoice(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADBLOCK_REMEMBER_CHOICE, enabled).apply()
    }

    fun getAdblockPreferredSource(): String = prefs.getString(KEY_ADBLOCK_PREFERRED_SOURCE, ADBLOCK_SOURCE_INTERNET) ?: ADBLOCK_SOURCE_INTERNET
    fun setAdblockPreferredSource(source: String) {
        prefs.edit().putString(KEY_ADBLOCK_PREFERRED_SOURCE, source).apply()
    }
    
    /**
     * Возвращает статус настройки загрузки на главную страницу
     */
    fun isLoadOnMainPageEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOAD_ON_MAIN_PAGE, false)
    }
    
    /**
     * Устанавливает статус настройки загрузки на главную страницу
     */
    fun setLoadOnMainPage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOAD_ON_MAIN_PAGE, enabled).apply()
        _loadOnMainPageFlow.value = enabled
    }
    
    /**
     * Переключает настройку загрузки на главную страницу
     */
    fun toggleLoadOnMainPage(): Boolean {
        val newValue = !isLoadOnMainPageEnabled()
        setLoadOnMainPage(newValue)
        return newValue
    }
    
    /**
     * Возвращает статус настройки показа кнопки настроек только на странице настроек
     */
    fun isShowSettingsButtonOnlyOnSettingsPageEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHOW_SETTINGS_BUTTON_ONLY_ON_SETTINGS_PAGE, false)
    }
    
    /**
     * Устанавливает статус настройки показа кнопки настроек только на странице настроек
     */
    fun setShowSettingsButtonOnlyOnSettingsPage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SETTINGS_BUTTON_ONLY_ON_SETTINGS_PAGE, enabled).apply()
        _showSettingsButtonOnlyOnSettingsPageFlow.value = enabled
    }
    
    /**
     * Переключает настройку показа кнопки настроек только на странице настроек
     */
    fun toggleShowSettingsButtonOnlyOnSettingsPage(): Boolean {
        val newValue = !isShowSettingsButtonOnlyOnSettingsPageEnabled()
        setShowSettingsButtonOnlyOnSettingsPage(newValue)
        return newValue
    }

    // StateFlow для полного отключения блокировщика рекламы
    private val _adblockDisabledFlow = MutableStateFlow(isAdblockDisabled())
    val adblockDisabledFlow: StateFlow<Boolean> = _adblockDisabledFlow.asStateFlow()
    fun isAdblockDisabled(): Boolean {
        return prefs.getBoolean(KEY_ADBLOCK_DISABLED, false)
    }
    fun setAdblockDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADBLOCK_DISABLED, disabled).apply()
        _adblockDisabledFlow.value = disabled
    }
    fun toggleAdblockDisabled(): Boolean {
        val newValue = !isAdblockDisabled()
        setAdblockDisabled(newValue)
        return newValue
    }

    // StateFlow для отслеживания состояния отображения информации о VIP в разделе торрентов
    private val _hideTorrentVipInfoFlow = MutableStateFlow(isHideTorrentVipInfo())
    val hideTorrentVipInfoFlow: StateFlow<Boolean> = _hideTorrentVipInfoFlow.asStateFlow()
    
    /**
     * Проверяет, скрыта ли информация о том, что торренты не относятся к VIP
     */
    fun isHideTorrentVipInfo(): Boolean {
        return prefs.getBoolean(KEY_HIDE_TORRENT_VIP_INFO, false)
    }
    
    /**
     * Устанавливает состояние отображения информации о VIP в разделе торрентов
     */
    fun setHideTorrentVipInfo(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_TORRENT_VIP_INFO, hide).apply()
        _hideTorrentVipInfoFlow.value = hide
    }
    
    /**
     * Показывает информацию о том, что торренты не относятся к VIP
     */
    fun showTorrentVipInfo() {
        setHideTorrentVipInfo(false)
    }
    
    /**
     * Скрывает информацию о том, что торренты не относятся к VIP
     */
    fun hideTorrentVipInfo() {
        setHideTorrentVipInfo(true)
    }
} 