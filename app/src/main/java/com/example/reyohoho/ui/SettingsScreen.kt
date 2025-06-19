package com.example.reyohoho.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reyohoho.MainActivity
import com.example.reyohoho.R
import com.example.reyohoho.ui.theme.ReYohohoTheme
import com.example.reyohoho.ui.formatFileSize
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import android.os.Environment
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Refresh
import android.app.DownloadManager
import kotlinx.coroutines.delay
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarResult
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Экран настроек приложения
 * Данная версия совместима с вызовом из MainActivity и заменяет SimpleSettingsScreen
 */
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onClose: () -> Unit,
    appVersion: String,
    onRefreshPage: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf("main") }
    val notifyOnUpdate = remember { mutableStateOf(settingsManager.isNotifyOnUpdateEnabled()) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        when (currentScreen) {
            "main" -> MainSettingsScreen(
                settingsManager = settingsManager,
                onClose = onClose,
                onNavigateTo = { screen -> currentScreen = screen }
            )
            "site" -> SiteSettingsScreen(
                settingsManager = settingsManager,
                onBack = { currentScreen = "main" },
                onClose = onClose
            )
            "appearance" -> AppearanceSettingsScreen(
                settingsManager = settingsManager,
                onBackPressed = { currentScreen = "main" },
                onClose = onClose
            )
            "about" -> AboutScreen(
                onBackPressed = { currentScreen = "main" },
                onClose = onClose,
                notifyOnUpdate = notifyOnUpdate.value,
                onNotifyOnUpdateChange = {
                    notifyOnUpdate.value = it
                    settingsManager.setNotifyOnUpdate(it)
                },
                appVersion = appVersion
            )
        }
    }
}

/**
 * Оригинальный экран настроек с навигацией между разделами
 * Сохранен для обратной совместимости
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    settingsManager: SettingsManager,
    onClose: () -> Unit,
    onNavigateTo: (String) -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(48.dp))
            
            Text(
                text = "Настройки",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF2A2A2A), shape = MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                // Настройки сайта
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Настройки сайта",
                    subtitle = "Зеркала, обновление, зум",
                    onClick = { onNavigateTo("site") }
                )
                
                // Внешний вид
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "Внешний вид",
                    subtitle = "Настройка отображения",
                    onClick = { onNavigateTo("appearance") }
                )
                
                // О приложении
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "О приложении",
                    subtitle = "Версия, автообновление, лицензия",
                    onClick = { onNavigateTo("about") }
                )
            }
        }
    }
}

/**
 * Экран настроек сайта
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val selectedMirror = settingsManager.siteMirrorFlow.collectAsState()
    val pullToRefresh = settingsManager.pullToRefreshFlow.collectAsState()
    val disableZoom = settingsManager.disableZoomFlow.collectAsState()
    
    // Получаем список всех зеркал
    val allMirrors = settingsManager.getAllMirrors()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF2A2A2A), shape = MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }
            
            Text(
                text = "Настройки сайта",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF2A2A2A), shape = MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Выбор зеркала сайта
            item {
                Text(
                    text = "Зеркало сайта",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                
                // Простой список зеркал с кнопками
                allMirrors.forEach { (url, name) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { 
                                settingsManager.setSiteMirror(url)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedMirror.value == url) Color(0xFF2A2A2A) else Color(0xFF1A1A1A)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = url,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            if (selectedMirror.value == url) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Выбрано",
                                    tint = Color.Green,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Настройки функциональности
            item {
                Text(
                    text = "Функциональность",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
                )
            }
            
            item {
                SettingSwitch(
                    title = "Обновление страницы",
                    description = "Обновление страницы свайпом вниз",
                    checked = pullToRefresh.value,
                    onCheckedChange = { settingsManager.togglePullToRefresh() }
                )
            }
            
            item {
                SettingSwitch(
                    title = "Отключить зум",
                    description = "Запретить масштабирование страницы",
                    checked = disableZoom.value,
                    onCheckedChange = { settingsManager.toggleDisableZoom() }
                )
            }
        }
    }
}

@Composable
fun AppSettingsScreen(
    onBackPressed: () -> Unit,
    settingsManager: SettingsManager
) {
    val removeTopSpacing = settingsManager.removeTopSpacingFlow.collectAsState()
    val fullscreenMode = settingsManager.fullscreenModeFlow.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Переключатели настроек
        SettingSwitch(
            title = "Убрать отступ сверху", 
            description = "Уменьшает пустое пространство вверху страницы",
            checked = removeTopSpacing.value,
            onCheckedChange = { settingsManager.toggleTopSpacing() }
        )
        
        SettingSwitch(
            title = "Полноэкранный режим",
            description = "Скрыть системные панели",
            checked = fullscreenMode.value,
            onCheckedChange = { settingsManager.toggleFullscreenMode() }
        )
        
        val versionName = "1.0.0" // Замените на реальное значение
        Text(
            text = "Версия приложения: $versionName",
            color = Color.Gray,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}

@Composable
fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF4CAF50),
                checkedTrackColor = Color(0x884CAF50),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}

@Composable
fun AppearanceSettingsScreen(
    settingsManager: SettingsManager,
    onBackPressed: () -> Unit,
    onClose: () -> Unit
) {
    val removeTopSpacing = settingsManager.removeTopSpacingFlow.collectAsState()
    val fullscreenMode = settingsManager.fullscreenModeFlow.collectAsState()
    val context = LocalContext.current
    
    // Отслеживаем изменение настройки полноэкранного режима
    LaunchedEffect(fullscreenMode.value) {
        try {
            (context as? MainActivity)?.setupImmersiveMode()
        } catch (e: Exception) {
            Log.e("SettingsScreen", "Ошибка при применении полноэкранного режима: ${e.message}")
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF2A2A2A), shape = MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }
            
            Text(
                text = "Внешний вид",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF2A2A2A), shape = MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White
                )
            }
        }
        
        LazyColumn {
            item {
                // Настройка отступа
                SettingSwitch(
                    title = "Убрать отступ сверху",
                    description = "Уменьшает пустое пространство вверху страницы",
                    checked = removeTopSpacing.value,
                    onCheckedChange = { settingsManager.toggleTopSpacing() }
                )
                
                // Настройка полноэкранного режима
                SettingSwitch(
                    title = "Полноэкранный режим",
                    description = "Скрыть системные панели для более комфортного просмотра",
                    checked = fullscreenMode.value,
                    onCheckedChange = { settingsManager.toggleFullscreenMode() }
                )
            }
        }
    }
}

@Composable
fun AboutScreen(
    onBackPressed: () -> Unit,
    onClose: () -> Unit = {},
    notifyOnUpdate: Boolean,
    onNotifyOnUpdateChange: (Boolean) -> Unit,
    appVersion: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showUpdateBanner by remember { mutableStateOf(false) }
    var bannerDismissed by remember { mutableStateOf(false) }
    var downloadInProgress by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadStatus by remember { mutableStateOf<Int?>(null) }
    var downloadedFilePath by remember { mutableStateOf<String?>(null) }
    var downloaded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsManager = SettingsManager.getInstance(context)
    val savedDownloadedVersion = settingsManager.getDownloadedUpdateVersion()
    val savedDownloadedId = settingsManager.getDownloadedUpdateId()
    val ignoredUpdateVersion = settingsManager.getIgnoredUpdateVersion()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // Верхняя панель (стрелка, заголовок, крестик)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF2A2A2A), shape = MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "О приложении",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF2A2A2A), shape = MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White
                )
            }
        }
        // Иконка под заголовком
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.CenterHorizontally)
        )
        // Контент по центру
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Версия: $appVersion",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            // Баннер о новой версии (компактный)
            Button(
                onClick = {
                    if (savedDownloadedVersion == latestVersion && savedDownloadedId > 0) return@Button
                    if (ignoredUpdateVersion == latestVersion) return@Button
                    isChecking = true
                    errorMessage = ""
                    coroutineScope.launch {
                        try {
                            val result = UpdateChecker.checkForUpdate()
                            isChecking = false
                            if (result.isUpdateAvailable) {
                                updateAvailable = true
                                latestVersion = result.latestVersion
                            } else {
                                updateAvailable = false
                                latestVersion = result.latestVersion
                            }
                        } catch (e: Exception) {
                            isChecking = false
                            errorMessage = e.message ?: "Ошибка при проверке обновления"
                        }
                    }
                },
                enabled = !isChecking && !downloadInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isChecking) "Проверка..." else "Проверить обновление")
            }
            Spacer(modifier = Modifier.height(16.dp))
            // ВОССТАНОВЛЕННЫЙ ПЕРЕКЛЮЧАТЕЛЬ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Уведомлять о новой версии",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = notifyOnUpdate,
                    onCheckedChange = onNotifyOnUpdateChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0x884CAF50),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0x33FFFFFF)
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (updateAvailable) {
                if (savedDownloadedVersion == latestVersion && savedDownloadedId > 0) {
                    // Кнопка 'Обновить' с прогресс-баром
                    if (downloadInProgress) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinearProgressIndicator(progress = downloadProgress ?: 0f, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "${((downloadProgress ?: 0f) * 100).toInt()}%", color = Color.White, fontSize = 16.sp)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                downloadInProgress = true
                                coroutineScope.launch {
                                    try {
                                        val result = UpdateChecker.checkForUpdate()
                                        val downloadId = UpdateChecker.downloadApkOnly(context, result.downloadUrl, latestVersion)
                                        settingsManager.setDownloadedUpdate(latestVersion, downloadId)
                                        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        for (i in 1..180) {
                                            val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                                            val cursor = dm.query(query)
                                            if (cursor.moveToFirst()) {
                                                val status = cursor.getInt(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS))
                                                val bytesDownloaded = cursor.getLong(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                                val bytesTotal = cursor.getLong(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                                downloadStatus = status
                                                downloadProgress = if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal else 0f
                                                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                                                    cursor.close()
                                                    downloadStatus = 8
                                                    downloadProgress = 1f
                                                    downloadedFilePath = downloadId.toString()
                                                    downloaded = true
                                                    settingsManager.setDownloadedUpdate(latestVersion, downloadId)
                                                    // Автоматическая установка APK
                                                    UpdateChecker.installDownloadedApk(context, downloadId)
                                                    break
                                                } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                                                    cursor.close()
                                                    downloadStatus = 16
                                                    break
                                                }
                                            }
                                            cursor.close()
                                            kotlinx.coroutines.delay(1000)
                                        }
                                        downloadInProgress = false
                                    } catch (e: Exception) {
                                        downloadInProgress = false
                                        errorMessage = e.message ?: "Ошибка загрузки обновления"
                                        downloadStatus = 16
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                        ) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Обновить", fontSize = 18.sp)
                        }
                    }
                } else {
                    // Кнопка 'Загрузить обновление'
                    FilledTonalButton(
                        onClick = {
                            downloadInProgress = true
                            coroutineScope.launch {
                                try {
                                    val result = UpdateChecker.checkForUpdate()
                                    val downloadId = UpdateChecker.downloadApkOnly(context, result.downloadUrl, latestVersion)
                                    settingsManager.setDownloadedUpdate(latestVersion, downloadId)
                                    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                    for (i in 1..180) {
                                        val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                                        val cursor = dm.query(query)
                                        if (cursor.moveToFirst()) {
                                            val status = cursor.getInt(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS))
                                            val bytesDownloaded = cursor.getLong(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                            val bytesTotal = cursor.getLong(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                            downloadStatus = status
                                            downloadProgress = if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal else 0f
                                            if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                                                cursor.close()
                                                downloadStatus = 8
                                                downloadProgress = 1f
                                                downloadedFilePath = downloadId.toString()
                                                downloaded = true
                                                settingsManager.setDownloadedUpdate(latestVersion, downloadId)
                                                // Автоматическая установка APK
                                                UpdateChecker.installDownloadedApk(context, downloadId)
                                                break
                                            } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                                                cursor.close()
                                                downloadStatus = 16
                                                break
                                            }
                                        }
                                        cursor.close()
                                        kotlinx.coroutines.delay(1000)
                                    }
                                    downloadInProgress = false
                                } catch (e: Exception) {
                                    downloadInProgress = false
                                    errorMessage = e.message ?: "Ошибка загрузки обновления"
                                    downloadStatus = 16
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Загрузить обновление", fontSize = 18.sp)
                    }
                }
            }
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    // SnackbarHost для баннера
    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFF2A2A2A), shape = RoundedCornerShape(23.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF4CAF50),
                    checkedTrackColor = Color(0x884CAF50),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0x33FFFFFF)
                )
            )
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFF2A2A2A), shape = RoundedCornerShape(23.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Перейти",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
} 