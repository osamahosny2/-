package com.example.connector

import android.util.Base64
import android.webkit.CookieManager
import com.example.model.MangaChapter
import com.example.model.MangaItem
import com.example.model.MangaPage
import com.example.model.SourceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Dedicated adapter for the current ProComic/ProChan website.
 *
 * The host and page layout have changed repeatedly, so this adapter does not rely
 * on the optional Kotatsu parser matching the display name. It supports browse,
 * search, title/cover metadata, chapter lists, and reader-page image URLs directly.
 */
class ProComicConnector {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    suspend fun searchManga(
        query: String,
        source: SourceItem,
        page: Int = 1
    ): Result<List<MangaItem>> = withContext(Dispatchers.IO) {
        try {
            val base = canonicalBase(source.baseUrl)
            val languageBase = arabicBase(base)
            val q = query.trim()
            val documents = LinkedHashMap<String, Document>()

            if (q.isBlank()) {
                val urls = if (page <= 1) {
                    listOf(
                        languageBase,
                        languageBase + "/series",
                        languageBase + "/series/manhua",
                        languageBase + "/series/manhwa",
                        base
                    )
                } else {
                    listOf(
                        languageBase + "/series?page=" + page,
                        languageBase + "/series/manhua?page=" + page,
                        languageBase + "/series/manhwa?page=" + page,
                        languageBase + "/page/" + page,
                        languageBase + "?page=" + page
                    )
                }
                for (url in urls) {
                    val doc = fetchDocument(url, base) ?: continue
                    documents.putIfAbsent(doc.location().ifBlank { url }, doc)
                    if (documents.values.sumOf { parseSeriesCards(it, source, "").size } >= PAGE_SIZE) break
                }
            } else {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val urls = listOf(
                    languageBase + "/search?query=" + encoded + "&page=" + page,
                    languageBase + "/search?q=" + encoded + "&page=" + page,
                    languageBase + "/search?search=" + encoded + "&page=" + page,
                    languageBase + "/search?term=" + encoded + "&page=" + page,
                    languageBase + "/series?search=" + encoded + "&page=" + page,
                    languageBase + "/series?query=" + encoded + "&page=" + page,
                    languageBase + "?s=" + encoded,
                    base + "/search?query=" + encoded,
                    base + "/?s=" + encoded
                )
                for (url in urls) {
                    val doc = fetchDocument(url, base) ?: continue
                    val found = parseSeriesCards(doc, source, q)
                    if (found.isNotEmpty()) {
                        documents[doc.location().ifBlank { url }] = doc
                        break
                    }
                }

                // Search pages occasionally change their query parameter. As a safe fallback,
                // search the visible Arabic catalogue and match normalized titles locally.
                if (documents.isEmpty()) {
                    val home = fetchDocument(languageBase, base)
                    if (home != null) documents[home.location().ifBlank { languageBase }] = home
                }
            }

            val results = LinkedHashMap<String, MangaItem>()
            for (document in documents.values) {
                parseSeriesCards(document, source, q).forEach { results.putIfAbsent(it.id, it) }
            }
            if (results.isEmpty()) {
                Result.failure(
                    IllegalStateException(
                        if (q.isBlank()) {
                            "لم أتمكن من اكتشاف قائمة الأعمال في ProComic. تأكد من رابط المصدر ثم أعد المحاولة."
                        } else {
                            "لم أجد عملاً بهذا الاسم في ProComic. تأكد من كتابة الاسم أو افتح الموقع من المتصفح المدمج للتحقق من الحماية."
                        }
                    )
                )
            } else {
                Result.success(results.values.take(if (q.isBlank()) PAGE_SIZE * 3 else MAX_RESULTS))
            }
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الاتصال بمصدر ProComic: " + e.message, e))
        }
    }

    suspend fun fetchDetails(
        manga: MangaItem,
        source: SourceItem
    ): Result<MangaItem> = withContext(Dispatchers.IO) {
        try {
            val base = canonicalBase(source.baseUrl)
            val url = decodeUrl(manga.id)
                ?: manga.id.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: return@withContext Result.failure(IllegalArgumentException("رابط العمل غير صالح."))
            val document = fetchDocument(url, base)
                ?: return@withContext Result.failure(IllegalStateException("تعذر فتح صفحة العمل في ProComic."))

            val title = firstNonBlank(
                document.selectFirst("h1")?.text()?.trim(),
                document.selectFirst(".series-title,.manga-title,.comic-title,[class*=series-title],[class*=manga-title]")?.text()?.trim(),
                document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore("|")?.trim(),
                document.selectFirst("meta[name=title]")?.attr("content")?.trim(),
                manga.title
            ).orEmpty()

            val cover = firstNonBlank(
                document.selectFirst("meta[property=og:image]")?.let { absoluteUrl(it.attr("content"), document.location()) },
                document.selectFirst("meta[name=twitter:image]")?.let { absoluteUrl(it.attr("content"), document.location()) },
                document.selectFirst(".series-cover img,.manga-cover img,.comic-cover img,.poster img,.cover img")?.let { bestImageUrl(it, document.location()) },
                document.selectFirst("img[alt*=cover i],img[class*=cover i],img[class*=poster i]")?.let { bestImageUrl(it, document.location()) },
                manga.coverUrl
            ).orEmpty()

            val description = firstNonBlank(
                document.selectFirst("meta[name=description]")?.attr("content")?.trim(),
                document.selectFirst("meta[property=og:description]")?.attr("content")?.trim(),
                document.selectFirst(".series-description,.manga-description,.comic-description,.description,[class*=synopsis]")?.text()?.trim(),
                manga.description
            ).orEmpty()

            val author = firstNonBlank(
                document.selectFirst("[class*=author] a,[class*=author] .value,.series-author,.manga-author")?.text()?.trim(),
                valueAfterLabel(document, listOf("المؤلف", "الكاتب", "Author", "Writer")),
                manga.author
            ).orEmpty()

            val status = mapStatus(
                firstNonBlank(
                    document.selectFirst("[class*=status]")?.text()?.trim(),
                    valueAfterLabel(document, listOf("الحالة", "Status")),
                    document.text().take(5000)
                ).orEmpty(),
                manga.status
            )

            val genres = LinkedHashSet<String>()
            manga.genres.filter { it.isNotBlank() }.forEach { genres.add(it) }
            document.select("a[href*='genre'],a[href*='tag'],a[href*='category'],[class*=genre] a").forEach {
                it.text().trim().takeIf { value -> value.length in 2..50 }?.let(genres::add)
            }

            val chapterCount = extractChapterLinks(document, url).size
            Result.success(
                manga.copy(
                    title = title,
                    coverUrl = cover,
                    author = author,
                    status = status,
                    description = description,
                    genres = genres.toList(),
                    sourceId = source.id,
                    sourceName = source.name,
                    sourceBaseUrl = base,
                    totalChaptersCount = maxOf(chapterCount, manga.totalChaptersCount)
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("تعذر استخراج عنوان العمل وغلافه وبياناته من ProComic: " + e.message, e))
        }
    }

    suspend fun fetchChapters(
        mangaId: String,
        source: SourceItem
    ): Result<List<MangaChapter>> = withContext(Dispatchers.IO) {
        try {
            val base = canonicalBase(source.baseUrl)
            val mangaUrl = decodeUrl(mangaId)
                ?: return@withContext Result.failure(IllegalArgumentException("رابط العمل غير صالح."))

            val documents = collectChapterDocuments(mangaUrl, base, MAX_CHAPTER_PAGES)
            val chapters = LinkedHashMap<String, MangaChapter>()
            for (document in documents) {
                for ((url, label) in extractChapterLinks(document, mangaUrl)) {
                    val number = extractChapterNumber(label, url) ?: continue
                    val id = encodeUrl(url, "procomic-chapter:")
                    chapters.putIfAbsent(
                        id,
                        MangaChapter(
                            id = id,
                            mangaId = mangaId,
                            chapterNumber = number,
                            title = label.ifBlank { "الفصل " + formatNumber(number) },
                            releaseDate = "",
                            scanlationGroup = source.name,
                            pageCount = 0
                        )
                    )
                }
            }

            val sorted = chapters.values.sortedWith(
                compareBy<MangaChapter> { it.chapterNumber }.thenBy { it.title }
            )
            if (sorted.isEmpty()) {
                Result.failure(
                    IllegalStateException(
                        "لم أتمكن من استخراج فصول العمل من ProComic. إذا كانت قائمة الفصول لا تظهر إلا بعد تحميل JavaScript، افتح العمل في المتصفح المدمج أولاً."
                    )
                )
            } else {
                Result.success(sorted)
            }
        } catch (e: Exception) {
            Result.failure(Exception("تعذر جلب فصول ProComic: " + e.message, e))
        }
    }

    suspend fun fetchChapterPages(
        chapterId: String,
        source: SourceItem
    ): Result<List<MangaPage>> = withContext(Dispatchers.IO) {
        try {
            val base = canonicalBase(source.baseUrl)
            val chapterUrl = decodeUrl(chapterId)
                ?: return@withContext Result.failure(IllegalArgumentException("رابط الفصل غير صالح."))
            val document = fetchDocument(chapterUrl, base)
                ?: return@withContext Result.failure(IllegalStateException("تعذر فتح الفصل في ProComic."))

            val urls = LinkedHashSet<String>()
            val selectors = listOf(
                ".reader-content img",
                ".reading-content img",
                ".chapter-content img",
                ".chapter-pages img",
                ".page-image img",
                ".reader-page img",
                "[class*=reader] img",
                "[class*=chapter] img",
                "main img[data-src]",
                "main img[data-lazy-src]",
                "main img[srcset]"
            )
            for (selector in selectors) {
                for (image in document.select(selector)) {
                    val url = bestImageUrl(image, document.location())
                    if (isImageUrl(url) && !isLikelyDecoration(url, image)) urls.add(url)
                }
                if (urls.size >= 2) break
            }

            // Some themes place the page URLs in a JSON script rather than <img> elements.
            if (urls.isEmpty()) {
                val body = document.html()
                val pattern = Regex("""https?://[^"'\\\s<>]+?\.(?:avif|webp|jpe?g|png)(?:\?[^"'\\\s<>]*)?""", RegexOption.IGNORE_CASE)
                pattern.findAll(body).map { it.value.replace("&amp;", "&") }
                    .filter { isImageUrl(it) && !isLikelyDecoration(it, null) }
                    .forEach { urls.add(it) }
            }

            if (urls.isEmpty()) {
                Result.failure(IllegalStateException("لم أتمكن من استخراج صور صفحات الفصل. قد يكون الفصل محمياً أو يعتمد على JavaScript."))
            } else {
                Result.success(urls.mapIndexed { index, url -> MangaPage(index + 1, url) })
            }
        } catch (e: Exception) {
            Result.failure(Exception("تعذر استخراج صور الفصل من ProComic: " + e.message, e))
        }
    }

    private fun parseSeriesCards(
        document: Document,
        source: SourceItem,
        query: String
    ): List<MangaItem> {
        val anchors = LinkedHashSet<Element>()
        anchors.addAll(document.select("a[href*='/series/']"))
        anchors.addAll(document.select("a[data-href*='/series/']"))

        val results = LinkedHashMap<String, MangaItem>()
        for (anchor in anchors) {
            val rawHref = firstNonBlank(anchor.absUrl("href"), anchor.attr("href"), anchor.attr("data-href")) ?: continue
            val url = absoluteUrl(rawHref, document.location())
            if (!isSeriesUrl(url) || !sameHost(url, canonicalBase(source.baseUrl))) continue

            val box = anchor.closest(
                "article,li,.series-card,.manga-card,.comic-card,[class*=series-card],[class*=manga-card],[class*=comic-card],[class*=series-item]"
            ) ?: anchor.parent()
            val image = anchor.selectFirst("img") ?: box?.selectFirst("img")
            val title = firstNonBlank(
                anchor.attr("title").trim(),
                anchor.attr("aria-label").trim(),
                anchor.text().trim(),
                image?.attr("alt")?.trim(),
                box?.selectFirst("h1,h2,h3,h4,[class*=title]")?.text()?.trim()
            ) ?: continue
            if (!isValidTitle(title)) continue
            if (query.isNotBlank() && !matchesQuery(query, title + " " + url + " " + (image?.attr("alt") ?: ""))) continue

            val cover = image?.let { bestImageUrl(it, document.location()) }.orEmpty()
            val id = encodeUrl(url, "procomic:")
            results.putIfAbsent(
                id,
                MangaItem(
                    id = id,
                    title = title,
                    altTitles = image?.attr("alt")?.trim()?.takeIf { it.isNotBlank() && !it.equals(title, true) }?.let(::listOf).orEmpty(),
                    coverUrl = cover,
                    author = box?.selectFirst("[class*=author]")?.text()?.trim().orEmpty(),
                    description = box?.selectFirst("[class*=description],[class*=summary]")?.text()?.trim().orEmpty(),
                    sourceId = source.id,
                    sourceName = source.name,
                    sourceBaseUrl = canonicalBase(source.baseUrl)
                )
            )
            if (results.size >= MAX_RESULTS) break
        }
        return results.values.toList()
    }

    private fun collectChapterDocuments(
        startUrl: String,
        base: String,
        maxPages: Int
    ): List<Document> {
        val first = fetchDocument(startUrl, base) ?: return emptyList()
        val queue = ArrayDeque<String>()
        val visited = LinkedHashSet<String>()
        val docs = mutableListOf<Document>()
        queue.add(startUrl)
        while (queue.isNotEmpty() && docs.size < maxPages) {
            val url = queue.removeFirst()
            if (!visited.add(url)) continue
            val doc = if (url == startUrl) first else fetchDocument(url, base) ?: continue
            docs.add(doc)
            for (link in paginationLinks(doc, startUrl)) {
                if (!visited.contains(link) && !queue.contains(link) && docs.size + queue.size < maxPages) {
                    queue.addLast(link)
                }
            }
        }
        return docs
    }

    private fun extractChapterLinks(document: Document, seriesUrl: String): List<Pair<String, String>> {
        val elements = LinkedHashSet<Element>()
        val selectors = listOf(
            ".chapter-list a[href]",
            ".chapters-list a[href]",
            ".chapter-item a[href]",
            ".chapter-row a[href]",
            ".chapter-entry a[href]",
            "[class*=chapter-list] a[href]",
            "[class*=chapter-item] a[href]",
            "a[href*='/chapter/']",
            "a[href*='/chapters/']",
            "a[href*='/chapter-']",
            "a[href*='/episode/']",
            "a[href*='/read/']",
            "a[href*='/ar/chapter/']"
        )
        selectors.forEach { elements.addAll(document.select(it)) }
        if (elements.isEmpty()) {
            elements.addAll(document.select("a[href]").filter {
                looksLikeChapterLink(it.text(), it.attr("href"))
            })
        }

        val links = LinkedHashMap<String, String>()
        for (element in elements) {
            val rawHref = firstNonBlank(element.absUrl("href"), element.attr("href")) ?: continue
            val url = absoluteUrl(rawHref, document.location())
            if (!url.startsWith("http", true) || !sameHost(url, seriesUrl) || url.trimEnd('/') == seriesUrl.trimEnd('/')) continue
            if (!looksLikeChapterLink(element.text(), url)) continue
            val label = firstNonBlank(
                element.text().trim(),
                element.attr("title").trim(),
                element.attr("aria-label").trim(),
                element.selectFirst("span,strong")?.text()?.trim()
            ) ?: ""
            if (extractChapterNumber(label, url) == null) continue
            links.putIfAbsent(url, label)
        }
        return links.entries.map { it.key to it.value }
    }

    private fun paginationLinks(document: Document, seriesUrl: String): List<String> {
        val urls = LinkedHashSet<String>()
        val seriesPath = runCatching { URI(seriesUrl).path.orEmpty().trimEnd('/') }.getOrDefault("")
        for (a in document.select("a[href]")) {
            val href = absoluteUrl(a.absUrl("href").ifBlank { a.attr("href") }, document.location())
            if (!sameHost(href, seriesUrl)) continue
            val path = runCatching { URI(href).path.orEmpty().trimEnd('/') }.getOrDefault("")
            val text = (a.text() + " " + a.attr("rel") + " " + a.attr("aria-label") + " " + a.className()).lowercase(Locale.ROOT)
            val isNext = a.attr("rel").equals("next", true) || text.contains("next") || text.contains("التالي")
            val isPage = (path == seriesPath && (href.contains("?page=", true) || href.contains("&page=", true))) ||
                path.startsWith(seriesPath + "/page/")
            if ((isNext || isPage) && href != seriesUrl) urls.add(href)
        }
        return urls.toList()
    }

    private fun fetchDocument(url: String, base: String): Document? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ar,en-US,en;q=0.9")
                .header("Referer", arabicBase(base) + "/")
                .apply {
                    runCatching { CookieManager.getInstance().getCookie(url) }
                        .getOrNull()?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
                }
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank() || looksLikeHumanVerification(body)) {
                    null
                } else {
                    Jsoup.parse(body, response.request.url.toString())
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun bestImageUrl(image: Element, pageUrl: String): String {
        val srcSet = firstNonBlank(image.attr("data-srcset"), image.attr("srcset")).orEmpty()
        val bestFromSet = srcSet.split(",")
            .map { it.trim().substringBefore(" ").trim() }
            .lastOrNull { it.isNotBlank() }
        return firstNonBlank(
            image.attr("data-src"),
            image.attr("data-lazy-src"),
            image.attr("data-original"),
            image.attr("data-url"),
            bestFromSet,
            image.attr("src")
        )?.let { absoluteUrl(it, pageUrl) }.orEmpty()
    }

    private fun valueAfterLabel(document: Document, labels: List<String>): String? {
        for (element in document.select("dt,strong,b,.label,[class*=label],[class*=meta]")) {
            val text = element.text().trim()
            if (labels.none { text.contains(it, ignoreCase = true) }) continue
            val nearby = firstNonBlank(
                element.nextElementSibling()?.text()?.trim(),
                element.parent()?.nextElementSibling()?.text()?.trim(),
                element.parent()?.selectFirst("a,span,.value")?.text()?.trim()
            )
            if (!nearby.isNullOrBlank() && nearby != text) return nearby
        }
        return null
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private fun mapStatus(value: String, fallback: String): String {
        val text = value.lowercase(Locale.ROOT)
        return when {
            text.contains("مكتمل") || text.contains("مكتملة") || text.contains("completed") || text.contains("finished") -> "COMPLETED"
            text.contains("متوقف") || text.contains("متوقفة") || text.contains("hiatus") || text.contains("paused") -> "HIATUS"
            text.contains("ملغ") || text.contains("cancelled") || text.contains("canceled") -> "CANCELLED"
            text.contains("مستمر") || text.contains("جاري") || text.contains("ongoing") || text.contains("publishing") -> "ONGOING"
            fallback.isNotBlank() -> fallback
            else -> "UNKNOWN"
        }
    }

    private fun extractChapterNumber(label: String, url: String): Float? {
        val labelPatterns = listOf(
            Regex("""(?:chapter|chap|ch\.?|episode|ep\.?|الفصل|الحلقة|شابتر)\s*[-#:：]*\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:^|\s)#?\s*(\d+(?:\.\d+)?)\s*(?:$|الفصل|chapter|ch\b)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in labelPatterns) {
            pattern.find(label)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        }
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        val pathPatterns = listOf(
            Regex("""/(?:chapter|chapters|chapter-|chap|ch|episode|ep|read)/[^/?#]*?(\d+(?:\.\d+)?)(?:/|$|[-.])""", RegexOption.IGNORE_CASE),
            Regex("""(?:chapter|chap|ch|episode|ep)[-_/ ]*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in pathPatterns) {
            pattern.find(path)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        }
        return null
    }

    private fun looksLikeChapterLink(text: String, href: String): Boolean {
        val combined = (text + " " + href).lowercase(Locale.ROOT)
        return combined.contains("chapter") || combined.contains("/chapters/") ||
            combined.contains("/chapter/") || combined.contains("chapter-") ||
            combined.contains("episode") || combined.contains("/read/") ||
            combined.contains("الفصل") || combined.contains("الحلقة") ||
            Regex("""(^|[^a-z])ch\.?\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(combined)
    }

    private fun matchesQuery(query: String, text: String): Boolean {
        val tokens = normalize(query).split(" ").filter { it.length >= 2 }
        if (tokens.isEmpty()) return true
        val candidate = normalize(text)
        val matched = tokens.count { candidate.contains(it) }
        return if (tokens.size == 1) matched == 1 else matched >= minOf(2, tokens.size)
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("").trim().replace(Regex("""\s+"""), " ")

    private fun isValidTitle(value: String): Boolean {
        val title = value.trim()
        if (title.length < 2 || title.length > 240) return false
        val normalized = title.lowercase(Locale.ROOT)
        return normalized !in setOf("home", "search", "next", "previous", "الرئيسية", "بحث", "التالي", "السابق") &&
            !normalized.startsWith("chapter ") && title.any { it.isLetter() }
    }

    private fun isSeriesUrl(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        return Regex("""/(?:[a-z]{2}/)?series/[^/?#]+/\d+(?:/|$)""", RegexOption.IGNORE_CASE).containsMatchIn(path + "/")
    }

    private fun isImageUrl(url: String): Boolean =
        url.startsWith("https://", true) || url.startsWith("http://", true)

    private fun isLikelyDecoration(url: String, image: Element?): Boolean {
        val value = (url + " " + image?.attr("alt").orEmpty() + " " + image?.attr("class").orEmpty())
            .lowercase(Locale.ROOT)
        return value.contains("avatar") || value.contains("logo") || value.contains("icon") ||
            value.contains("banner") || value.contains("profile")
    }

    private fun sameHost(url: String, reference: String): Boolean {
        val a = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.") }.getOrDefault("")
        val b = runCatching { URI(reference).host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.") }.getOrDefault("")
        return a.isNotBlank() && a == b
    }

    private fun absoluteUrl(value: String, base: String): String {
        val raw = value.trim().replace("&amp;", "&")
        if (raw.isBlank()) return ""
        return try {
            val candidate = URI(raw)
            if (candidate.isAbsolute) candidate.toString()
            else URI(base).resolve(candidate).toString()
        } catch (_: Exception) {
            raw
        }
    }

    private fun canonicalBase(input: String): String {
        val value = input.trim().trimEnd('/')
        val uri = runCatching { URI(if (value.contains("://")) value else "https://" + value) }.getOrNull()
        val host = uri?.host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.")
        if (host == "procomic.net" || host.endsWith(".procomic.net") ||
            host == "prochan.net" || host.endsWith(".prochan.net")) {
            return "https://procomic.pro"
        }
        if (uri != null && host.isNotBlank()) {
            val path = uri.path.orEmpty().trimEnd('/')
            return uri.scheme.orEmpty().ifBlank { "https" } + "://" + host +
                if (path == "/ar") "/ar" else ""
        }
        return value
    }

    private fun arabicBase(base: String): String =
        if (runCatching { URI(base).path.orEmpty().trimEnd('/') }.getOrDefault("").endsWith("/ar")) base
        else base.trimEnd('/') + "/ar"

    private fun decodeUrl(id: String): String? {
        if (id.startsWith("http://") || id.startsWith("https://")) return id
        val prefixes = listOf("procomic:", "procomic-chapter:", "generic:", "generic-chapter:")
        val prefix = prefixes.firstOrNull { id.startsWith(it) } ?: return null
        return runCatching {
            String(Base64.decode(id.removePrefix(prefix), Base64.URL_SAFE), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun encodeUrl(url: String, prefix: String): String =
        prefix + Base64.encodeToString(
            url.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    private fun formatNumber(value: Float): String =
        if (value % 1.0f == 0.0f) value.toInt().toString() else value.toString()

    private fun looksLikeHumanVerification(body: String): Boolean {
        val text = body.lowercase(Locale.ROOT)
        val markers = listOf("cf-chl-", "cloudflare", "verify you are human", "checking your browser", "captcha", "attention required", "just a moment")
        return markers.count { text.contains(it) } >= 2
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val MAX_RESULTS = 300
        private const val MAX_CHAPTER_PAGES = 40

        fun isProComic(source: SourceItem?): Boolean {
            if (source == null) return false
            return isProComicUrl(source.baseUrl)
        }

        fun isProComicUrl(url: String): Boolean {
            val host = runCatching { URI(if (url.contains("://")) url else "https://" + url).host.orEmpty() }
                .getOrDefault("")
                .lowercase(Locale.ROOT)
                .removePrefix("www.")
            return host == "procomic.pro" || host.endsWith(".procomic.pro") ||
                host == "procomic.net" || host.endsWith(".procomic.net") ||
                host == "prochan.net" || host.endsWith(".prochan.net")
        }
    }
}
