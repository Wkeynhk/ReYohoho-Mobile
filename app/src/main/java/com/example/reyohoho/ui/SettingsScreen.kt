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

/**
 * Экран настроек приложения
 * Данная версия совместима с вызовом из MainActivity и заменяет SimpleSettingsScreen
 */
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onClose: () -> Unit,
    onRefreshPage: () -> Unit = {}
) {
    // Состояние для текущего экрана настроек
    var currentScreen by remember { mutableStateOf("main") }
    
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
        
        Text(
            text = "Версия приложения: 3.1",
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
    onBackPressed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
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
                color = Color.White
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Иконка приложения
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(100.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Название приложения
            Text(
                text = "ReYohoho",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Версия приложения
            Text(
                text = "Версия 3.03",
                fontSize = 16.sp,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Описание
            Text(
                text = "Приложение ReYohoho представляет собой Android-приложение, которое интегрирует в себя веб-сайт ReYohoho с функцией блокировки рекламы.",
                fontSize = 16.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Лицензия
            Text(
                text = "Лицензия: MIT",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
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