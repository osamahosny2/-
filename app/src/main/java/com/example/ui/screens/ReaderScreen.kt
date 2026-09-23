package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.MangaChapter
import com.example.model.MangaPage
import com.example.model.ReadingMode
import com.example.ui.MangaViewModel
import com.example.ui.theme.DemonGoldAccent
import com.example.ui.theme.DemonNightDark
import com.example.ui.theme.DemonPurpleSecondary
import com.example.ui.theme.DemonSurfaceCard
import com.example.ui.theme.DemonSurfaceDark
import com.example.ui.theme.DemonSurfaceElevated
import com.example.ui.theme.IrumaCyanLight
import com.example.ui.theme.IrumaCyanPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    chapter: MangaChapter,
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    val pages by viewModel.readerPages.collectAsState()
    val isReaderLoading by viewModel.isReaderLoading.collectAsState()
    val readingMode by viewModel.readingMode.collectAsState()
    val scope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var showModeMenu by remember { mutableStateOf(false) }

    val totalPages = pages.size

    // Initial page position
    val initialPage = remember(chapter.id) {
        chapter.lastReadPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("reader_screen_container")
    ) {
        if (isReaderLoading) {
            // Loading State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DemonNightDark),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = IrumaCyanPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "جارٍ تحميل صفحات ${chapter.displayTitle}...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "محرك الاتصال يجهز الصور بأعلى دقة",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        } else if (pages.isEmpty()) {
            // Empty / Error State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DemonNightDark),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "تعذر تحميل صفحات هذا الفصل",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                IconButton(onClick = { viewModel.openReader(chapter) }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "إعادة المحاولة",
                        tint = IrumaCyanPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        } else {
            // Main Reader Content based on ReadingMode
            when (readingMode) {
                ReadingMode.WEBTOON -> {
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

                    // Track scroll position to update progress
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .distinctUntilChanged()
                            .collect { index ->
                                viewModel.onPageChanged(index)
                            }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    showControls = !showControls
                                }
                            }
                    ) {
                        itemsIndexed(pages, key = { _, page -> "${chapter.id}_${page.pageNumber}" }) { index, page ->
                            MangaPageView(
                                page = page,
                                totalPages = totalPages,
                                isWebtoon = true
                            )
                        }
                    }
                }

                ReadingMode.RTL_PAGING, ReadingMode.LTR_PAGING -> {
                    val pagerState = rememberPagerState(
                        initialPage = initialPage,
                        pageCount = { totalPages }
                    )

                    // Track page changes
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }
                            .distinctUntilChanged()
                            .collect { index ->
                                viewModel.onPageChanged(index)
                            }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    showControls = !showControls
                                }
                            },
                        reverseLayout = (readingMode == ReadingMode.RTL_PAGING)
                    ) { pageIndex ->
                        val page = pages.getOrNull(pageIndex)
                        if (page != null) {
                            MangaPageView(
                                page = page,
                                totalPages = totalPages,
                                isWebtoon = false
                            )
                        }
                    }
                }
            }
        }

        // Top Overlay Header
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.closeReader() },
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = chapter.displayTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "مايريماشيتا! إيروما-كن • ${readingMode.titleAr}",
                            color = IrumaCyanLight,
                            fontSize = 11.sp
                        )
                    }

                    // Reading Mode Quick Selector
                    Box {
                        IconButton(onClick = { showModeMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "وضع القراءة",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = { showModeMenu = false },
                            modifier = Modifier.background(DemonSurfaceElevated)
                        ) {
                            ReadingMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mode.titleAr,
                                            color = if (mode == readingMode) IrumaCyanPrimary else Color.White,
                                            fontWeight = if (mode == readingMode) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        viewModel.setReadingMode(mode)
                                        showModeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Refresh chapter pages
                    IconButton(onClick = { viewModel.openReader(chapter) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "إعادة تحميل الصفحات",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Bottom Overlay Footer
        val currentPageIndex by viewModel.currentPageIndex.collectAsState()

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Page indicator pill & slider
                    if (totalPages > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "صفحة ${currentPageIndex + 1}",
                                color = IrumaCyanLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.width(60.dp)
                            )

                            Slider(
                                value = currentPageIndex.toFloat(),
                                onValueChange = { viewModel.onPageChanged(it.toInt()) },
                                valueRange = 0f..(totalPages - 1).toFloat(),
                                steps = if (totalPages > 2) totalPages - 2 else 0,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = IrumaCyanPrimary,
                                    activeTrackColor = IrumaCyanPrimary,
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )

                            Text(
                                text = "$totalPages صفحة",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(60.dp)
                            )
                        }
                    }

                    // Next / Previous Chapter buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DemonSurfaceElevated,
                            modifier = Modifier.clickable { viewModel.prevChapter() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "السابق",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "الفصل السابق",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Floating Page Pill in the center
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IrumaCyanPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${currentPageIndex + 1} / $totalPages",
                                color = IrumaCyanPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IrumaCyanPrimary,
                            modifier = Modifier.clickable { viewModel.nextChapter() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "الفصل التالي",
                                    color = Color(0xFF041824),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "التالي",
                                    tint = Color(0xFF041824),
                                    modifier = Modifier.size(16.dp)
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
private fun MangaPageView(
    page: MangaPage,
    totalPages: Int,
    isWebtoon: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isImageLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isWebtoon) Modifier.fillMaxSize() else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (isImageLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isWebtoon) 550.dp else 650.dp)
                    .background(Color(0xFF0D0F18)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = IrumaCyanPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "صفحة ${page.pageNumber}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (hasError) {
            // Elegant fallback stylized manga page
            StylizedMangaPageFallback(
                pageNumber = page.pageNumber,
                totalPages = totalPages,
                isWebtoon = isWebtoon
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(page.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "صفحة المانجا ${page.pageNumber}",
                contentScale = if (isWebtoon) ContentScale.FillWidth else ContentScale.Fit,
                onLoading = {
                    isImageLoading = true
                    hasError = false
                },
                onSuccess = {
                    isImageLoading = false
                    hasError = false
                },
                onError = {
                    isImageLoading = false
                    hasError = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!isWebtoon) Modifier.fillMaxSize() else Modifier)
            )
        }
    }
}

@Composable
private fun StylizedMangaPageFallback(
    pageNumber: Int,
    totalPages: Int,
    isWebtoon: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isWebtoon) 380.dp else 500.dp)
            .background(DemonSurfaceElevated)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = IrumaCyanPrimary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "تعذر تحميل صفحة $pageNumber",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "يرجى التحقق من اتصال الإنترنت أو المصدر",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}
