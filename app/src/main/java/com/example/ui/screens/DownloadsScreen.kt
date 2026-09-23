package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DownloadStatus
import com.example.model.DownloadTask
import com.example.ui.MangaViewModel
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DemonNightDark
import com.example.ui.theme.DemonSurfaceCard
import com.example.ui.theme.DemonSurfaceElevated
import com.example.ui.theme.IrumaCyanLight
import com.example.ui.theme.IrumaCyanPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun DownloadsScreen(
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    val downloadQueue by viewModel.downloadQueue.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DemonNightDark)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("downloads_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "التنزيلات",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "إدارة الفصول المنزلة للقراءة دون اتصال بالإنترنت",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DemonSurfaceElevated
            ) {
                Text(
                    text = "${downloadQueue.count { it.status == DownloadStatus.COMPLETED }} فصلاً جاهزاً",
                    color = IrumaCyanLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (downloadQueue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لا توجد تنزيلات حالية",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "يمكنك تنزيل الفصول الفردية أو نطاق محدد من صفحة العمل لقراءتها أينما كنت دون إنترنت.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloadQueue, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onDelete = { viewModel.deleteDownloadedChapter(task.mangaId, task.chapterId) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(CardBorder)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Icon
                when (task.status) {
                    DownloadStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "مكتمل",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "خطأ",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DownloadStatus.DOWNLOADING -> {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "جارٍ التنزيل",
                            tint = IrumaCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "في الانتظار",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.mangaTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = task.chapterTitle,
                        color = IrumaCyanLight,
                        fontSize = 12.sp
                    )
                }

                // Action / Delete
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PENDING) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = IrumaCyanPrimary,
                    trackColor = DemonSurfaceElevated
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (task.totalPages > 0) "الصفحة ${task.downloadedPages} من ${task.totalPages}" else "جارٍ التحضير...",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${(task.progress * 100).toInt()}%",
                        color = IrumaCyanLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (task.status == DownloadStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "جاهز للقراءة في وضع عدم الاتصال",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${task.totalPages} صفحة",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            } else if (task.status == DownloadStatus.FAILED) {
                Text(
                    text = task.errorMsg ?: "فشل التنزيل",
                    color = Color(0xFFF87171),
                    fontSize = 11.sp
                )
            }
        }
    }
}
