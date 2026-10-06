package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MangaRepository
import com.example.model.DownloadTask
import com.example.model.MangaCategory
import com.example.model.MangaChapter
import com.example.model.MangaItem
import com.example.model.MangaPage
import com.example.model.ReadingMode
import com.example.model.ReadingProgress
import com.example.model.SourceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    LIBRARY,
    BROWSE,
    DOWNLOADS,
    HISTORY,
    SETTINGS
}

class MangaViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MangaRepository(application.applicationContext)

    // Current Navigation Tab
    private val _currentTab = MutableStateFlow(AppTab.LIBRARY)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // 1. Library & Categories
    val libraryManga: StateFlow<List<MangaItem>> = repository.library
    val categories: StateFlow<List<MangaCategory>> = repository.categories

    private val _selectedCategoryId = MutableStateFlow<String?>(null) // null = all
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    val filteredLibrary: StateFlow<List<MangaItem>> = combine(
        libraryManga,
        _selectedCategoryId
    ) { mangaList, catId ->
        if (catId == null) {
            mangaList
        } else {
            mangaList.filter { it.categoryIds.contains(catId) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Browse & Search (Real Data from Sources - Starts completely empty by default)
    val sources: StateFlow<List<SourceItem>> = repository.sources

    private val _selectedSource = MutableStateFlow<SourceItem?>(null)
    val selectedSource: StateFlow<SourceItem?> = _selectedSource.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MangaItem>>(emptyList())
    val searchResults: StateFlow<List<MangaItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    private val _isSearchingAllSources = MutableStateFlow(false)
    val isSearchingAllSources: StateFlow<Boolean> = _isSearchingAllSources.asStateFlow()

    // 3. Selected Manga Details & Chapters
    private val _selectedManga = MutableStateFlow<MangaItem?>(null)
    val selectedManga: StateFlow<MangaItem?> = _selectedManga.asStateFlow()

    private val _mangaChapters = MutableStateFlow<List<MangaChapter>>(emptyList())
    val mangaChapters: StateFlow<List<MangaChapter>> = _mangaChapters.asStateFlow()

    private val _isLoadingChapters = MutableStateFlow(false)
    val isLoadingChapters: StateFlow<Boolean> = _isLoadingChapters.asStateFlow()

    private val _chaptersSortAscending = MutableStateFlow(false)
    val chaptersSortAscending: StateFlow<Boolean> = _chaptersSortAscending.asStateFlow()

    // 4. Active Reader
    private val _activeReaderChapter = MutableStateFlow<MangaChapter?>(null)
    val activeReaderChapter: StateFlow<MangaChapter?> = _activeReaderChapter.asStateFlow()

    private val _readerPages = MutableStateFlow<List<MangaPage>>(emptyList())
    val readerPages: StateFlow<List<MangaPage>> = _readerPages.asStateFlow()

    private val _isReaderLoading = MutableStateFlow(false)
    val isReaderLoading: StateFlow<Boolean> = _isReaderLoading.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    val readingMode: StateFlow<ReadingMode> = repository.readingMode

    // 5. Downloads Manager
    val downloadQueue: StateFlow<List<DownloadTask>> = repository.downloadQueue

    // 6. History
    val readingHistory: StateFlow<List<ReadingProgress>> = repository.history

    // 7. WebView In-App Browser for Cloudflare/CAPTCHA verification
    private val _activeWebviewUrl = MutableStateFlow<String?>(null)
    val activeWebviewUrl: StateFlow<String?> = _activeWebviewUrl.asStateFlow()

    init {
        // Observe sources: Only search if user has added at least one source!
        viewModelScope.launch {
            repository.sources.collect { srcList ->
                val current = _selectedSource.value
                if (current == null && srcList.isNotEmpty()) {
                    _selectedSource.value = srcList.first()
                    performSearch(_searchQuery.value)
                } else if (current != null && !srcList.any { it.id == current.id }) {
                    _selectedSource.value = srcList.firstOrNull()
                    if (_selectedSource.value != null) {
                        performSearch(_searchQuery.value)
                    } else {
                        _searchResults.value = emptyList()
                    }
                }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun addCategory(name: String) {
        if (name.isNotBlank()) {
            repository.addCategory(name)
        }
    }

    fun updateCategory(id: String, newName: String) {
        if (newName.isNotBlank()) {
            repository.updateCategory(id, newName)
        }
    }

    fun deleteCategory(id: String) {
        repository.deleteCategory(id)
        if (_selectedCategoryId.value == id) {
            _selectedCategoryId.value = null
        }
    }

    // --- Source Management Operations (User added/edited/deleted from Settings) ---

    fun addSource(name: String, baseUrl: String, description: String = "", requiresVerification: Boolean = false) {
        if (name.isNotBlank() && baseUrl.isNotBlank()) {
            val added = repository.addSource(name, baseUrl, description, requiresVerification)
            _selectedSource.value = added
            performSearch(_searchQuery.value)
        }
    }

    fun updateSource(id: String, name: String, baseUrl: String, description: String = "", requiresVerification: Boolean = false) {
        if (name.isNotBlank() && baseUrl.isNotBlank()) {
            repository.updateSource(id, name, baseUrl, description, requiresVerification)
            if (_selectedSource.value?.id == id) {
                _selectedSource.value = _selectedSource.value?.copy(
                    name = name.trim(),
                    baseUrl = baseUrl.trim(),
                    description = description.trim(),
                    requiresVerification = requiresVerification
                )
            }
        }
    }

    fun deleteSource(id: String) {
        repository.deleteSource(id)
    }

    // --- Browse / Search Operations ---

    fun setSource(source: SourceItem?) {
        _selectedSource.value = source
        if (source != null) {
            performSearch(_searchQuery.value)
        } else {
            _searchResults.value = emptyList()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        performSearch(query)
    }

    fun performSearch(query: String) {
        val src = _selectedSource.value
        if (src == null) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            val result = repository.connector.searchManga(query, src)
            if (result.isSuccess) {
                _searchResults.value = result.getOrDefault(emptyList())
            } else {
                _searchError.value = result.exceptionOrNull()?.message ?: "حدث خطأ أثناء البحث"
            }
            _isSearching.value = false
        }
    }

    fun performSearchAllSources(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            _searchResults.value = emptyList()
            _searchError.value = "اكتب اسم المانجا أو المانهوا أولاً."
            return
        }
        val srcs = sources.value
        if (srcs.isEmpty()) {
            _searchResults.value = emptyList()
            _searchError.value = "لا توجد مصادر مضافة."
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _isSearchingAllSources.value = true
            _searchError.value = null
            try {
                val results = srcs.map { src ->
                    async {
                        withTimeoutOrNull(15_000L) {
                            repository.connector.searchManga(q, src).getOrElse { emptyList() }
                        } ?: emptyList()
                    }
                }.awaitAll().flatten()

                _searchResults.value = results
                    .distinctBy { it.id }
                    .sortedWith(compareBy<MangaItem> { it.sourceName.lowercase() }.thenBy { it.title.lowercase() })

                if (_searchResults.value.isEmpty()) {
                    _searchError.value = "لم يتم العثور على العمل في المصادر التي استجابت للبحث."
                }
            } finally {
                _isSearching.value = false
                _isSearchingAllSources.value = false
            }
        }
    }

    // --- Manga Details & Chapters Operations ---

    fun openMangaDetails(manga: MangaItem) {
        _selectedManga.value = manga
        loadChaptersForManga(manga.id)
    }

    fun closeMangaDetails() {
        _selectedManga.value = null
        _mangaChapters.value = emptyList()
    }

    fun toggleLibraryForSelectedManga(categoryIds: List<String> = emptyList()) {
        val current = _selectedManga.value ?: return
        if (repository.isMangaInLibrary(current.id)) {
            repository.removeFromLibrary(current.id)
            _selectedManga.value = current.copy(inLibrary = false)
        } else {
            repository.addToLibrary(current, categoryIds)
            _selectedManga.value = current.copy(inLibrary = true, categoryIds = categoryIds)
        }
    }

    fun updateSelectedMangaCategories(categoryIds: List<String>) {
        val current = _selectedManga.value ?: return
        repository.updateMangaCategories(current.id, categoryIds)
        _selectedManga.value = current.copy(categoryIds = categoryIds)
    }

    fun toggleChaptersSort() {
        _chaptersSortAscending.value = !_chaptersSortAscending.value
    }

    private fun loadChaptersForManga(mangaId: String) {
        viewModelScope.launch {
            _isLoadingChapters.value = true
            val source = sources.value.firstOrNull { it.id == (_selectedManga.value?.sourceId ?: "") }
            val result = repository.connector.fetchChapters(mangaId, source)
            if (result.isSuccess) {
                val chapters = result.getOrDefault(emptyList())
                // Enrich with download and read states
                val enriched = chapters.map { chap ->
                    val isDownloaded = repository.isChapterDownloaded(mangaId, chap.id)
                    val progress = repository.getChapterProgress(chap.id)
                    chap.copy(
                        isDownloaded = isDownloaded,
                        isRead = progress != null && progress.lastPage >= (progress.totalPages - 1).coerceAtLeast(0),
                        lastReadPage = progress?.lastPage ?: 0
                    )
                }
                _mangaChapters.value = enriched
            } else {
                _mangaChapters.value = emptyList()
            }
            _isLoadingChapters.value = false
        }
    }

    // --- Reader Operations ---

    fun openReader(chapter: MangaChapter) {
        _activeReaderChapter.value = chapter
        _isReaderLoading.value = true
        _currentPageIndex.value = chapter.lastReadPage

        viewModelScope.launch {
            val currentManga = _selectedManga.value
            val mangaId = currentManga?.id ?: chapter.mangaId

            // Check if downloaded offline
            if (repository.isChapterDownloaded(mangaId, chapter.id)) {
                val localPages = repository.getDownloadedPages(mangaId, chapter.id)
                if (localPages.isNotEmpty()) {
                    _readerPages.value = localPages
                    _isReaderLoading.value = false
                    return@launch
                }
            }

            // Otherwise fetch real pages from network
            val source = sources.value.firstOrNull { it.id == (currentManga?.sourceId ?: "") }
            val result = repository.connector.fetchChapterPages(chapter.id, source)
            if (result.isSuccess) {
                _readerPages.value = result.getOrDefault(emptyList())
            } else {
                _readerPages.value = emptyList()
            }
            _isReaderLoading.value = false
        }
    }

    fun closeReader() {
        _activeReaderChapter.value = null
        _readerPages.value = emptyList()
    }

    fun onPageChanged(pageIndex: Int) {
        _currentPageIndex.value = pageIndex
        val chapter = _activeReaderChapter.value ?: return
        val manga = _selectedManga.value
        val total = _readerPages.value.size

        repository.saveProgress(
            ReadingProgress(
                mangaId = manga?.id ?: chapter.mangaId,
                chapterId = chapter.id,
                mangaTitle = manga?.title ?: "مانجا",
                chapterTitle = chapter.displayTitle,
                lastPage = pageIndex,
                totalPages = total
            )
        )
    }

    fun setReadingMode(mode: ReadingMode) {
        repository.setReadingMode(mode)
    }

    fun nextChapter() {
        val current = _activeReaderChapter.value ?: return
        val list = _mangaChapters.value.sortedBy { it.chapterNumber }
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx != -1 && idx < list.size - 1) {
            openReader(list[idx + 1])
        }
    }

    fun prevChapter() {
        val current = _activeReaderChapter.value ?: return
        val list = _mangaChapters.value.sortedBy { it.chapterNumber }
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx > 0) {
            openReader(list[idx - 1])
        }
    }

    // --- Range Downloads Operations ---

    fun downloadChapter(chapter: MangaChapter) {
        val manga = _selectedManga.value ?: return
        repository.queueDownload(chapter, manga)
    }

    fun downloadRange(fromChapter: Float, toChapter: Float) {
        val manga = _selectedManga.value ?: return
        val min = minOf(fromChapter, toChapter)
        val max = maxOf(fromChapter, toChapter)
        val targets = _mangaChapters.value.filter { it.chapterNumber in min..max }
        repository.queueDownloadRange(targets, manga)
    }

    fun downloadNextUnread(count: Int) {
        val manga = _selectedManga.value ?: return
        val unread = _mangaChapters.value
            .sortedBy { it.chapterNumber }
            .filter { !it.isRead && !repository.isChapterDownloaded(manga.id, it.id) }
            .take(count)
        repository.queueDownloadRange(unread, manga)
    }

    fun deleteDownloadedChapter(mangaId: String, chapterId: String) {
        repository.deleteDownloadedChapter(mangaId, chapterId)
        // Refresh chapter state
        _mangaChapters.value = _mangaChapters.value.map {
            if (it.id == chapterId) it.copy(isDownloaded = false) else it
        }
    }

    // --- In-App Browser / WebView ---

    fun openWebview(url: String) {
        _activeWebviewUrl.value = url
    }

    fun closeWebview() {
        _activeWebviewUrl.value = null
    }
}
