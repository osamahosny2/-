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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.MangaCategory
import com.example.model.MangaItem
import com.example.ui.AppTab
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

@Composable
fun LibraryScreen(
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    val libraryManga by viewModel.filteredLibrary.collectAsState()
    val allLibraryManga by viewModel.libraryManga.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategoryId.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<MangaCategory?>(null) }
    var newCategoryName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DemonNightDark)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("library_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "المكتبة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "أعمال المانجا والمنهوا المحفوظة",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = IrumaCyanPrimary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${allLibraryManga.size} عمل",
                    color = IrumaCyanPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic User Categories Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "All" Chip
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("الكل (${allLibraryManga.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IrumaCyanPrimary,
                        selectedLabelColor = Color(0xFF041824),
                        containerColor = DemonSurfaceElevated,
                        labelColor = TextSecondary
                    )
                )
            }

            // User-created categories
            items(categories, key = { it.id }) { cat ->
                val count = allLibraryManga.count { it.categoryIds.contains(cat.id) }
                val isSelected = selectedCategory == cat.id

                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectCategory(cat.id) },
                    label = { Text("${cat.name} ($count)", fontSize = 12.sp) },
                    trailingIcon = {
                        if (isSelected) {
                            IconButton(
                                onClick = {
                                    editingCategory = cat
                                    newCategoryName = cat.name
                                },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل",
                                    tint = Color(0xFF041824)
                                )
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IrumaCyanPrimary,
                        selectedLabelColor = Color(0xFF041824),
                        containerColor = DemonSurfaceElevated,
                        labelColor = TextSecondary
                    )
                )
            }

            // Add Category Button
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DemonSurfaceElevated,
                    modifier = Modifier.clickable {
                        newCategoryName = ""
                        showAddCategoryDialog = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = IrumaCyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "تصنيف جديد",
                            color = IrumaCyanLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Library Content / Empty State
        if (allLibraryManga.isEmpty()) {
            // STRICT REQUIREMENT: Library must start empty with clear guidance to browse/add manga!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(IrumaCyanPrimary.copy(alpha = 0.5f), DemonPurpleSecondary.copy(alpha = 0.5f))
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            tint = IrumaCyanPrimary,
                            modifier = Modifier.size(56.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "المكتبة فارغة",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "لم تقم بإضافة أي أعمال إلى مكتبتك بعد.\nتصفح المصادر الحقيقية أو ابحث عن مانجا/منهوا لإضافتها إلى مكتبتك ومتابعة فصولها وتنزيلها.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.selectTab(AppTab.BROWSE) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("library_explore_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IrumaCyanPrimary,
                                contentColor = Color(0xFF041824)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "استكشاف المصادر والأعمال",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        } else if (libraryManga.isEmpty()) {
            // Category has no manga
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد أعمال في هذا التصنيف حالياً",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            // Manga Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 105.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(libraryManga, key = { it.id }) { manga ->
                    MangaGridCard(
                        manga = manga,
                        onClick = { viewModel.openMangaDetails(manga) }
                    )
                }
            }
        }
    }

    // Dialog: Add New Custom Category
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("إنشاء تصنيف جديد", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("مثال: مفضلة، قيد القراءة، منهوا...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(newCategoryName)
                            showAddCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrumaCyanPrimary)
                ) {
                    Text("إضافة", color = Color(0xFF041824), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("إلغاء", color = TextMuted)
                }
            },
            containerColor = DemonSurfaceCard
        )
    }

    // Dialog: Edit / Delete Custom Category
    editingCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("تعديل التصنيف", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            viewModel.deleteCategory(cat.id)
                            editingCategory = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حذف التصنيف", color = Color.White)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.updateCategory(cat.id, newCategoryName)
                            editingCategory = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrumaCyanPrimary)
                ) {
                    Text("حفظ التعديل", color = Color(0xFF041824), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("إلغاء", color = TextMuted)
                }
            },
            containerColor = DemonSurfaceCard
        )
    }
}

@Composable
fun MangaGridCard(
    manga: MangaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("manga_card_${manga.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(CardBorder)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(DemonSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (manga.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(manga.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = manga.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = manga.author.ifBlank { manga.sourceName },
                    color = IrumaCyanLight,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
