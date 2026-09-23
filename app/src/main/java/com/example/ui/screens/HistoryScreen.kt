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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MangaChapter
import com.example.ui.MangaViewModel
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DemonNightDark
import com.example.ui.theme.DemonSurfaceCard
import com.example.ui.theme.DemonSurfaceElevated
import com.example.ui.theme.IrumaCyanLight
import com.example.ui.theme.IrumaCyanPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    val historyList by viewModel.readingHistory.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DemonNightDark)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("history_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "سجل القراءة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "متابعة التقدم في الفصول المقروءة",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DemonSurfaceElevated
            ) {
                Text(
                    text = "${historyList.size} نشاط",
                    color = IrumaCyanLight,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = IrumaCyanPrimary,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "سجل القراءة فارغ",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "عندما تبدأ بقراءة أي فصل، سيتم حفظ موضع قراءتك تلقائياً لتستأنف من حيث توقفت.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyList, key = { it.chapterId }) { progress ->
                    val percent = if (progress.totalPages > 0) {
                        ((progress.lastPage + 1) * 100) / progress.totalPages
                    } else 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val dummyChapter = MangaChapter(
                                    id = progress.chapterId,
                                    mangaId = progress.mangaId,
                                    chapterNumber = 1f,
                                    title = progress.chapterTitle,
                                    lastReadPage = progress.lastPage
                                )
                                viewModel.openReader(dummyChapter)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(CardBorder)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = progress.mangaTitle,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = progress.chapterTitle,
                                        color = IrumaCyanLight,
                                        fontSize = 12.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = IrumaCyanPrimary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "$percent%",
                                        color = IrumaCyanPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "وصلت لصفحة ${progress.lastPage + 1} من ${progress.totalPages}",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )

                                val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                                    .format(Date(progress.timestamp))
                                Text(
                                    text = dateStr,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val fraction = if (progress.totalPages > 0) {
                                (progress.lastPage + 1).toFloat() / progress.totalPages.toFloat()
                            } else 0f

                            LinearProgressIndicator(
                                progress = { fraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = IrumaCyanPrimary,
                                trackColor = DemonSurfaceElevated
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val dummyChapter = MangaChapter(
                                        id = progress.chapterId,
                                        mangaId = progress.mangaId,
                                        chapterNumber = 1f,
                                        title = progress.chapterTitle,
                                        lastReadPage = progress.lastPage
                                    )
                                    viewModel.openReader(dummyChapter)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DemonSurfaceElevated,
                                    contentColor = IrumaCyanLight
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "استكمال القراءة",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
