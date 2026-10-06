package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.connector.ConnectorEngine
import com.example.model.DownloadStatus
import com.example.model.DownloadTask
import com.example.model.MangaCategory
import com.example.model.MangaChapter
import com.example.model.MangaItem
import com.example.model.MangaPage
import com.example.model.ReadingMode
import com.example.model.ReadingProgress
import com.example.model.SourceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MangaRepository(private val context: Context) {

    val connector = ConnectorEngine()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("iruma_manga_prefs_v2", Context.MODE_PRIVATE)

    // 1. Library State (Starts completely EMPTY by default!)
    private val _library = MutableStateFlow<List<MangaItem>>(emptyList())
    val library: StateFlow<List<MangaItem>> = _library.asStateFlow()

    // 2. User-defined Sources (Starts completely EMPTY upon install - Zero default sources!)
    private val _sources = MutableStateFlow<List<SourceItem>>(emptyList())
    val sources: StateFlow<List<SourceItem>> = _sources.asStateFlow()

    // 3. User-defined Custom Categories (Dynamic & Editable by user)
    private val _categories = MutableStateFlow<List<MangaCategory>>(emptyList())
    val categories: StateFlow<List<MangaCategory>> = _categories.asStateFlow()

    // 3. Download Tasks Queue
    private val _downloadQueue = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadQueue: StateFlow<List<DownloadTask>> = _downloadQueue.asStateFlow()

    // 4. Reading History
    private val _history = MutableStateFlow<List<ReadingProgress>>(emptyList())
    val history: StateFlow<List<ReadingProgress>> = _history.asStateFlow()

    // 5. Reading Mode Preference
    private val _readingMode = MutableStateFlow(ReadingMode.WEBTOON)
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    // Download storage directory
    private val downloadsDir: File
        get() = File(context.filesDir, "manga_downloads").apply { if (!exists()) mkdirs() }

    init {
        loadDataFromStorage()
    }

    private fun loadDataFromStorage() {
        // Load User Sources (STARTS COMPLETELY EMPTY UPON INSTALL! NO DEFAULT SOURCES)
        val sourcesJson = prefs.getString("user_sources_v3", null)
        if (!sourcesJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(sourcesJson)
                val list = mutableListOf<SourceItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SourceItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            baseUrl = obj.getString("baseUrl"),
                            lang = obj.optString("lang", "ar"),
                            description = obj.optString("description", ""),
                            requiresVerification = obj.optBoolean("requiresVerification", false)
                        )
                    )
                }
                _sources.value = list
            } catch (ignored: Exception) {}
        } else {
            _sources.value = emptyList() // Strict: App starts empty!
        }

        // Load Categories
        val catsJson = prefs.getString("user_categories", null)
        if (!catsJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(catsJson)
                val list = mutableListOf<MangaCategory>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        MangaCategory(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            order = obj.optInt("order", i)
                        )
                    )
                }
                _categories.value = list
            } catch (ignored: Exception) {}
        } else {
            // Default initial user categories which user can freely rename or delete
            _categories.value = listOf(
                MangaCategory(id = "cat_reading", name = "قيد القراءة", order = 0),
                MangaCategory(id = "cat_fav", name = "المفضلة", order = 1),
                MangaCategory(id = "cat_plan", name = "سأقرأها لاحقاً", order = 2)
            )
            saveCategoriesToStorage()
        }

        // Load Library (STARTS EMPTY if user hasn't added any yet!)
        val libJson = prefs.getString("library_items", null)
        if (!libJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(libJson)
                val list = mutableListOf<MangaItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val catArr = obj.optJSONArray("categoryIds") ?: JSONArray()
                    val catList = mutableListOf<String>()
                    for (c in 0 until catArr.length()) catList.add(catArr.getString(c))

                    val genresArr = obj.optJSONArray("genres") ?: JSONArray()
                    val genresList = mutableListOf<String>()
                    for (g in 0 until genresArr.length()) genresList.add(genresArr.getString(g))

                    list.add(
                        MangaItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            coverUrl = obj.optString("coverUrl", ""),
                            author = obj.optString("author", ""),
                            status = obj.optString("status", ""),
                            description = obj.optString("description", ""),
                            genres = genresList,
                            sourceId = obj.optString("sourceId", "mangadex"),
                            sourceName = obj.optString("sourceName", "MangaDex"),
                            inLibrary = true,
                            categoryIds = catList,
                            addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                        )
                    )
                }
                _library.value = list
            } catch (ignored: Exception) {}
        }

        // Load Reading Mode
        val modeStr = prefs.getString("reading_mode", ReadingMode.WEBTOON.name)
        _readingMode.value = try {
            ReadingMode.valueOf(modeStr ?: ReadingMode.WEBTOON.name)
        } catch (e: Exception) {
            ReadingMode.WEBTOON
        }

        // Load History
        val histJson = prefs.getString("reading_history", null)
        if (!histJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(histJson)
                val list = mutableListOf<ReadingProgress>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        ReadingProgress(
                            mangaId = obj.getString("mangaId"),
                            chapterId = obj.getString("chapterId"),
                            mangaTitle = obj.getString("mangaTitle"),
                            chapterTitle = obj.getString("chapterTitle"),
                            lastPage = obj.getInt("lastPage"),
                            totalPages = obj.getInt("totalPages"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                _history.value = list
            } catch (ignored: Exception) {}
        }
    }

    // --- Library Operations ---

    fun addToLibrary(manga: MangaItem, selectedCategoryIds: List<String> = emptyList()) {
        val current = _library.value.toMutableList()
        current.removeAll { it.id == manga.id }
        current.add(0, manga.copy(inLibrary = true, categoryIds = selectedCategoryIds, addedAt = System.currentTimeMillis()))
        _library.value = current
        saveLibraryToStorage()
    }

    fun removeFromLibrary(mangaId: String) {
        val current = _library.value.toMutableList()
        current.removeAll { it.id == mangaId }
        _library.value = current
        saveLibraryToStorage()
    }

    fun updateMangaCategories(mangaId: String, categoryIds: List<String>) {
        val current = _library.value.toMutableList()
        val index = current.indexOfFirst { it.id == mangaId }
        if (index != -1) {
            current[index] = current[index].copy(categoryIds = categoryIds)
            _library.value = current
            saveLibraryToStorage()
        }
    }

    fun isMangaInLibrary(mangaId: String): Boolean {
        return _library.value.any { it.id == mangaId }
    }

    private fun saveLibraryToStorage() {
        val arr = JSONArray()
        for (item in _library.value) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("coverUrl", item.coverUrl)
                put("author", item.author)
                put("status", item.status)
                put("description", item.description)
                put("sourceId", item.sourceId)
                put("sourceName", item.sourceName)
                put("addedAt", item.addedAt)
                put("categoryIds", JSONArray(item.categoryIds))
                put("genres", JSONArray(item.genres))
            }
            arr.put(obj)
        }
        prefs.edit().putString("library_items", arr.toString()).apply()
    }

    // --- Category Management Operations ---

    fun addCategory(name: String): MangaCategory {
        val newCat = MangaCategory(
            id = "cat_" + UUID.randomUUID().toString().take(8),
            name = name.trim(),
            order = _categories.value.size
        )
        val updated = _categories.value + newCat
        _categories.value = updated
        saveCategoriesToStorage()
        return newCat
    }

    fun updateCategory(id: String, newName: String) {
        val updated = _categories.value.map {
            if (it.id == id) it.copy(name = newName.trim()) else it
        }
        _categories.value = updated
        saveCategoriesToStorage()
    }

    fun deleteCategory(id: String) {
        val updated = _categories.value.filterNot { it.id == id }
        _categories.value = updated
        // Remove this category from all library manga
        val updatedLib = _library.value.map { manga ->
            if (manga.categoryIds.contains(id)) {
                manga.copy(categoryIds = manga.categoryIds.filterNot { it == id })
            } else manga
        }
        _library.value = updatedLib
        saveCategoriesToStorage()
        saveLibraryToStorage()
    }

    private fun saveCategoriesToStorage() {
        val arr = JSONArray()
        for (cat in _categories.value) {
            val obj = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("order", cat.order)
            }
            arr.put(obj)
        }
        prefs.edit().putString("user_categories", arr.toString()).apply()
    }

    // --- Source Management Operations (User-added from Settings) ---

    fun addSource(
        name: String,
        baseUrl: String,
        description: String = "",
        requiresVerification: Boolean = false
    ): SourceItem {
        var cleanUrl = baseUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        val newSource = SourceItem(
            id = "src_" + UUID.randomUUID().toString().take(8),
            name = name.trim(),
            baseUrl = cleanUrl,
            lang = "ar",
            description = description.trim(),
            requiresVerification = requiresVerification
        )
        val updated = _sources.value + newSource
        _sources.value = updated
        saveSourcesToStorage()
        return newSource
    }

    fun updateSource(
        id: String,
        name: String,
        baseUrl: String,
        description: String = "",
        requiresVerification: Boolean = false
    ) {
        var cleanUrl = baseUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        val updated = _sources.value.map {
            if (it.id == id) {
                it.copy(
                    name = name.trim(),
                    baseUrl = cleanUrl,
                    description = description.trim(),
                    requiresVerification = requiresVerification
                )
            } else it
        }
        _sources.value = updated
        saveSourcesToStorage()
    }

    fun deleteSource(id: String) {
        val updated = _sources.value.filterNot { it.id == id }
        _sources.value = updated
        saveSourcesToStorage()
    }

    private fun saveSourcesToStorage() {
        val arr = JSONArray()
        for (src in _sources.value) {
            val obj = JSONObject().apply {
                put("id", src.id)
                put("name", src.name)
                put("baseUrl", src.baseUrl)
                put("lang", src.lang)
                put("description", src.description)
                put("requiresVerification", src.requiresVerification)
            }
            arr.put(obj)
        }
        prefs.edit().putString("user_sources_v3", arr.toString()).apply()
    }

    // --- Reading Mode Preference ---

    fun setReadingMode(mode: ReadingMode) {
        _readingMode.value = mode
        prefs.edit().putString("reading_mode", mode.name).apply()
    }

    // --- Reading History & Progress ---

    fun saveProgress(progress: ReadingProgress) {
        val list = _history.value.toMutableList()
        list.removeAll { it.chapterId == progress.chapterId }
        list.add(0, progress)
        _history.value = list

        val arr = JSONArray()
        for (p in list.take(100)) {
            val obj = JSONObject().apply {
                put("mangaId", p.mangaId)
                put("chapterId", p.chapterId)
                put("mangaTitle", p.mangaTitle)
                put("chapterTitle", p.chapterTitle)
                put("lastPage", p.lastPage)
                put("totalPages", p.totalPages)
                put("timestamp", p.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString("reading_history", arr.toString()).apply()
    }

    fun getChapterProgress(chapterId: String): ReadingProgress? {
        return _history.value.find { it.chapterId == chapterId }
    }

    // --- Offline Downloads Management ---

    fun isChapterDownloaded(mangaId: String, chapterId: String): Boolean {
        val dir = File(downloadsDir, "$mangaId/$chapterId")
        if (!dir.exists() || !dir.isDirectory) return false
        val files = dir.listFiles { _, name -> name.endsWith(".jpg") || name.endsWith(".png") }
        return !files.isNullOrEmpty()
    }

    fun getDownloadedPages(mangaId: String, chapterId: String): List<MangaPage> {
        val dir = File(downloadsDir, "$mangaId/$chapterId")
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles { _, name -> name.endsWith(".jpg") || name.endsWith(".png") } ?: return emptyList()
        return files.sortedBy { it.name }.mapIndexed { index, file ->
            MangaPage(
                pageNumber = index + 1,
                imageUrl = file.toURI().toString(),
                localPath = file.absolutePath
            )
        }
    }

    fun deleteDownloadedChapter(mangaId: String, chapterId: String) {
        val dir = File(downloadsDir, "$mangaId/$chapterId")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        // Remove from download queue
        val currentQueue = _downloadQueue.value.toMutableList()
        currentQueue.removeAll { it.chapterId == chapterId }
        _downloadQueue.value = currentQueue
    }

    fun queueDownload(chapter: MangaChapter, manga: MangaItem) {
        if (isChapterDownloaded(manga.id, chapter.id)) return

        val taskId = "dl_${chapter.id}"
        val existing = _downloadQueue.value.find { it.chapterId == chapter.id }
        if (existing != null && (existing.status == DownloadStatus.DOWNLOADING || existing.status == DownloadStatus.PENDING)) {
            return
        }

        val task = DownloadTask(
            id = taskId,
            chapterId = chapter.id,
            chapterTitle = chapter.displayTitle,
            mangaId = manga.id,
            mangaTitle = manga.title,
            status = DownloadStatus.PENDING
        )

        _downloadQueue.value = _downloadQueue.value + task
        processNextDownload()
    }

    fun queueDownloadRange(chapters: List<MangaChapter>, manga: MangaItem) {
        for (chap in chapters) {
            queueDownload(chap, manga)
        }
    }

    private fun processNextDownload() {
        scope.launch {
            val pending = _downloadQueue.value.firstOrNull { it.status == DownloadStatus.PENDING } ?: return@launch
            updateTaskStatus(pending.id, DownloadStatus.DOWNLOADING, progress = 0.05f)

            try {
                // Fetch real pages from connector
                val pagesRes = connector.fetchChapterPages(pending.chapterId)
                val pages = pagesRes.getOrNull()
                if (pages.isNullOrEmpty()) {
                    updateTaskStatus(pending.id, DownloadStatus.FAILED, error = "تعذر الحصول على روابط الصفحات")
                    processNextDownload()
                    return@launch
                }

                val targetDir = File(downloadsDir, "${pending.mangaId}/${pending.chapterId}")
                targetDir.mkdirs()

                var downloaded = 0
                for (page in pages) {
                    val bytesRes = connector.downloadImageBytes(page.imageUrl)
                    val bytes = bytesRes.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val file = File(targetDir, "page_${String.format("%03d", page.pageNumber)}.jpg")
                        FileOutputStream(file).use { it.write(bytes) }
                        downloaded++
                        val prog = downloaded.toFloat() / pages.size.toFloat()
                        updateTaskProgress(pending.id, prog, pages.size, downloaded)
                    }
                }

                if (downloaded > 0) {
                    updateTaskStatus(pending.id, DownloadStatus.COMPLETED, progress = 1.0f)
                } else {
                    updateTaskStatus(pending.id, DownloadStatus.FAILED, error = "فشل تنزيل صفحات الفصل")
                }
            } catch (e: Exception) {
                updateTaskStatus(pending.id, DownloadStatus.FAILED, error = e.message)
            }

            processNextDownload()
        }
    }

    private fun updateTaskStatus(taskId: String, status: DownloadStatus, progress: Float = 0f, error: String? = null) {
        val list = _downloadQueue.value.map {
            if (it.id == taskId) it.copy(status = status, progress = progress, errorMsg = error) else it
        }
        _downloadQueue.value = list
    }

    private fun updateTaskProgress(taskId: String, progress: Float, totalPages: Int, downloaded: Int) {
        val list = _downloadQueue.value.map {
            if (it.id == taskId) it.copy(progress = progress, totalPages = totalPages, downloadedPages = downloaded) else it
        }
        _downloadQueue.value = list
    }
}
