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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
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
import com.example.reyohoho.AdBlocker
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
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
import com.example.reyohoho.DownloadStatus
import com.example.reyohoho.AppDownloadManager
import android.app.Activity
import com.example.reyohoho.DownloadInfo
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.Job
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults

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
    // BackHandler: если не main — переход на main, если main — закрыть настройки
    androidx.activity.compose.BackHandler {
        if (currentScreen != "main") {
            currentScreen = "main"
        } else {
            onClose()
        }
    }
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
            "downloads" -> DownloadsScreen(onBack = { currentScreen = "main" }, onClose = onClose)
            "download_settings" -> DownloadSettingsScreen(
                settingsManager = settingsManager,
                onBack = { currentScreen = "main" },
                onClose = onClose
            )
            "torrents" -> TorrentsSettingsScreen(onBack = { currentScreen = "main" }, onClose = onClose)
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
    val downloadManager = remember {
        try {
            AppDownloadManager.getInstance(context)
        } catch (e: Exception) {
            null
        }
    }
    val activeDownloads by downloadManager?.activeDownloads?.collectAsState() ?: remember { mutableStateOf(emptyMap<Long, DownloadInfo>()) }
    val downloadHistory by downloadManager?.downloadHistory?.collectAsState() ?: remember { mutableStateOf(emptyList<DownloadInfo>()) }
    val coroutineScope = rememberCoroutineScope()
    
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
                    icon = Icons.Default.Settings,
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
                
                // Кнопка перехода к загрузкам
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "Загрузки",
                    subtitle = "Смотреть активные и завершённые загрузки",
                    onClick = { onNavigateTo("downloads") }
                )
                
                // --- Новый раздел Торренты ---
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "Торренты",
                    subtitle = "Интеграция с TorrServe",
                    onClick = { onNavigateTo("torrents") }
                )
                
                // О приложении
                SettingsItem(
                    icon = Icons.Default.Settings,
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
    val loadOnMainPage = settingsManager.loadOnMainPageFlow.collectAsState()
    
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            
            item {
                SettingSwitch(
                    title = "Загружать на последней странице",
                    description = "Загружаться на последней посещённой странице вместо главной",
                    checked = loadOnMainPage.value,
                    onCheckedChange = { settingsManager.toggleLoadOnMainPage() }
                )
            }
            
            item {
                SettingSwitch(
                    title = "Отключить блокировщик рекламы",
                    description = "Полностью отключить блокировку рекламы",
                    checked = settingsManager.adblockDisabledFlow.collectAsState().value,
                    onCheckedChange = { settingsManager.setAdblockDisabled(it) }
                )
            }
            
            // Настройки блокировщика рекламы
            item {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val settingsManager = SettingsManager.getInstance(context)
                
                var selectedSource by remember { 
                    mutableStateOf(
                        if (AdBlocker.isUsingLocalFile()) 
                            SettingsManager.ADBLOCK_SOURCE_LOCAL 
                        else 
                            SettingsManager.ADBLOCK_SOURCE_INTERNET
                    ) 
                }
                var domainsCount by remember { mutableStateOf(AdBlocker.getLoadedDomainsCount()) }
                var isCheckingAvailability by remember { mutableStateOf(false) }
                var internetAvailable by remember { mutableStateOf(false) }
                var localFileAvailable by remember { mutableStateOf(AdBlocker.checkLocalFileAvailable(context)) }
                var rememberChoice by remember { mutableStateOf(settingsManager.isAdblockRememberChoiceEnabled()) }
                var showInternetInfo by remember { mutableStateOf(false) }
                var showLocalInfo by remember { mutableStateOf(false) }
                
                // Проверяем доступность источников при загрузке
                LaunchedEffect(Unit) {
                    isCheckingAvailability = true
                    internetAvailable = AdBlocker.checkInternetSourceAvailable()
                    localFileAvailable = AdBlocker.checkLocalFileAvailable(context)
                    isCheckingAvailability = false
                }
                
                Column {
                    // Заголовок секции
                    Text(
                        text = "Блокировщик рекламы",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
                    )
                    
                    // Настройка запоминания выбора
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Запоминать выбор источника",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Выбор будет сохранен и использован при следующем запуске",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        Switch(
                            checked = rememberChoice,
                            onCheckedChange = { newValue ->
                                rememberChoice = newValue
                                settingsManager.setAdblockRememberChoice(newValue)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0x884CAF50),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }
                    
                    // Выбор источника доменов
                    Text(
                        text = "Источник доменов для блокировки:",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )

                    val segmentOptions = listOf("Интернет", "Локальный файл")
                    val segmentDescriptions = listOf(
                        "Актуальный список доменов из интернета",
                        "Встроенный список доменов в приложении"
                    )
                    val infoDialogState = remember { mutableStateOf<String?>(null) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Color(0xFF23272F), shape = RoundedCornerShape(12.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        segmentOptions.forEachIndexed { idx, label ->
                            val selected = (selectedSource == SettingsManager.ADBLOCK_SOURCE_INTERNET && idx == 0) ||
                                    (selectedSource == SettingsManager.ADBLOCK_SOURCE_LOCAL && idx == 1)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF22C55E) else Color.Transparent)
                                    .clickable {
                                        selectedSource = if (idx == 0) SettingsManager.ADBLOCK_SOURCE_INTERNET else SettingsManager.ADBLOCK_SOURCE_LOCAL
                                        AdBlocker.setPreferredSource(context, idx == 1)
                                        coroutineScope.launch {
                                            try {
                                                AdBlocker.reloadDomains(context)
                                                domainsCount = AdBlocker.getLoadedDomainsCount()
                                            } catch (e: Exception) {
                                                Log.e("SettingsScreen", "Ошибка при перезагрузке доменов: ${e.message}")
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else Color.Gray,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                            }
                            if (idx == 0) Divider(
                                color = Color(0xFF23272F),
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                            )
                        }
                        // Иконка информации справа
                        IconButton(
                            onClick = {
                                infoDialogState.value = "both"
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Подробнее",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Краткое описание выбранного варианта
                    Text(
                        text = if (selectedSource == SettingsManager.ADBLOCK_SOURCE_INTERNET) segmentDescriptions[0] else segmentDescriptions[1],
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )

                    // Диалог подробной информации
                    if (infoDialogState.value != null) {
                        AlertDialog(
                            onDismissRequest = { infoDialogState.value = null },
                            confirmButton = {
                                TextButton(onClick = { infoDialogState.value = null }) {
                                    Text("ОК")
                                }
                            },
                            title = {
                                Text(
                                    text = "Источники доменов для блокировки",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Column {
                                    Text(
                                        text = "Интернет-источник (EasyList):",
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = "• Загружает актуальный список доменов из интернета\n" +
                                            "• Требует подключения к интернету\n" +
                                            "• Содержит больше доменов для блокировки\n" +
                                            "• Может быть медленнее при загрузке",
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    
                                    Text(
                                        text = "Локальный файл:",
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = "• Использует встроенный список доменов\n" +
                                            "• Работает без интернета\n" +
                                            "• Быстрая загрузка\n" +
                                            "• Меньше доменов, но стабильная работа"
                                    )
                                }
                            }
                        )
                    }
                    
                    // Индикатор загрузки
                    if (isCheckingAvailability) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Проверка доступности источников...",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    // Количество загруженных доменов
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Домены",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Загружено доменов: $domainsCount",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Кнопка перезагрузки доменов
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { 
                                coroutineScope.launch {
                                    try {
                                        AdBlocker.reloadDomains(context)
                                        domainsCount = AdBlocker.getLoadedDomainsCount()
                                    } catch (e: Exception) {
                                        Log.e("SettingsScreen", "Ошибка при перезагрузке доменов: ${e.message}")
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A1A)
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
                            Text(
                                text = "Перезагрузить домены",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Перезагрузить",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
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
    var showSettingsInfo by remember { mutableStateOf(false) }
    var showTorrentInfo by remember { mutableStateOf(false) }
    val removeTopSpacing = settingsManager.removeTopSpacingFlow.collectAsState()
    val fullscreenMode = settingsManager.fullscreenModeFlow.collectAsState()
    val showSettingsButtonOnlyOnSettingsPage = settingsManager.showSettingsButtonOnlyOnSettingsPageFlow.collectAsState()
    val settingsButtonPadding = settingsManager.settingsButtonPaddingFlow.collectAsState()
    var showSettingsPositionOverlay by remember { mutableStateOf(false) }
    var previewSettingsButtonOffset by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var showTorrentPositionOverlay by remember { mutableStateOf(false) }
    var previewTorrentButtonOffset by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val torrentButtonPadding = settingsManager.torrentButtonPaddingFlow.collectAsState()
    val context = LocalContext.current
    
    // Отслеживаем изменение настройки полноэкранного режима
    LaunchedEffect(fullscreenMode.value) {
        try {
            (context as? MainActivity)?.setupImmersiveMode()
        } catch (e: Exception) {
            Log.e("SettingsScreen", "Ошибка при применении полноэкранного режима: ${e.message}")
        }
    }
    
    Box(Modifier.fillMaxSize()) {
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
            Spacer(modifier = Modifier.height(32.dp))
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
            // Переключатель "Кнопка настроек только на странице настроек" ПЕРЕД выбором положения кнопок
            SettingSwitch(
                title = "Кнопка настроек только на странице настроек",
                description = "Показывать кнопку настроек только на странице настроек сайта",
                checked = showSettingsButtonOnlyOnSettingsPage.value,
                onCheckedChange = { settingsManager.toggleShowSettingsButtonOnlyOnSettingsPage() }
            )
            // --- Положение кнопки настроек ---
            Spacer(modifier = Modifier.height(20.dp))
            Text("Положение кнопки настроек", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { showSettingsPositionOverlay = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                ) { Text("Выбрать") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { settingsManager.resetSettingsButtonPadding() },
                    modifier = Modifier,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336))
                ) { Text("Сбросить") }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { showSettingsInfo = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Инфо", tint = Color(0xFF4CAF50))
                }
            }
            if (showSettingsInfo) {
                AlertDialog(
                    onDismissRequest = { showSettingsInfo = false },
                    confirmButton = { TextButton(onClick = { showSettingsInfo = false }) { Text("ОК") } },
                    title = { Text("Положение кнопки настроек") },
                    text = {
                        val (end, bottom) = settingsButtonPadding.value
                        Text("Текущее положение: end=${end.toInt()}dp, bottom=${bottom.toInt()}dp\n(отступ справа и снизу)")
                    }
                )
            }
            // --- Положение кнопки торрентов ---
            Spacer(modifier = Modifier.height(20.dp))
            Text("Положение кнопки торрентов", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { showTorrentPositionOverlay = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                ) { Text("Выбрать") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { settingsManager.resetTorrentButtonPadding() },
                    modifier = Modifier,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336))
                ) { Text("Сбросить") }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { showTorrentInfo = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Инфо", tint = Color(0xFF4CAF50))
                }
            }
            if (showTorrentInfo) {
                AlertDialog(
                    onDismissRequest = { showTorrentInfo = false },
                    confirmButton = { TextButton(onClick = { showTorrentInfo = false }) { Text("ОК") } },
                    title = { Text("Положение кнопки торрентов") },
                    text = {
                        val (end, bottom) = torrentButtonPadding.value
                        Text("Текущее положение: end=${end.toInt()}dp, bottom=${bottom.toInt()}dp\n(отступ справа и снизу)")
                    }
                )
            }
        }
        if (showSettingsPositionOverlay) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                Text(
                    text = "Нажмите в нужное место для кнопки",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    previewSettingsButtonOffset = offset.x to offset.y
                                },
                                onTap = { offset ->
                                    val screenWidth = size.width
                                    val screenHeight = size.height
                                    val endPx = screenWidth - offset.x
                                    val bottomPx = screenHeight - offset.y
                                    val endDp = with(density) { endPx.toDp().value }
                                    val bottomDp = with(density) { bottomPx.toDp().value }
                                    settingsManager.setSettingsButtonPadding(endDp, bottomDp)
                                    showSettingsPositionOverlay = false
                                    previewSettingsButtonOffset = null
                                }
                            )
                        }
                ) {
                    previewSettingsButtonOffset?.let { (x, y) ->
                        Box(
                            Modifier
                                .absoluteOffset { IntOffset(x.toInt(), y.toInt()) }
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.3f), shape = CircleShape)
                        )
                    }
                }
            }
        }
        if (showTorrentPositionOverlay) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                Text(
                    text = "Нажмите в нужное место для кнопки",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    previewTorrentButtonOffset = offset.x to offset.y
                                },
                                onTap = { offset ->
                                    val screenWidth = size.width
                                    val screenHeight = size.height
                                    val endPx = screenWidth - offset.x
                                    val bottomPx = screenHeight - offset.y
                                    val endDp = with(density) { endPx.toDp().value }
                                    val bottomDp = with(density) { bottomPx.toDp().value }
                                    settingsManager.setTorrentButtonPadding(endDp, bottomDp)
                                    showTorrentPositionOverlay = false
                                    previewTorrentButtonOffset = null
                                }
                            )
                        }
                ) {
                    previewTorrentButtonOffset?.let { (x, y) ->
                        Box(
                            Modifier
                                .absoluteOffset { IntOffset(x.toInt(), y.toInt()) }
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.3f), shape = CircleShape)
                        )
                    }
                }
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
    var wasChecked by remember { mutableStateOf(false) }

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
                    wasChecked = true
                    coroutineScope.launch {
                        try {
                            val result = UpdateChecker.checkForUpdate(context)
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
                                        val result = UpdateChecker.checkForUpdate(context)
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
                                    val result = UpdateChecker.checkForUpdate(context)
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
            if (wasChecked && !updateAvailable && !isChecking && errorMessage.isEmpty()) {
                Text("У вас последняя версия", color = Color(0xFF4CAF50), fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Параметр 'Уведомлять о новой версии' (Row с Switch)
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
                onCheckedChange = onCheckedChange
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val downloadManager = remember {
        try {
            AppDownloadManager.getInstance(context)
        } catch (e: Exception) {
            null
        }
    }
    val activeDownloads by downloadManager?.activeDownloads?.collectAsState() ?: remember { mutableStateOf(emptyMap<Long, DownloadInfo>()) }
    val downloadHistory by downloadManager?.downloadHistory?.collectAsState() ?: remember { mutableStateOf(emptyList<DownloadInfo>()) }
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = SettingsManager.getInstance(context)
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        DownloadSettingsScreen(
            settingsManager = settingsManager,
            onBack = { showSettings = false },
            onClose = { showSettings = false }
        )
        return
    }

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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }
            Text(
                text = "Загрузки",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF2A2A2A), shape = MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки загрузок",
                    tint = Color.White
                )
            }
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
        // Контент
        if (activeDownloads.isNotEmpty() && downloadManager != null) {
            Text("Активные загрузки", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp))
            LazyColumn {
                items(activeDownloads.values.toList(), key = { it.id }) { download ->
                    DownloadCard(download, downloadManager, context, coroutineScope, true)
                }
            }
        }
        if (downloadHistory.isNotEmpty() && downloadManager != null) {
            Text("Завершённые загрузки", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
            LazyColumn {
                items(downloadHistory, key = { it.id }) { download ->
                    DownloadCard(download, downloadManager, context, coroutineScope, false)
                }
            }
        }
        if (activeDownloads.isEmpty() && downloadHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нет загрузок", color = Color.Gray)
            }
        }
    }
}

@Composable
fun DownloadCard(
    download: DownloadInfo,
    downloadManager: AppDownloadManager,
    context: Context,
    coroutineScope: CoroutineScope,
    isActive: Boolean
) {
    val settingsManager = SettingsManager.getInstance(context)
    val showSpeed by settingsManager.showDownloadSpeedFlow.collectAsState()
    val showTime by settingsManager.showRemainingTimeFlow.collectAsState()
    val progressMode by settingsManager.progressDisplayModeFlow.collectAsState()
    val showConfirmation by settingsManager.showDownloadConfirmationFlow.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogText by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<() -> Job>({ coroutineScope.launch { } }) }

    val (speed, setSpeed) = remember { mutableStateOf(0L) }
    val (lastDownloaded, setLastDownloaded) = remember { mutableStateOf(download.downloadedSize) }
    val (lastTime, setLastTime) = remember { mutableStateOf(System.currentTimeMillis()) }
    val (remainingTime, setRemainingTime) = remember { mutableStateOf(0L) }

    if (isActive) {
        LaunchedEffect(download.downloadedSize) {
            val now = System.currentTimeMillis()
            val deltaBytes = download.downloadedSize - lastDownloaded
            val deltaTime = now - lastTime
            if (deltaTime > 1000) {
                setSpeed(if (deltaTime > 0) (deltaBytes * 1000) / deltaTime else 0)
                setLastDownloaded(download.downloadedSize)
                setLastTime(now)
            }
            if (download.fileSize > 0 && speed > 0) {
                setRemainingTime(((download.fileSize - download.downloadedSize) / speed))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF2A2A2A),
            title = { 
                Text(
                    text = dialogTitle,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    text = dialogText,
                    color = Color.Gray,
                    fontSize = 14.sp
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmAction()
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Подтвердить")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Верхняя часть: Название и крестик
            Row(
                verticalAlignment = Alignment.Top // Выравниваем по верху для совпадения с текстом
            ) {
                // Извлекаем название и качество из имени файла
                val fullFileName = download.fileName
                val title = if (fullFileName.contains(" - ")) fullFileName.substringBeforeLast(" - ").trim() else fullFileName
                val qualityPart = if (fullFileName.contains(" - ")) fullFileName.substringAfterLast(" - ").trim() else ""
                val quality = qualityPart.replace(".mp4", "").replace(".mkv", "") + "p"

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (qualityPart.isNotEmpty()) {
                        Text(
                            text = quality,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (isActive) {
                    IconButton(
                        onClick = {
                            val action = { coroutineScope.launch { downloadManager.cancelDownload(download.id) } }
                            if (showConfirmation) {
                                dialogTitle = "Отмена загрузки"
                                dialogText = "Вы уверены, что хотите отменить загрузку файла?"
                                confirmAction = action
                                showDialog = true
                            } else {
                                action()
                            }
                        },
                        modifier = Modifier.size(32.dp).offset(y = (-4).dp) // Смещаем для выравнивания
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Отменить", tint = Color.Red.copy(alpha = 0.8f))
                    }
                }
            }

            // Нижняя часть: Прогресс и кнопки
            if (isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = if (download.fileSize > 0) download.downloadedSize.toFloat() / download.fileSize else 0f,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (progressMode == "PERCENT") {
                            "${((if (download.fileSize > 0) download.downloadedSize.toFloat() / download.fileSize else 0f) * 100).toInt()}%"
                        } else {
                            "${formatFileSize(download.downloadedSize)} / ${formatFileSize(download.fileSize)}"
                        },
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    Row {
                        if (showSpeed) {
                            Text(
                                text = "${if (speed > 0) formatFileSize(speed) else "0 KB"}/c",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (showTime && remainingTime > 0) {
                            Text(
                                text = " • ${formatTime(remainingTime)}",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else { // Завершенные или отмененные
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (download.status == DownloadStatus.COMPLETED) {
                         Button(
                            onClick = { downloadManager.openFile(download, context as? Activity) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Открыть", fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            text = when (download.status) {
                                DownloadStatus.FAILED -> "Ошибка"
                                DownloadStatus.CANCELLED -> "Отменено"
                                else -> "Статус неизвестен"
                            },
                            color = if (download.status == DownloadStatus.FAILED) Color.Red.copy(alpha = 0.8f) else Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        val action = { coroutineScope.launch { downloadManager.deleteFile(download) } }
                        if (showConfirmation) {
                            dialogTitle = "Удаление файла"
                            dialogText = "Вы уверены, что хотите удалить этот файл? Это действие необратимо."
                            confirmAction = action
                            showDialog = true
                        } else {
                            action()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

fun formatTime(seconds: Long): String {
    val s = seconds % 60
    val m = (seconds / 60) % 60
    val h = seconds / 3600
    return buildString {
        if (h > 0) append("$h ч ")
        if (m > 0) append("$m мин ")
        append("$s сек")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val showSpeed = settingsManager.showDownloadSpeedFlow.collectAsState()
    val showTime = settingsManager.showRemainingTimeFlow.collectAsState()
    val progressMode = settingsManager.progressDisplayModeFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
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
                text = "Настройки загрузок",
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
                SettingSwitch(
                    title = "Показывать скорость загрузки",
                    description = "Отображать текущую скорость загрузки файла",
                    checked = showSpeed.value,
                    onCheckedChange = { settingsManager.setShowDownloadSpeed(it) }
                )
            }
            item {
                SettingSwitch(
                    title = "Показывать время до конца",
                    description = "Отображать оставшееся время загрузки",
                    checked = showTime.value,
                    onCheckedChange = { settingsManager.setShowRemainingTime(it) }
                )
            }
            item {
                Text(
                    text = "Отображение прогресса",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = progressMode.value == "PERCENT",
                        onClick = { settingsManager.setProgressDisplayMode("PERCENT") },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50))
                    )
                    Text("В процентах", color = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = progressMode.value == "MB",
                        onClick = { settingsManager.setProgressDisplayMode("MB") },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50))
                    )
                    Text("В мегабайтах", color = Color.White)
                }
            }
            item {
                SettingSwitch(
                    title = "Предупреждения",
                    description = "Показывать диалог подтверждения при отмене или удалении загрузки",
                    checked = settingsManager.showDownloadConfirmationFlow.collectAsState().value,
                    onCheckedChange = { settingsManager.setShowDownloadConfirmation(it) }
                )
            }
        }
    }
} 

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentsSettingsScreen(onBack: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = SettingsManager.getInstance(context)
    val torrentsEnabled = settingsManager.torrentsEnabledFlow.collectAsState().value
    val externalTorrServeUrl = settingsManager.externalTorrServeUrlFlow.collectAsState().value
    val hideTorrentVipInfo = settingsManager.hideTorrentVipInfoFlow.collectAsState().value
    var urlInput by remember { mutableStateOf(externalTorrServeUrl.removeSuffix("/")) }
    var urlValid by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    fun validateUrl(url: String): Boolean {
        // Валидный http/https, ip, домен или localhost, обязательно порт, без слеша на конце
        val regex = Regex("^https?://((([a-zA-Z0-9\\-]+\\.)+[a-zA-Z]{2,})|(localhost)|((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)):(\\d{2,5})$")
        return regex.matches(url)
    }

    LaunchedEffect(urlInput) {
        urlValid = validateUrl(urlInput)
    }

    // Модальное окно для информации о VIP
    if (!hideTorrentVipInfo) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color(0xFF2F2F2F),
            title = {
                Text(
                    text = "Функции торрентов",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Функции связанные с торрентами не относятся к подписке ReYohoho VIP",
                    fontSize = 16.sp,
                    color = Color.White,
                    lineHeight = 24.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { settingsManager.hideTorrentVipInfo() }
                ) {
                    Text(
                        "Понятно, больше не показывать",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }
            Text(
                text = "Торренты",
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Переключатель активации торрентов
        SettingSwitch(
            title = "Активировать торренты",
            description = "Включить или выключить все функции с торрентами",
            checked = torrentsEnabled,
            onCheckedChange = { settingsManager.setTorrentsEnabled(!torrentsEnabled) }
        )
        
        if (torrentsEnabled) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Интеграция с TorrServe",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Переключатель режима сервера
            val serverMode = settingsManager.torrServerModeFlow.collectAsState().value
            val freeTorrEnabled = settingsManager.freeTorrEnabledFlow.collectAsState().value
            
            // Выбор режима сервера
            Text(
                text = "Режим торрент-сервера",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        settingsManager.setTorrServerMode(SettingsManager.TORR_SERVER_MODE_CUSTOM)
                        settingsManager.setFreeTorrEnabled(false)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serverMode == SettingsManager.TORR_SERVER_MODE_CUSTOM) 
                            Color(0xFF4CAF50) else Color(0xFF666666)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Свой сервер")
                }
                
                Button(
                    onClick = { 
                        settingsManager.setTorrServerMode(SettingsManager.TORR_SERVER_MODE_FREE_TORR)
                        settingsManager.setFreeTorrEnabled(true)
                        
                        // Запускаем поиск доступных серверов
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            try {
                                val torrServeManager = com.example.reyohoho.ui.TorrServeManager.getInstance(context)
                                val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    torrServeManager.initializeFreeTorrServer()
                                }
                                if (success) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "FreeTorr сервер найден и готов к работе",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Не удалось найти доступный FreeTorr сервер",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Ошибка при поиске сервера: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serverMode == SettingsManager.TORR_SERVER_MODE_FREE_TORR) 
                            Color(0xFF4CAF50) else Color(0xFF666666)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("FreeTorr")
                }
            }
            
            if (serverMode == SettingsManager.TORR_SERVER_MODE_CUSTOM) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "URL вашего TorrServe",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Используем BasicTextField для лучшего ввода URL
                BasicTextField(
                    value = urlInput,
                    onValueChange = { newValue ->
                        urlInput = newValue
                        checkResult = null
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF2A2A2A),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (urlValid) Color(0xFF4CAF50) else Color.Gray,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp)
                )
                
                // Сохраняем URL только при нажатии кнопки проверки
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (urlValid) {
                            settingsManager.setExternalTorrServeUrl(urlInput)
                            settingsManager.setUseInternalTorrServe(false)
                        }
                        checking = true
                        checkResult = null
                        kotlinx.coroutines.GlobalScope.launch {
                            try {
                                val available = com.example.reyohoho.ui.TorrServeManager.getInstance(context).checkTorrServeAvailable()
                                checking = false
                                checkResult = if (available) "Соединение успешно" else "Нет соединения"
                            } catch (e: Exception) {
                                checking = false
                                checkResult = "Ошибка: ${e.message}"
                            }
                        }
                    },
                    enabled = urlValid && !checking,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (checking) "Проверить и сохранить" else "Проверить и сохранить")
                }
                if (checkResult != null) {
                    Text(
                        text = checkResult ?: "",
                        color = if (checkResult == "Соединение успешно") Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Пример: http://localhost:8090, http://192.168.1.50:8090",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            if (serverMode == SettingsManager.TORR_SERVER_MODE_FREE_TORR) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Кнопка для ручной смены адреса
                var isChangingServer by remember { mutableStateOf(false) }
                
                // Отображение текущего сервера через StateFlow
                val currentServerUrl = settingsManager.freeTorrServerUrlFlow.collectAsState().value
                val currentServerDisplay = if (currentServerUrl != "http://localhost:8090/") {
                    currentServerUrl.replace("http://", "").replace(":8090", "").replace("/", "")
                } else {
                    "Не выбран"
                }
                
                // Отображение текущего сервера
                Text(
                    text = "Текущий сервер: $currentServerDisplay",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        isChangingServer = true
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            try {
                                val torrServeManager = com.example.reyohoho.ui.TorrServeManager.getInstance(context)
                                val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    torrServeManager.forceSwitchServer()
                                }
                                if (success) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Сервер успешно изменен",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Не удалось найти доступный сервер",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Ошибка при смене сервера: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isChangingServer = false
                            }
                        }
                    },
                    enabled = !isChangingServer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isChangingServer) "Поиск сервера..." else "Сменить адрес")
                }
            }
            

        }
    }
} 