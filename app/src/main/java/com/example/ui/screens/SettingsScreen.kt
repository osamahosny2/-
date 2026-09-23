package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ReadingMode
import com.example.model.SourceItem
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
fun SettingsScreen(
    viewModel: MangaViewModel,
    modifier: Modifier = Modifier
) {
    val readingMode by viewModel.readingMode.collectAsState()
    val currentSource by viewModel.selectedSource.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val sources by viewModel.sources.collectAsState()

    var showAddCatDialog by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }

    // Source Dialog States
    var showSourceDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SourceItem?>(null) }
    var sourceNameInput by remember { mutableStateOf("") }
    var sourceUrlInput by remember { mutableStateOf("") }
    var sourceDescInput by remember { mutableStateOf("") }
    var sourceRequiresVerificationInput by remember { mutableStateOf(false) }

    var sourceToDelete by remember { mutableStateOf<SourceItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DemonNightDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("settings_screen")
    ) {
        Text(
            text = "الإعدادات",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Reading Mode Section
        Text(
            text = "وضع القراءة المفضل",
            style = MaterialTheme.typography.titleMedium,
            color = IrumaCyanPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(CardBorder)
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ReadingMode.entries.forEach { mode ->
                    val isSelected = readingMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setReadingMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setReadingMode(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = IrumaCyanPrimary,
                                unselectedColor = TextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.titleAr,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = mode.descriptionAr,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Categories Management Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "إدارة تصنيفات المكتبة",
                style = MaterialTheme.typography.titleMedium,
                color = IrumaCyanPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Button(
                onClick = {
                    newCatName = ""
                    showAddCatDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = DemonSurfaceElevated),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = IrumaCyanPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة تصنيف", color = IrumaCyanLight, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(CardBorder)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (categories.isEmpty()) {
                    Text(
                        text = "لا توجد تصنيفات مخصصة حالياً. أضف تصنيفك الأول لتنظيم أعمالك.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = cat.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            IconButton(
                                onClick = { viewModel.deleteCategory(cat.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sources Management Section (Strict: Empty upon installation, user adds/edits/deletes!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "إدارة المصادر والمواقع",
                style = MaterialTheme.typography.titleMedium,
                color = IrumaCyanPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Button(
                onClick = {
                    editingSource = null
                    sourceNameInput = ""
                    sourceUrlInput = ""
                    sourceDescInput = ""
                    sourceRequiresVerificationInput = false
                    showSourceDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = DemonSurfaceElevated),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = IrumaCyanPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة مصدر", color = IrumaCyanLight, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(CardBorder)
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (sources.isEmpty()) {
                    Text(
                        text = "قائمة المصادر فارغة حالياً. اضغط على «إضافة مصدر» لإدخال اسم ورابط الموقع الذي تريد القراءة منه (مثل MangaDex أو أي موقع آخر).",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    sources.forEach { source ->
                        val isSelected = currentSource?.id == source.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setSource(source) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setSource(source) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = IrumaCyanPrimary,
                                    unselectedColor = TextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.name,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = source.baseUrl,
                                    color = IrumaCyanLight,
                                    fontSize = 11.sp
                                )
                                if (source.description.isNotBlank()) {
                                    Text(
                                        text = source.description,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            // Edit source button
                            IconButton(
                                onClick = {
                                    editingSource = source
                                    sourceNameInput = source.name
                                    sourceUrlInput = source.baseUrl
                                    sourceDescInput = source.description
                                    sourceRequiresVerificationInput = source.requiresVerification
                                    showSourceDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل المصدر",
                                    tint = IrumaCyanLight,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            // Delete source button
                            IconButton(
                                onClick = {
                                    sourceToDelete = source
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف المصدر",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // App Information
        Text(
            text = "حول التطبيق",
            style = MaterialTheme.typography.titleMedium,
            color = IrumaCyanPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DemonSurfaceCard),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(CardBorder)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Display the user-approved actual icon
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "أيقونة إيروما مانجا",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "إيروما مانجا",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تطبيق عربي حر لقراءة المانجا والمنهوا مع إضافة وإدارة المصادر المخصصة، التنزيل للقراءة بدون إنترنت، وتخصيص تصنيفات المكتبة.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }

    // Dialog: Add Category
    if (showAddCatDialog) {
        AlertDialog(
            onDismissRequest = { showAddCatDialog = false },
            title = { Text("إضافة تصنيف جديد", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    placeholder = { Text("مثال: مفضلة، مانها، أكشن...") },
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
                        if (newCatName.isNotBlank()) {
                            viewModel.addCategory(newCatName)
                            showAddCatDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrumaCyanPrimary)
                ) {
                    Text("إضافة", color = Color(0xFF041824), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCatDialog = false }) {
                    Text("إلغاء", color = TextMuted)
                }
            },
            containerColor = DemonSurfaceCard
        )
    }

    // Dialog: Add or Edit Source
    if (showSourceDialog) {
        val isEditing = editingSource != null
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = {
                Text(
                    text = if (isEditing) "تعديل المصدر" else "إضافة مصدر جديد",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sourceNameInput,
                        onValueChange = { sourceNameInput = it },
                        label = { Text("اسم المصدر") },
                        placeholder = { Text("مثال: MangaDex") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = sourceUrlInput,
                        onValueChange = { sourceUrlInput = it },
                        label = { Text("رابط الموقع / Base URL") },
                        placeholder = { Text("https://mangadex.org") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = sourceDescInput,
                        onValueChange = { sourceDescInput = it },
                        label = { Text("وصف المصدر (اختياري)") },
                        placeholder = { Text("مثال: ترجمات مانجا عربية") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sourceRequiresVerificationInput = !sourceRequiresVerificationInput }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = sourceRequiresVerificationInput,
                            onCheckedChange = { sourceRequiresVerificationInput = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = IrumaCyanPrimary,
                                uncheckedColor = TextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "يتطلب متصفح / حل كابتشا",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sourceNameInput.isNotBlank() && sourceUrlInput.isNotBlank()) {
                            if (isEditing) {
                                editingSource?.let { src ->
                                    viewModel.updateSource(
                                        id = src.id,
                                        name = sourceNameInput,
                                        baseUrl = sourceUrlInput,
                                        description = sourceDescInput,
                                        requiresVerification = sourceRequiresVerificationInput
                                    )
                                }
                            } else {
                                viewModel.addSource(
                                    name = sourceNameInput,
                                    baseUrl = sourceUrlInput,
                                    description = sourceDescInput,
                                    requiresVerification = sourceRequiresVerificationInput
                                )
                            }
                            showSourceDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrumaCyanPrimary)
                ) {
                    Text("حفظ", color = Color(0xFF041824), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false }) {
                    Text("إلغاء", color = TextMuted)
                }
            },
            containerColor = DemonSurfaceCard
        )
    }

    // Dialog: Delete Source Confirmation
    sourceToDelete?.let { src ->
        AlertDialog(
            onDismissRequest = { sourceToDelete = null },
            title = { Text("حذف المصدر", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف مصدر «${src.name}»؟ لن يتم جلب الأعمال منه بعد الحذف.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSource(src.id)
                        sourceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("تأكيد الحذف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sourceToDelete = null }) {
                    Text("إلغاء", color = TextMuted)
                }
            },
            containerColor = DemonSurfaceCard
        )
    }
}
