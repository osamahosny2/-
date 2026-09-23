package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MangaDetailsScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WebviewScreen
import com.example.ui.theme.DemonNightDark
import com.example.ui.theme.DemonSurfaceDark
import com.example.ui.theme.IrumaCyanPrimary
import com.example.ui.theme.TextMuted

@Composable
fun MainAppScreen(
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    val activeReaderChapter by viewModel.activeReaderChapter.collectAsState()
    val activeWebviewUrl by viewModel.activeWebviewUrl.collectAsState()
    val selectedManga by viewModel.selectedManga.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()

    // Wrap in Arabic RTL layout direction
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when {
            // 1. In-App Browser for Human Verification (Cloudflare / CAPTCHA)
            activeWebviewUrl != null -> {
                BackHandler {
                    viewModel.closeWebview()
                }
                WebviewScreen(
                    url = activeWebviewUrl!!,
                    onClose = { viewModel.closeWebview() }
                )
            }

            // 2. Fullscreen Manga Chapter Reader
            activeReaderChapter != null -> {
                BackHandler {
                    viewModel.closeReader()
                }
                ReaderScreen(
                    chapter = activeReaderChapter!!,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Manga Details & Chapters
            selectedManga != null -> {
                BackHandler {
                    viewModel.closeMangaDetails()
                }
                MangaDetailsScreen(
                    manga = selectedManga!!,
                    viewModel = viewModel,
                    onClose = { viewModel.closeMangaDetails() }
                )
            }

            // 4. Main App Tabs with Navigation Bar
            else -> {
                Scaffold(
                    modifier = modifier
                        .fillMaxSize()
                        .background(DemonNightDark),
                    containerColor = DemonNightDark,
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("bottom_nav_bar"),
                            containerColor = DemonSurfaceDark,
                            tonalElevation = 8.dp
                        ) {
                            // Library Tab
                            NavigationBarItem(
                                selected = currentTab == AppTab.LIBRARY,
                                onClick = { viewModel.selectTab(AppTab.LIBRARY) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.LIBRARY) Icons.Filled.CollectionsBookmark else Icons.Outlined.CollectionsBookmark,
                                        contentDescription = "المكتبة"
                                    )
                                },
                                label = {
                                    Text(
                                        text = "المكتبة",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentTab == AppTab.LIBRARY) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF041824),
                                    selectedTextColor = IrumaCyanPrimary,
                                    indicatorColor = IrumaCyanPrimary,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_tab_library")
                            )

                            // Browse & Sources Tab
                            NavigationBarItem(
                                selected = currentTab == AppTab.BROWSE,
                                onClick = { viewModel.selectTab(AppTab.BROWSE) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.BROWSE) Icons.Filled.Explore else Icons.Outlined.Explore,
                                        contentDescription = "المصادر"
                                    )
                                },
                                label = {
                                    Text(
                                        text = "المصادر",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentTab == AppTab.BROWSE) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF041824),
                                    selectedTextColor = IrumaCyanPrimary,
                                    indicatorColor = IrumaCyanPrimary,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_tab_browse")
                            )

                            // Downloads Tab
                            NavigationBarItem(
                                selected = currentTab == AppTab.DOWNLOADS,
                                onClick = { viewModel.selectTab(AppTab.DOWNLOADS) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.DOWNLOADS) Icons.Filled.Download else Icons.Outlined.Download,
                                        contentDescription = "التنزيلات"
                                    )
                                },
                                label = {
                                    Text(
                                        text = "التنزيلات",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentTab == AppTab.DOWNLOADS) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF041824),
                                    selectedTextColor = IrumaCyanPrimary,
                                    indicatorColor = IrumaCyanPrimary,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_tab_downloads")
                            )

                            // History Tab
                            NavigationBarItem(
                                selected = currentTab == AppTab.HISTORY,
                                onClick = { viewModel.selectTab(AppTab.HISTORY) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                                        contentDescription = "السجل"
                                    )
                                },
                                label = {
                                    Text(
                                        text = "السجل",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentTab == AppTab.HISTORY) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF041824),
                                    selectedTextColor = IrumaCyanPrimary,
                                    indicatorColor = IrumaCyanPrimary,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_tab_history")
                            )

                            // Settings Tab
                            NavigationBarItem(
                                selected = currentTab == AppTab.SETTINGS,
                                onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                        contentDescription = "الإعدادات"
                                    )
                                },
                                label = {
                                    Text(
                                        text = "الإعدادات",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentTab == AppTab.SETTINGS) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF041824),
                                    selectedTextColor = IrumaCyanPrimary,
                                    indicatorColor = IrumaCyanPrimary,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_tab_settings")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "tab_transition"
                        ) { targetTab ->
                            when (targetTab) {
                                AppTab.LIBRARY -> LibraryScreen(viewModel = viewModel)
                                AppTab.BROWSE -> BrowseScreen(viewModel = viewModel)
                                AppTab.DOWNLOADS -> DownloadsScreen(viewModel = viewModel)
                                AppTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                                AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
