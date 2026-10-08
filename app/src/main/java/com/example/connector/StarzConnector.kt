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
import java.util.Locale
import java.util.concurrent.TimeUnit

class StarzConnector {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchManga(query: String, source: SourceItem, page: Int = 1): Result<List<MangaItem>> =
        withContext(Dispatchers.IO) {
            try {
                val base = source.baseUrl.trimEnd('/')
                val docs = if (query.isBlank()) {
                    fetchBrowse(base, page)
                } else {
                    fetchSearch(base, query.trim(), page)
                }
                val merged = LinkedHashMap<String, MangaItem>()
                docs.forEach { doc ->
                    parseCards(doc, source).forEach { merged.putIfAbsent(it.id, it) }
                }
                val results = merged.values.toList().take(500)
                if (results.isEmpty()) {
                    Result.failure(Exception("لم يتم العثور على أعمال في Manga Starz. أكمل التحقق يدوياً في المتصفح المدمج إذا ظهر ثم أعد المحاولة."))
                } else {
                    Result.success(results)
                }
            } catch (e: Exception) {
                Result.failure(Exception("تعذر قراءة Manga Starz: " + e.message, e))
            }
        }

    suspend fun fetchChapters(mangaId: String, source: SourceItem): Result<List<MangaChapter>> =
        withContext(Dispatchers.IO) {
            try {
                val mangaUrl = decodeId(mangaId, "generic:")
                    ?: return@withContext Result.failure(Exception("رابط Manga Starz غير صالح."))
                val document = fetchDocument(mangaUrl)
                    ?: return@withContext Result.failure(Exception("تعذر فتح صفحة العمل. أكمل التحقق يدوياً ثم أعد المحاولة."))
                val elements = document.select(
                    ".listing-chapters_wrap .wp-manga-chapter a, " +
                        ".wp-manga-chapter a, li.wp-manga-chapter a, " +
                        ".eplister li a, .chapters-list a, .chapter-list a"
                )
                val seen = HashSet<String>()
                val chapters = mutableListOf<MangaChapter>()
                for (element in elements) {
                    val href = absoluteUrl(element.attr("href"))
                    if (!isSameHost(href, mangaUrl) || !seen.add(href)) continue
                    val label = firstNonBlank(
                        element.text().trim(),
                        element.attr("title").trim(),
                        element.selectFirst("span, .chapter, .chapter-title")?.text()?.trim()
                    ) ?: continue
                    val number = extractChapterNumber(label, href) ?: continue
                    chapters += MangaChapter(
                        id = genericChapterId(href),
                        mangaId = mangaId,
                        chapterNumber = number,
                        title = label,
                        releaseDate = "",
                        scanlationGroup = "Manga Starz",
                        pageCount = 0
                    )
                }
                val sorted = chapters.distinctBy { it.id }
                    .sortedWith(compareBy<MangaChapter> { it.chapterNumber }.thenBy { it.title })
                if (sorted.isEmpty()) Result.failure(Exception("لم أجد فصول Manga Starz في صفحة العمل."))
                else Result.success(sorted)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر قراءة فصول Manga Starz: " + e.message, e))
            }
        }

    suspend fun fetchChapterPages(chapterId: String, source: SourceItem): Result<List<MangaPage>> =
        withContext(Dispatchers.IO) {
            try {
                val chapterUrl = decodeId(chapterId, "generic-chapter:")
                    ?: return@withContext Result.failure(Exception("رابط فصل Manga Starz غير صالح."))
                val document = fetchDocument(chapterUrl)
                    ?: return@withContext Result.failure(Exception("تعذر فتح الفصل. أكمل التحقق يدوياً ثم أعد المحاولة."))
                val urls = LinkedHashSet<String>()
                val selectors = listOf(
                    ".reading-content img",
                    ".reading-detail img",
                    ".page-break img",
                    ".text-left img",
                    ".entry-content img"
                )
                for (selector in selectors) {
                    document.select(selector).forEach { image ->
                        val url = bestImageUrl(image)
                        if (url.startsWith("http")) urls += url
                    }
                    if (urls.isNotEmpty()) break
                }
                if (urls.isEmpty()) Result.failure(Exception("لم أجد صور صفحات الفصل في Manga Starz."))
                else Result.success(urls.mapIndexed { index, url -> MangaPage(index + 1, url) })
            } catch (e: Exception) {
                Result.failure(Exception("تعذر قراءة صور الفصل من Manga Starz: " + e.message, e))
            }
        }

    private fun fetchBrowse(base: String, page: Int): List<Document> {
        val url = if (page <= 1) base + "/manga/" else base + "/manga/page/" + page + "/"
        return fetchDocument(url)?.let { listOf(it) }.orEmpty()
    }

    private fun fetchSearch(base: String, query: String, page: Int): List<Document> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val candidates = listOf(
            base + "/?s=" + encoded + "&post_type=wp-manga",
            base + "/manga/?s=" + encoded,
            base + "/?s=" + encoded,
            base + "/search/" + encoded + "/"
        )
        val first = candidates.asSequence()
            .mapNotNull { fetchDocument(it) }
            .firstOrNull { parseCards(it, sourceFor(base)).isNotEmpty() }
            ?: return emptyList()
        val docs = mutableListOf(first)
        val next = LinkedHashSet<String>()
        next += discoverNext(first, base)
        for (p in 2..page.coerceAtMost(8)) {
            next += base + "/manga/page/" + p + "/?s=" + encoded
            next += base + "/page/" + p + "/?s=" + encoded + "&post_type=wp-manga"
        }
        for (url in next) {
            if (docs.size >= 8) break
            val doc = fetchDocument(url) ?: continue
            if (parseCards(doc, sourceFor(base)).isNotEmpty()) docs += doc
        }
        return docs
    }

    private fun discoverNext(document: Document, base: String): List<String> =
        document.select(".next.page-numbers, .nav-links a, .wp-pagenavi a, a[rel=next]")
            .mapNotNull { absoluteUrl(it.attr("href")).takeIf { url -> isSameHost(url, base) } }
            .distinct()

    private fun parseCards(document: Document, source: SourceItem): List<MangaItem> {
        val results = mutableListOf<MangaItem>()
        val seen = HashSet<String>()
        for (card in document.select(".page-item-detail.manga")) {
            val anchor = card.selectFirst(
                ".post-title a[href], .item-summary .post-title a[href], .item-thumb a[href]"
            ) ?: continue
            val href = absoluteUrl(anchor.attr("href"))
            if (!isMangaUrl(href, source.baseUrl) || !seen.add(href)) continue
            val title = firstNonBlank(
                anchor.attr("title").trim(),
                card.selectFirst(".post-title a, .post-title, h3, h4, h5")?.text()?.trim(),
                anchor.text().trim(),
                card.selectFirst("img")?.attr("alt")?.trim()
            ) ?: continue
            if (!isValidTitle(title)) continue
            val image = card.selectFirst(".item-thumb.c-image-hover img, .item-thumb img, img")
            results += MangaItem(
                id = genericId(href),
                title = title,
                coverUrl = image?.let { bestImageUrl(it) }.orEmpty(),
                author = card.selectFirst(".author-content a, .author a")?.text()?.trim().orEmpty(),
                description = card.selectFirst(".summary-content, .post-content, .description, .excerpt")?.text()?.trim().orEmpty(),
                sourceId = source.id,
                sourceName = source.name
            )
            if (results.size >= 500) break
        }
        return results
    }

    private fun fetchDocument(url: String): Document? {
        return try {
            val response = client.newCall(buildRequest(url)).execute()
            val body = response.body?.string().orEmpty()
            val ok = response.isSuccessful && body.isNotBlank() && !looksLikeHumanVerification(body)
            response.close()
            if (ok) Jsoup.parse(body, url) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Referer", BASE_URL + "/")
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "ar,en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
        try {
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        } catch (_: Exception) {
        }
        return builder.build()
    }

    private fun bestImageUrl(image: Element): String {
        val srcset = image.attr("srcset").trim()
        if (srcset.isNotBlank()) {
            val candidate = srcset.split(",")
                .map { it.trim().split(Regex("\\s+")) }
                .mapNotNull { it.firstOrNull()?.takeIf { value -> value.startsWith("http") || value.startsWith("/") } }
                .lastOrNull()
            if (!candidate.isNullOrBlank()) return absoluteUrl(candidate)
        }
        return firstNonBlank(
            image.attr("data-src"),
            image.attr("data-lazy-src"),
            image.attr("data-original"),
            image.attr("src")
        )?.let { absoluteUrl(it) }.orEmpty()
    }

    private fun isMangaUrl(url: String, baseUrl: String): Boolean {
        if (!isSameHost(url, baseUrl)) return false
        val path = try { URI(url).path.orEmpty().trimEnd('/') } catch (_: Exception) { return false }
        if (!path.startsWith("/manga/")) return false
        if (path == "/manga" || path.matches(Regex("/manga/page/\\d+"))) return false
        return true
    }

    private fun isValidTitle(title: String): Boolean {
        val t = title.trim()
        if (t.length < 2) return false
        val lower = t.lowercase(Locale.ROOT)
        if (lower in setOf(
                "home", "login", "register", "search", "next", "previous", "menu",
                "قائمة المانجا", "التالي", "الرئيسية", "بحث"
            )
        ) return false
        if (t.all { it.isDigit() || it == '.' || it == '-' || it == '_' }) return false
        return true
    }

    private fun extractChapterNumber(text: String, href: String): Float? {
        val combined = text + " " + href
        val patterns = listOf(
            Regex("""(?:chapter|chap|ch\.?|episode|ep\.?|الفصل)\s*[-#:]*\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""/(?:chapter|chap|ch|episode|ep)[-/]?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""/(\d+(?:\.\d+)?)/?$""")
        )
        for (pattern in patterns) {
            pattern.find(combined)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        }
        return null
    }

    private fun absoluteUrl(raw: String): String {
        if (raw.startsWith("http")) return raw
        return try { URI(BASE_URL + "/").resolve(raw).toString() } catch (_: Exception) { raw }
    }

    private fun isSameHost(url: String, base: String): Boolean =
        try { URI(url).host.equals(URI(base).host, ignoreCase = true) } catch (_: Exception) { false }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private fun genericId(url: String): String =
        "generic:" + Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun genericChapterId(url: String): String =
        "generic-chapter:" + Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decodeId(id: String, prefix: String): String? {
        if (!id.startsWith(prefix)) return null
        return try { String(Base64.decode(id.removePrefix(prefix), Base64.URL_SAFE), Charsets.UTF_8) } catch (_: Exception) { null }
    }

    private fun sourceFor(base: String) = SourceItem("starz", "starzmanga", base)

    private fun looksLikeHumanVerification(body: String): Boolean {
        val text = body.lowercase(Locale.ROOT)
        val markers = listOf("cf-chl-", "cloudflare", "verify you are human", "checking your browser", "captcha", "attention required", "just a moment", "security verification")
        return markers.count { text.contains(it) } >= 2
    }

    companion object {
        const val BASE_URL = "https://starzmanga.com"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        fun isStarz(source: SourceItem?): Boolean =
            source?.baseUrl?.contains("starzmanga.com", true) == true ||
                source?.baseUrl?.contains("manga-starz.net", true) == true ||
                source?.name?.contains("starzmanga", true) == true

        fun isStarzImageUrl(url: String): Boolean =
            url.contains("starz.starzmanga.com", true) ||
                url.contains("starz.manga-starz.net", true)

        fun imageHeaders(): Map<String, String> =
            mapOf("User-Agent" to BROWSER_USER_AGENT, "Referer" to (BASE_URL + "/"))
    }
}
