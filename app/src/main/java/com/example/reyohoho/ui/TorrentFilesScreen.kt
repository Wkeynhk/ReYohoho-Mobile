package com.example.reyohoho.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Экран выбора файлов из торрента для воспроизведения
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentFilesScreen(
    magnetUrl: String,
    torrentHash: String,
    onClose: () -> Unit,
    onFileSelected: (Int, String) -> Unit,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    val torrServeManager = remember { TorrServeManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var files by remember { mutableStateOf<List<TorrentFileUtils.TorrentFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var groupBySeason by remember { mutableStateOf(true) }

    // Загружаем список файлов при первом отображении
    LaunchedEffect(torrentHash) {
        coroutineScope.launch {
            try {
                isLoading = true
                error = null
                android.util.Log.d("TorrentFilesScreen", "Начинаем загрузку файлов для hash: $torrentHash")
                
                val rawFiles = torrServeManager.getTorrentFiles(torrentHash)
                android.util.Log.d("TorrentFilesScreen", "Получено сырых файлов: ${rawFiles.size}")
                
                if (rawFiles.isNotEmpty()) {
                    android.util.Log.d("TorrentFilesScreen", "Обрабатываем файлы...")
                    val processedFiles = TorrentFileUtils.processVideoFiles(rawFiles)
                    android.util.Log.d("TorrentFilesScreen", "Обработано видеофайлов: ${processedFiles.size}")
                    
                    if (processedFiles.isNotEmpty()) {
                        files = TorrentFileUtils.sortFilesByEpisode(processedFiles)
                        android.util.Log.d("TorrentFilesScreen", "Файлы отсортированы: ${files.size}")
                    } else {
                        android.util.Log.w("TorrentFilesScreen", "После обработки не осталось файлов")
                        error = "В торренте не найдено видеофайлов. Всего файлов: ${rawFiles.size}"
                    }
                } else {
                    android.util.Log.w("TorrentFilesScreen", "TorrServe не вернул файлы")
                    error = "Не удалось получить список файлов торрента"
                }
            } catch (e: Exception) {
                android.util.Log.e("TorrentFilesScreen", "Ошибка загрузки файлов", e)
                error = "Ошибка загрузки файлов: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Заголовок с кнопкой назад
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xAA222222), shape = RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Выбор файла для воспроизведения",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (files.isNotEmpty()) "${files.size} видеофайлов" else "Загрузка...",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp
                    )
                }

                // Переключатель группировки по сезонам
                if (files.isNotEmpty() && files.any { it.episodeInfo?.season != null }) {
                    Switch(
                        checked = groupBySeason,
                        onCheckedChange = { groupBySeason = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color(0xFF666666),
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Загрузка списка файлов...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
                
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Ошибка",
                                color = Color.Red,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error!!,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    error = null
                                    coroutineScope.launch {
                                        isLoading = true
                                        try {
                                            val rawFiles = torrServeManager.getTorrentFiles(torrentHash)
                                            files = TorrentFileUtils.processVideoFiles(rawFiles)
                                        } catch (e: Exception) {
                                            error = "Ошибка загрузки файлов: ${e.message}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Повторить")
                            }
                        }
                    }
                }
                
                files.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Видеофайлы не найдены",
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "В торренте отсутствуют поддерживаемые видеофайлы",
                                color = Color(0xFFAAAAAA),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                
                else -> {
                    // Список файлов
                    if (groupBySeason && files.any { it.episodeInfo?.season != null }) {
                        // Группированный по сезонам список
                        val groupedFiles = TorrentFileUtils.groupFilesBySeason(files)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            groupedFiles.forEach { (season, seasonFiles) ->
                                item {
                                    // Заголовок сезона
                                    Text(
                                        text = if (season != null) "Сезон $season" else "Без определения сезона",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                
                                items(seasonFiles) { file ->
                                    FileItem(
                                        file = file,
                                        onClick = { onFileSelected(file.index, file.name) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Обычный список
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(files) { file ->
                                FileItem(
                                    file = file,
                                    onClick = { onFileSelected(file.index, file.name) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItem(
    file: TorrentFileUtils.TorrentFile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка видеофайла
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color(0xFF4CAF50),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Информация о файле
            Column(modifier = Modifier.weight(1f)) {
                // Номер серии (если есть)
                TorrentFileUtils.getEpisodeNumber(file)?.let { episodeNumber ->
                    Text(
                        text = episodeNumber,
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                
                // Название файла
                Text(
                    text = TorrentFileUtils.formatEpisodeTitle(file),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = TorrentFileUtils.formatFileSize(file.size),
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp
                    )
                    
                    file.episodeInfo?.quality?.let { quality ->
                        Box(
                            modifier = Modifier
                                .background(
                                    Color(0xFF333333),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = quality,
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Стрелка
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}