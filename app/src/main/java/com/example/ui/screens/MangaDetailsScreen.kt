package com.example.ui.screens

import com.example.connector.StarzConnector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.MangaChapter
import com.example.model.MangaItem
import com.example.ui.MangaViewModel
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DemonGoldAccent
import com.example.ui.theme.DemonNightDark
import com.example.ui.theme.DemonPurpleSecondary
import com.example.ui.theme.DemonSurfaceCard
import com.example.ui.theme.DemonSurfaceElevated
import com.example.ui.theme.IrumaCyanLight
import com.example.ui.theme.IrumaCyanPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaDetailsScreen(
    manga: MangaItem,
    viewModel: MangaViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chapters by viewModel.mangaChapters.collectAsState()
    val isLoadingChapters by viewModel.isLoadingChapters.collectAsState()
    val sortAsc by viewModel.chaptersSortAscending.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val libraryManga by viewModel.libraryManga.collectAsState()

    val isInLibrary = libraryManga.any { it.id == manga.id }
    val currentMangaInLib = libraryManga.find { it.id == manga.id }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showRangeDownloadDialog by remember { mutableStateOf(false) }

    // Display chapters sorted
    val sortedChapters = remember(chapters, sortAsc) {
        if (sortAsc) chapters.sortedBy { it.chapterNumber } else chapters.sortedByDescending { it.chapterNumber }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DemonNightDark)
            .statusBarsPadding()
            .testTag("manga_details_screen")
    ) {
        // Top Back Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Color.White
                )
            }
            Text(
                text = manga.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.openWebview("https://mangadex.org/title/${manga.id}") }) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = "فتح في المتصفح",
                    tint = IrumaCyanPrimary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Hero / Manga Header Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(IrumaCyanPrimary.copy(alpha = 0.3f), DemonPurpleSecondary.copy(alpha = 0.3f))
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Cover Image
                            Box(
                                modifier = Modifier
                                    .width(105.dp)
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DemonSurfaceElevated)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(manga.coverUrl)
                                        .apply {
                                            if (StarzConnector.isStarzImageUrl(manga.coverUrl)) {
                                                StarzConnector.imageHeaders().forEach { (name, value) ->
                                                    addHeader(name, value)
                                                }
                                            }
                                        }
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = manga.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = manga.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    lineHeight = 22.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "المؤلف: ${manga.author.ifBlank { "غير معروف" }}",
                                    color = IrumaCyanLight,
                                    fontSize = 12.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (manga.status == "مستمر") Color(0xFF10B981).copy(alpha = 0.2f) else DemonPurpleSecondary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = manga.status,
                                            color = if (manga.status == "مستمر") Color(0xFF34D399) else Color(0xFFC084FC),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = manga.sourceName,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons (Library, Read, Range Download)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Library Button
                            Button(
                                onClick = {
                                    if (isInLibrary) {
                                        viewModel.toggleLibraryForSelectedManga()
                                    } else {
                                        showCategoryDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manga_library_toggle_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isInLibrary) DemonSurfaceElevated else IrumaCyanPrimary,
                                    contentColor = if (isInLibrary) IrumaCyanLight else Color(0xFF041824)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isInLibrary) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isInLibrary) "في المكتبة" else "إضافة للمكتبة",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Read First / Latest Chapter Button
                            Button(
                                onClick = {
                                    val target = chapters.minByOrNull { it.chapterNumber } ?: chapters.firstOrNull()
                                    if (target != null) viewModel.openReader(target)
                                },
                                enabled = chapters.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manga_start_reading_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DemonPurpleSecondary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ابدأ القراءة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Range Download Action Button
                        OutlinedButton(
                            onClick = { showRangeDownloadDialog = true },
                            enabled = chapters.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manga_range_download_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = IrumaCyanPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تنزيل نطاق فصول محدد للقراءة دون إنترنت", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Genres FlowRow
                        if (manga.genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                manga.genres.forEach { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = DemonSurfaceElevated
                                    ) {
                                        Text(
                                            text = genre,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Description
                        if (manga.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = manga.description,
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Chapters Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الفصول المتاحة (${chapters.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.toggleChaptersSort() }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "ترتيب",
                                tint = IrumaCyanPrimary
                            )
                        }
                        Text(
                            text = if (sortAsc) "تصاعدي (1 -> N)" else "تنازلي (N -> 1)",
                            color = IrumaCyanLight,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Chapters Loading or Empty or List
            if (isLoadingChapters) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = IrumaCyanPrimary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("جارٍ جلب الفصول الحقيقية من المصدر...", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else if (chapters.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا توجد فصول متوفرة حالياً لهذا العمل", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(sortedChapters, key = { it.id }) { chapter ->
                    ChapterListItem(
                        chapter = chapter,
                        onClick = { viewModel.openReader(chapter) },
                        onDownload = { viewModel.downloadChapter(chapter) }
                    )
                }
            }
        }
    }

    // Dialog: Category Assignment when adding to library
    if (showCategoryDialog) {
        val selectedCats = remember { mutableStateListOf<String>() }
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("اختر تصنيفات العمل", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("حدد التصنيفات التي تريد إدراج هذا العمل تحتها:", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedCats.contains(cat.id)) selectedCats.remove(cat.id)
                                    else selectedCats.add(cat.id)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedCats.contains(cat.id),
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectedCats.add(cat.id) else selectedCats.remove(cat.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = IrumaCyanPrimary,
                                    checkmarkColor = Color(0xFF041824)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(cat.name, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleLibraryForSelectedManga(selectedCats.toList())
                        showCategoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrumaCyanPrimary)
                ) {
                    Text("إضافة للمكتبة", color = Color(0xFF041824), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("إلغاء", color = TextMuted)
                }
            },
            containerColor = DemonSurfaceCard
        )
    }

    // Dialog: Range Download
    if (showRangeDownloadDialog) {
        var fromStr by remember { mutableStateOf("1") }
        var toStr by remember { mutableStateOf(chapters.size.toString()) }

        AlertDialog(
            onDismissRequest = { showRangeDownloadDialog = false },
            title = { Text("تنزيل فصول محددة", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "اختر نطاق الفصول أو استخدم الخيارات السريعة:",
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fromStr,
                            onValueChange = { fromStr = it },
                            label = { Text("من فصل") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        OutlinedTextField(
                            value = toStr,
                            onValueChange = { toStr = it },
                            label = { Text("إلى فصل") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.downloadNextUnread(5)
                                showRangeDownloadDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DemonSurfaceElevated)
                        ) {
                            Text("5 فصول تالية", fontSize = 11.sp, color = IrumaCyanLight)
                        }

                        Button(
                            onClick = {
                                viewModel.downloadNextUnread(10)
                                showRangeDownloadDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DemonSurfaceElevated)
                        ) {
                            Text("10 فصول تالية", fontSize = 11.sp, color = IrumaCyanLight)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val f = fromStr.toFloatOrNull() ?: 1f
                        val t = toStr.toFloatOrNull() ?: f
                        viewModel.downloadRange(f, t)
                        showRangeDownloadDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrumaCyanPrimary)
                ) {
                    Text("بدء التنزيل", color = Color(0xFF041824), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDownloadDialog = false }) {
                    Text("إلغاء", color = TextMuted)
                }
            },
            containerColor = DemonSurfaceCard
        )
    }
}

@Composable
fun ChapterListItem(
    chapter: MangaChapter,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("chapter_item_${chapter.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (chapter.isRead) DemonSurfaceCard.copy(alpha = 0.6f) else DemonSurfaceCard
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(CardBorder)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Read indicator
            if (chapter.isRead) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "تمت القراءة",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.displayTitle,
                    color = if (chapter.isRead) TextMuted else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chapter.scanlationGroup.isNotBlank()) {
                        Text(
                            text = chapter.scanlationGroup,
                            color = IrumaCyanLight,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (chapter.releaseDate.isNotBlank()) {
                        Text(
                            text = chapter.releaseDate,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Download Status / Button
            if (chapter.isDownloaded) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "مُنزل",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "تنزيل",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
