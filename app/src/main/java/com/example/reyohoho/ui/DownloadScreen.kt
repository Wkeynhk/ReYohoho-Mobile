package com.example.reyohoho.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reyohoho.AppDownloadManager
import com.example.reyohoho.DownloadInfo
import com.example.reyohoho.DownloadStatus
import com.example.reyohoho.ui.formatFileSize
import com.example.reyohoho.ui.formatDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

/**
 * Экран загрузок для отображения в настройках
 */
@Composable
fun DownloadScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val downloadManager = remember { 
        try {
            AppDownloadManager.getInstance(context)
        } catch (e: Exception) {
            Log.e("DownloadScreen", "Ошибка инициализации AppDownloadManager: ${e.message}")
            null
        }
    }
    val activity = context as? Activity
    
    // Если менеджер загрузок не инициализирован, показываем сообщение об ошибке
    if (downloadManager == null) {
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
                    .padding(top = 36.dp),
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
                    text = "Загрузки",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Не удалось загрузить данные о загрузках.\nПопробуйте перезапустить приложение.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }
    
    // Получаем списки загрузок
    val activeDownloads by downloadManager.activeDownloads.collectAsState()
    val downloadHistory by downloadManager.downloadHistory.collectAsState()
    
    // Для обновления прогресса скачивания
    val coroutineScope = rememberCoroutineScope()
    
    // Периодическое обновление прогресса загрузки
    LaunchedEffect(Unit) {
        while (true) {
            activeDownloads.keys.forEach { downloadId ->
                downloadManager.updateDownloadProgress(downloadId)
            }
            delay(1000) // Обновляем раз в секунду
        }
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
                .padding(top = 36.dp),
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
                text = "Загрузки",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Активные загрузки
        if (activeDownloads.isNotEmpty()) {
            Text(
                text = "Активные загрузки",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
            ) {
                items(activeDownloads.values.toList()) { download ->
                    ActiveDownloadItem(
                        downloadInfo = download,
                        onCancel = {
                            coroutineScope.launch {
                                downloadManager.cancelDownload(download.id)
                            }
                        }
                    )
                }
            }
            
            HorizontalDivider(
                color = Color.Gray,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        // История загрузок
        Text(
            text = "История загрузок",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        if (downloadHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет загруженных файлов",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(downloadHistory) { download ->
                    HistoryDownloadItem(
                        downloadInfo = download,
                        onOpen = {
                            downloadManager.openFile(download, activity)
                        },
                        onDelete = {
                            coroutineScope.launch {
                                downloadManager.deleteFile(download)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadItem(downloadInfo: DownloadInfo, onCancel: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Название файла
                Text(
                    text = downloadInfo.fileName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // Кнопка отмены
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Отменить",
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Прогресс загрузки
            val progress = if (downloadInfo.fileSize > 0) {
                downloadInfo.downloadedSize.toFloat() / downloadInfo.fileSize
            } else {
                0f
            }
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFF2E2E2E)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Информация о размере
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatFileSize(downloadInfo.downloadedSize),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                
                Text(
                    text = formatFileSize(downloadInfo.fileSize),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun HistoryDownloadItem(
    downloadInfo: DownloadInfo,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка типа файла
            Icon(
                imageVector = getFileIcon(downloadInfo.mimeType),
                contentDescription = null,
                tint = getFileColor(downloadInfo.mimeType),
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Информация о файле
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = downloadInfo.fileName,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Статус загрузки
                    val statusColor = when (downloadInfo.status) {
                        DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                        DownloadStatus.FAILED -> Color(0xFFF44336)
                        DownloadStatus.CANCELLED -> Color(0xFFFF9800)
                        else -> Color.Gray
                    }
                    
                    val statusText = when (downloadInfo.status) {
                        DownloadStatus.COMPLETED -> "Завершено"
                        DownloadStatus.FAILED -> "Ошибка"
                        DownloadStatus.CANCELLED -> "Отменено"
                        else -> "В процессе"
                    }
                    
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp
                    )
                    
                    Text(
                        text = " • ${formatFileSize(downloadInfo.fileSize)} • ",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    
                    Text(
                        text = formatDate(downloadInfo.timestamp),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            
            // Кнопка меню
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню",
                        tint = Color.White
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF2A2A2A))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Открыть",
                                color = Color.White
                            )
                        },
                        onClick = {
                            onOpen()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    )
                    
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Удалить",
                                color = Color.White
                            )
                        },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFF44336)
                            )
                        }
                    )
                }
            }
        }
    }
}

// Получение иконки файла в зависимости от типа
@Composable
fun getFileIcon(mimeType: String) = when {
    mimeType.startsWith("video/") -> Icons.Default.Star
    mimeType.startsWith("image/") -> Icons.Default.Face
    mimeType.startsWith("audio/") -> Icons.Default.Email
    else -> Icons.Default.Info
}

// Получение цвета для иконки файла
fun getFileColor(mimeType: String) = when {
    mimeType.startsWith("video/") -> Color(0xFF2196F3)
    mimeType.startsWith("image/") -> Color(0xFF4CAF50)
    mimeType.startsWith("audio/") -> Color(0xFFFF9800)
    else -> Color(0xFF9E9E9E)
} 