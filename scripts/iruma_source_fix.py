from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def edit(path, fn):
    p = ROOT / path
    s = p.read_text()
    s2 = fn(s)
    if s2 == s:
        raise RuntimeError("No changes made to " + path)
    p.write_text(s2)

def connector(s):
    start = s.index("    suspend fun searchManga(")
    end = s.index("    suspend fun fetchChapters(", start)
    new = r'''    suspend fun searchManga(query: String, source: SourceItem?): Result<List<MangaItem>> =
        withContext(Dispatchers.IO) {
            if (source == null) return@withContext Result.success(emptyList())
            try {
                if (isMangaDex(source)) return@withContext searchMangaDex(query.trim(), source)
                val q = query.trim()
                val encoded = URLEncoder.encode(q, "UTF-8")
                val base = source.baseUrl.trimEnd('/')
                val candidates = if (q.isBlank()) listOf(base) else listOf(
                    "$base/search?q=$encoded", "$base/?s=$encoded",
                    "$base/search/$encoded/", "$base/?post_type=wp-manga&s=$encoded"
                )
                for (url in candidates.distinct()) {
                    val results = crawlSearchPages(url, source)
                    if (results.isNotEmpty()) return@withContext Result.success(results)
                }
                Result.failure(Exception("لم يتم العثور على أعمال من " + source.name + " أو أن الموقع يمنع الوصول الآلي."))
            } catch (e: Exception) {
                Result.failure(Exception("تعذر الاتصال بالمصدر " + source.name + ": " + e.message, e))
            }
        }

    private fun crawlSearchPages(initialUrl: String, source: SourceItem): List<MangaItem> {
        val queue = ArrayDeque<String>()
        val visited = HashSet<String>()
        val results = LinkedHashMap<String, MangaItem>()
        queue.add(initialUrl)
        var pages = 0
        while (queue.isNotEmpty() && pages < 100) {
            val url = queue.removeFirst()
            if (!visited.add(url)) continue
            pages++
            try {
                val response = execute(url)
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank() || looksLikeHumanVerification(body)) continue
                val document = Jsoup.parse(body, url)
                for (item in parseSearchDocument(document, source)) results.putIfAbsent(item.id, item)
                for (next in findPaginationLinks(document, source.baseUrl)) {
                    if (!visited.contains(next) && !queue.contains(next)) queue.addLast(next)
                }
            } catch (_: Exception) { }
        }
        return results.values.toList()
    }

'''
    s = s[:start] + new + s[end:]

    start = s.index("    suspend fun fetchChapters(")
    end = s.index("    suspend fun fetchChapterPages(", start)
    new = r'''    private fun crawlChapterLinks(initialUrl: String, baseUrl: String): List<Pair<String, String>> {
        val queue = ArrayDeque<String>()
        val visited = HashSet<String>()
        val links = LinkedHashMap<String, String>()
        queue.add(initialUrl)
        var pages = 0
        while (queue.isNotEmpty() && pages < 100) {
            val url = queue.removeFirst()
            if (!visited.add(url)) continue
            pages++
            try {
                val response = execute(url)
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank() || looksLikeHumanVerification(body)) continue
                val document = Jsoup.parse(body, url)
                val elements = document.select(
                    "li.wp-manga-chapter a, .wp-manga-chapter a, .listing-chapters_wrap a, " +
                    ".chapter-list a, .chapters-list a, .chapter-item a, .row-content-chapter a, " +
                    ".eplister li a, .listing-chapters_wrap li a, .chapters li a, .chapter-list-item a"
                ).toMutableList()
                if (elements.isEmpty()) {
                    elements.addAll(document.select("a[href]").filter {
                        looksLikeChapterLink(it.text(), it.attr("href"))
                    })
                }
                for (element in elements) {
                    val href = element.absUrl("href").ifBlank { element.attr("href") }
                    if (href.startsWith("http") && sameSite(href, baseUrl) && !links.containsKey(href)) {
                        links[href] = element.text().trim().ifBlank { "فصل" }
                    }
                }
                for (next in findPaginationLinks(document, baseUrl)) {
                    if (!visited.contains(next) && !queue.contains(next)) queue.addLast(next)
                }
            } catch (_: Exception) { }
        }
        return links.map { it.key to it.value }
    }

    suspend fun fetchChapters(
        mangaId: String,
        source: SourceItem? = null
    ): Result<List<MangaChapter>> = withContext(Dispatchers.IO) {
        try {
            if (isMangaDex(source) || !mangaId.startsWith("generic:")) {
                return@withContext fetchMangaDexChapters(mangaId)
            }
            val mangaUrl = decodeId(mangaId, "generic:")
                ?: return@withContext Result.failure(Exception("رابط العمل غير صالح."))

            val chapterLinks = crawlChapterLinks(mangaUrl, mangaUrl)
            val chapters = mutableListOf<MangaChapter>()
            val seen = HashSet<String>()
            for ((href, label) in chapterLinks) {
                if (!seen.add(href)) continue
                val number = extractChapterNumber(label, href) ?: continue
                chapters.add(
                    MangaChapter(
                        id = genericChapterId(href),
                        mangaId = mangaId,
                        chapterNumber = number,
                        title = label,
                        releaseDate = "",
                        scanlationGroup = source?.name ?: "المصدر",
                        pageCount = 0
                    )
                )
            }
            val sorted = chapters.sortedWith(
                compareBy<MangaChapter> { it.chapterNumber }.thenBy { it.title }
            )
            if (sorted.isEmpty()) {
                Result.failure(Exception("لم أجد قائمة الفصول في صفحة العمل. قد يحتاج هذا الموقع إلى دعم مخصص."))
            } else {
                Result.success(sorted)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

'''
    s = s[:start] + new + s[end:]

    start = s.index("    private fun parseSearchDocument(")
    end = s.index("    private fun execute(", start)
    new = r'''    private fun parseSearchDocument(document: Document, source: SourceItem): List<MangaItem> {
        val selectors = listOf(
            "div.c-tabs-item__content div.post-title a",
            ".manga__item .post-title a",
            ".page-item-detail.manga a[href]",
            ".item-summary .post-title a",
            ".row.c-tabs-item__content a[href]",
            ".page-item-detail a[href]",
            ".manga__item a[href]",
            ".c-tabs-item__content a[href]",
            "article a[href]",
            ".bsx a[href]",
            ".bs a[href]",
            ".listupd a[href]",
            "h2 a[href], h3 a[href], h4 a[href]",
            "a[href*='/manga/']",
            "a[href*='/manhwa/']",
            "a[href*='/manhua/']",
            "a[href*='/comic/']",
            "a[href*='/series/']",
            "a[href*='/title/']"
        )
        val anchors = LinkedHashSet<Element>()
        for (selector in selectors) anchors.addAll(document.select(selector))
        val results = mutableListOf<MangaItem>()
        val seen = HashSet<String>()

        for (anchor in anchors) {
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            if (!href.startsWith("http") || !sameSite(href, source.baseUrl) || !seen.add(href)) continue

            val container = anchor.closest(
                ".page-item-detail, .manga__item, .c-tabs-item__content, article, .row, " +
                ".bs, .bsx, .item, .listupd, .item-summary"
            ) ?: anchor.parent()

            val title = firstNonBlank(
                anchor.text().trim(),
                anchor.attr("title").trim(),
                anchor.attr("aria-label").trim(),
                container?.selectFirst(
                    ".post-title, .entry-title, .item-title, .tt, h1, h2, h3, h4, h5"
                )?.text()?.trim(),
                container?.selectFirst("img[alt]")?.attr("alt")?.trim()
            ) ?: continue

            if (title.length < 2 || looksLikeNavigation(title) ||
                looksLikeChapterLink(title, href)) continue

            val image = container?.selectFirst(
                "img[data-src], img[data-lazy-src], img[data-original], img[data-cfsrc], img[src]"
            )
            results.add(
                MangaItem(
                    id = genericId(href),
                    title = title,
                    coverUrl = image?.let { imageUrl(it) }.orEmpty(),
                    author = container?.selectFirst(
                        ".author-content a, .author a, .mg_author a, .author"
                    )?.text()?.trim().orEmpty(),
                    description = container?.selectFirst(
                        ".summary-content, .post-content, .description, .summary__content, " +
                        ".excerpt, .des, .limit, .entry-content"
                    )?.text()?.trim().orEmpty(),
                    sourceId = source.id,
                    sourceName = source.name
                )
            )
        }
        return results
    }

'''
    s = s[:start] + new + s[end:]

    if "import java.util.ArrayDeque" not in s:
        s = s.replace("import java.util.Locale\n", "import java.util.ArrayDeque\nimport java.util.Locale\n", 1)

    old = '''    private fun imageUrl(image: Element): String =
        firstNonBlank(
            image.absUrl("data-src"),
            image.absUrl("data-lazy-src"),
            image.absUrl("data-original"),
            image.absUrl("src")
        ).orEmpty()
'''
    new = '''    private fun imageUrl(image: Element): String =
        firstNonBlank(
            image.absUrl("data-src"),
            image.absUrl("data-lazy-src"),
            image.absUrl("data-original"),
            image.absUrl("data-cfsrc"),
            image.absUrl("data-url"),
            image.absUrl("src")
        ).orEmpty()

    private fun findPaginationLinks(document: Document, baseUrl: String): List<String> {
        val selectors = listOf(
            "a[rel=next]", "a.next", ".next a", ".pagination a[href]",
            ".page-numbers[href]", ".nav-links a[href]", ".wp-pagenavi a[href]",
            ".pagination-nav a[href]", ".paging-navigation a[href]", ".pagination-area a[href]"
        )
        val links = LinkedHashSet<String>()
        for (selector in selectors) {
            for (element in document.select(selector)) {
                val href = element.absUrl("href").ifBlank { element.attr("href") }
                if (href.startsWith("http") && sameSite(href, baseUrl)) links.add(href)
            }
        }
        return links.toList()
    }
'''
    if old not in s:
        raise RuntimeError("Connector image block changed unexpectedly")
    return s.replace(old, new, 1)

def viewmodel(s):
    s=s.replace("import kotlinx.coroutines.SharingStarted\n",
                "import kotlinx.coroutines.SharingStarted\nimport kotlinx.coroutines.Job\nimport kotlinx.coroutines.async\nimport kotlinx.coroutines.awaitAll\nimport kotlinx.coroutines.delay\n",1)
    s=s.replace('''    private val _selectedSource = MutableStateFlow<SourceItem?>(null)
    val selectedSource: StateFlow<SourceItem?> = _selectedSource.asStateFlow()
''','''    private val _selectedSource = MutableStateFlow<SourceItem?>(null)
    val selectedSource: StateFlow<SourceItem?> = _selectedSource.asStateFlow()

    private val _searchAllSources = MutableStateFlow(true)
    val searchAllSources: StateFlow<Boolean> = _searchAllSources.asStateFlow()
''',1)
    s=s.replace('''    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
''','''    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private var searchJob: Job? = null
''',1)
    old='''    fun setSource(source: SourceItem?) {
        _selectedSource.value = source
        if (source != null) {
            performSearch(_searchQuery.value)
        } else {
            _searchResults.value = emptyList()
        }
    }
'''
    new='''    fun setSource(source: SourceItem?) {
        _searchAllSources.value = false
        _selectedSource.value = source
        if (source != null) performSearch(_searchQuery.value)
        else _searchResults.value = emptyList()
    }

    fun setSearchAllSources(enabled: Boolean) {
        _searchAllSources.value = enabled
        if (enabled) performSearch(_searchQuery.value)
        else _selectedSource.value?.let { performSearch(_searchQuery.value) }
    }
'''
    if old not in s: raise RuntimeError("ViewModel setSource block not found")
    s=s.replace(old,new,1)
    old='''    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        performSearch(query)
    }
'''
    new='''    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            performSearch(query)
        }
    }
'''
    if old not in s: raise RuntimeError("ViewModel setSearchQuery block not found")
    s=s.replace(old,new,1)
    start=s.index("    fun performSearch(query: String) {")
    end=s.index("    // --- Manga Details & Chapters Operations ---",start)
    new=r'''    fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchError.value = null
            _isSearching.value = false
            return
        }
        val targets = if (_searchAllSources.value) sources.value else listOfNotNull(_selectedSource.value)
        if (targets.isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            val results = targets.map { source ->
                async { repository.connector.searchManga(query, source) }
            }.awaitAll()
            val successful = results.filter { it.isSuccess }
            val merged = successful.flatMap { it.getOrDefault(emptyList()) }
                .distinctBy { it.sourceId + "::" + it.id }
            _searchResults.value = merged
            if (merged.isEmpty() && successful.isEmpty()) {
                _searchError.value = "تعذر الاتصال بجميع المصادر المتاحة. بعض المواقع قد تحتاج تحققاً بشرياً."
            }
            _isSearching.value = false
        }
    }

'''
    s=s[:start]+new+s[end:]
    return s

def browse(s):
    s=s.replace('''    val selectedSource by viewModel.selectedSource.collectAsState()
    val sources by viewModel.sources.collectAsState()
''','''    val selectedSource by viewModel.selectedSource.collectAsState()
    val searchAllSources by viewModel.searchAllSources.collectAsState()
    val sources by viewModel.sources.collectAsState()
''',1)
    needle='''            ) {
                items(sources, key = { it.id }) { source ->
'''
    insert='''            ) {
                item {
                    FilterChip(
                        selected = searchAllSources,
                        onClick = { viewModel.setSearchAllSources(true) },
                        label = { Text("كل المصادر", fontSize = 12.sp, fontWeight = if (searchAllSources) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IrumaCyanPrimary,
                            selectedLabelColor = Color(0xFF041824),
                            selectedLeadingIconColor = Color(0xFF041824),
                            containerColor = DemonSurfaceElevated,
                            labelColor = TextSecondary,
                            iconColor = TextMuted
                        )
                    )
                }
                items(sources, key = { it.id }) { source ->
'''
    if needle not in s: raise RuntimeError("Browse source row not found")
    return s.replace(needle,insert,1)

edit("app/src/main/java/com/example/connector/ConnectorEngine.kt", connector)
edit("app/src/main/java/com/example/ui/MangaViewModel.kt", viewmodel)
edit("app/src/main/java/com/example/ui/screens/BrowseScreen.kt", browse)
print("source fixes applied")
