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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class ConnectorEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    suspend fun searchManga(query: String, source: SourceItem?): Result<List<MangaItem>> =
        withContext(Dispatchers.IO) {
            if (source == null) return@withContext Result.success(emptyList())

            try {
                if (isMangaDex(source)) {
                    return@withContext searchMangaDex(query.trim(), source)
                }

                val q = query.trim()
                val encoded = URLEncoder.encode(q, "UTF-8")
                val base = source.baseUrl.trimEnd('/')
                val candidates = if (q.isBlank()) {
                    listOf(base)
                } else {
                    listOf(
                        base + "/search?q=" + encoded,
                        base + "/?s=" + encoded,
                        base + "/search/" + encoded + "/",
                        base + "/?post_type=wp-manga&s=" + encoded
                    )
                }

                for (url in candidates.distinct()) {
                    val response = execute(url)
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful || body.isBlank()) continue
                    if (looksLikeHumanVerification(body)) continue

                    val results = parseSearchDocument(Jsoup.parse(body, url), source)
                    if (results.isNotEmpty()) return@withContext Result.success(results)
                }

                Result.failure(
                    Exception(
                        "لم يتم العثور على أعمال من " + source.name +
                            ". إذا كان الموقع يطلب Cloudflare/CAPTCHA، افتحه بالمتصفح المدمج وأكمل التحقق يدوياً ثم أعد البحث."
                    )
                )
            } catch (e: Exception) {
                Result.failure(Exception("تعذر الاتصال بالمصدر " + source.name + ": " + e.message, e))
            }
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

            val response = execute(mangaUrl)
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                return@withContext Result.failure(
                    Exception("تعذر فتح صفحة العمل. أكمل التحقق في المتصفح المدمج ثم أعد المحاولة.")
                )
            }
            if (looksLikeHumanVerification(body)) {
                return@withContext Result.failure(
                    Exception("هذا الموقع يطلب تحققاً بشرياً. افتحه في المتصفح المدمج، أكمل التحقق يدوياً، ثم أعد المحاولة.")
                )
            }

            val document = Jsoup.parse(body, mangaUrl)
            val elements = document.select(
                "li.wp-manga-chapter a, .wp-manga-chapter a, " +
                    ".listing-chapters_wrap a, .chapter-list a, .chapters-list a, " +
                    ".chapter-item a, .row-content-chapter a, .eplister li a"
            ).toMutableList()

            if (elements.isEmpty()) {
                elements.addAll(
                    document.select("a[href]").filter {
                        looksLikeChapterLink(it.text(), it.attr("href"))
                    }
                )
            }

            val seen = HashSet<String>()
            val chapters = mutableListOf<MangaChapter>()

            for (element in elements) {
                val href = element.absUrl("href").ifBlank { element.attr("href") }
                if (!href.startsWith("http") || !seen.add(href)) continue

                val label = element.text().trim().ifBlank { "فصل" }
                val number = extractChapterNumber(label, href) ?: continue

                chapters.add(
                    MangaChapter(
                        id = genericChapterId(href),
                        mangaId = mangaId,
                        chapterNumber = number,
                        title = label,
                        releaseDate = "",
                        scanlationGroup = "المصدر",
                        pageCount = 0
                    )
                )
            }

            val sorted = chapters
                .distinctBy { it.id }
                .sortedWith(compareBy<MangaChapter> { it.chapterNumber }.thenBy { it.title })

            if (sorted.isEmpty()) {
                Result.failure(
                    Exception("لم أجد قائمة الفصول في صفحة العمل. قد يحتاج هذا الموقع إلى دعم مخصص.")
                )
            } else {
                Result.success(sorted)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchChapterPages(
        chapterId: String,
        source: SourceItem? = null
    ): Result<List<MangaPage>> = withContext(Dispatchers.IO) {
        try {
            if (isMangaDex(source) || !chapterId.startsWith("generic-chapter:")) {
                return@withContext fetchMangaDexPages(chapterId)
            }

            val chapterUrl = decodeId(chapterId, "generic-chapter:")
                ?: return@withContext Result.failure(Exception("رابط الفصل غير صالح."))

            val response = execute(chapterUrl)
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                return@withContext Result.failure(
                    Exception("تعذر فتح الفصل. أكمل التحقق في المتصفح المدمج ثم أعد المحاولة.")
                )
            }
            if (looksLikeHumanVerification(body)) {
                return@withContext Result.failure(
                    Exception("هذا الفصل محمي بتحقق بشري. أكمل التحقق يدوياً في المتصفح المدمج ثم أعد المحاولة.")
                )
            }

            val document = Jsoup.parse(body, chapterUrl)
            val selectors = listOf(
                ".reading-content img",
                ".page-break img",
                ".reading-detail img",
                ".chapter-content img",
                ".text-left img",
                ".entry-content img",
                "img[data-src]",
                "img[data-lazy-src]",
                "img[data-original]"
            )

            val urls = LinkedHashSet<String>()
            for (selector in selectors) {
                for (element in document.select(selector)) {
                    val url = firstNonBlank(
                        element.absUrl("data-src"),
                        element.absUrl("data-lazy-src"),
                        element.absUrl("data-original"),
                        element.absUrl("src")
                    )
                    if (!url.isNullOrBlank() && url.startsWith("http")) urls.add(url)
                }
                if (urls.isNotEmpty()) break
            }

            if (urls.isEmpty()) {
                return@withContext Result.failure(
                    Exception("لم أجد صور صفحات في الفصل. قد يحتاج الموقع إلى JavaScript أو تحقق بشري داخل المتصفح.")
                )
            }

            Result.success(
                urls.mapIndexed { index, url ->
                    MangaPage(pageNumber = index + 1, imageUrl = url)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadImageBytes(imageUrl: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(buildRequest(imageUrl)).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("فشل تنزيل الصورة: رمز " + response.code)
                    )
                }
                val bytes = response.body?.bytes()
                    ?: return@withContext Result.failure(Exception("استجابة الصورة فارغة"))
                Result.success(bytes)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun searchMangaDex(query: String, source: SourceItem): Result<List<MangaItem>> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = if (query.isNotBlank()) {
            "https://api.mangadex.org/manga?title=" + encoded +
                "&limit=25&includes[]=cover_art&includes[]=author&contentRating[]=safe&contentRating[]=suggestive"
        } else {
            "https://api.mangadex.org/manga?limit=25&order[followedCount]=desc" +
                "&includes[]=cover_art&includes[]=author&contentRating[]=safe&contentRating[]=suggestive"
        }

        val response = execute(url)
        val body = response.body?.string()
        if (!response.isSuccessful || body.isNullOrBlank()) {
            return Result.failure(Exception("فشل الاتصال بالمصدر: رمز الاستجابة " + response.code))
        }

        val json = JSONObject(body)
        val dataArray = json.optJSONArray("data") ?: JSONArray()
        val mangaList = mutableListOf<MangaItem>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val id = item.optString("id")
            val attributes = item.optJSONObject("attributes") ?: JSONObject()

            val titleObj = attributes.optJSONObject("title") ?: JSONObject()
            val titleAr = titleObj.optString("ar", "")
            val titleEn = titleObj.optString("en", "")
            val titleJa = titleObj.optString("ja-ro", "")
            val title = when {
                titleAr.isNotBlank() -> titleAr
                titleEn.isNotBlank() -> titleEn
                titleJa.isNotBlank() -> titleJa
                else -> titleObj.keys().asSequence().firstOrNull()?.let { titleObj.optString(it) }
                    ?: "مانجا غير معنونة"
            }

            val altTitles = mutableListOf<String>()
            val alt = attributes.optJSONArray("altTitles")
            if (alt != null) {
                for (j in 0 until alt.length()) {
                    val obj = alt.getJSONObject(j)
                    for (key in obj.keys()) {
                        obj.optString(key).takeIf { it.isNotBlank() }?.let(altTitles::add)
                    }
                }
            }

            val descObj = attributes.optJSONObject("description") ?: JSONObject()
            val description = descObj.optString(
                "ar",
                descObj.optString("en", "لا يوجد وصف متاح لهذا العمل.")
            )

            val statusAr = when (attributes.optString("status", "unknown")) {
                "ongoing" -> "مستمر"
                "completed" -> "مكتمل"
                "hiatus" -> "متوقف مؤقتاً"
                "cancelled" -> "ملغى"
                else -> attributes.optString("status", "unknown")
            }

            var coverFileName: String? = null
            var authorName = "غير معروف"
            val rels = item.optJSONArray("relationships") ?: JSONArray()
            for (r in 0 until rels.length()) {
                val rel = rels.getJSONObject(r)
                when (rel.optString("type")) {
                    "cover_art" -> coverFileName = rel.optJSONObject("attributes")?.optString("fileName")
                    "author" -> rel.optJSONObject("attributes")?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                        authorName = it
                    }
                }
            }

            val coverUrl = if (!coverFileName.isNullOrBlank()) {
                "https://uploads.mangadex.org/covers/" + id + "/" + coverFileName + ".512.jpg"
            } else ""

            val genres = mutableListOf<String>()
            val tags = attributes.optJSONArray("tags") ?: JSONArray()
            for (t in 0 until tags.length()) {
                val tagAttrs = tags.getJSONObject(t).optJSONObject("attributes") ?: JSONObject()
                tagAttrs.optJSONObject("name")?.optString("en")?.takeIf { it.isNotBlank() }?.let(genres::add)
            }

            mangaList.add(
                MangaItem(
                    id = id,
                    title = title,
                    altTitles = altTitles,
                    coverUrl = coverUrl,
                    author = authorName,
                    status = statusAr,
                    description = description,
                    genres = genres.take(6),
                    sourceId = source.id,
                    sourceName = source.name
                )
            )
        }
        return Result.success(mangaList)
    }

    private fun fetchMangaDexChapters(mangaId: String): Result<List<MangaChapter>> {
        val url = "https://api.mangadex.org/manga/" + mangaId +
            "/feed?translatedLanguage[]=ar&translatedLanguage[]=en&order[chapter]=asc&limit=500&includes[]=scanlation_group"
        val response = execute(url)
        val body = response.body?.string()
        if (!response.isSuccessful || body.isNullOrBlank()) {
            return Result.failure(Exception("تعذر جلب الفصول: رمز " + response.code))
        }

        val json = JSONObject(body)
        val data = json.optJSONArray("data") ?: JSONArray()
        val chapters = mutableListOf<MangaChapter>()

        for (i in 0 until data.length()) {
            val chapter = data.getJSONObject(i)
            val attributes = chapter.optJSONObject("attributes") ?: JSONObject()
            val id = chapter.optString("id")
            val numText = attributes.optString("chapter", (i + 1).toString())
            val number = numText.toFloatOrNull() ?: (i + 1).toFloat()
            val published = attributes.optString("publishAt", "")
            val releaseDate = if (published.length >= 10) published.substring(0, 10) else ""

            var group = "فريق الترجمة"
            val rels = chapter.optJSONArray("relationships") ?: JSONArray()
            for (r in 0 until rels.length()) {
                val rel = rels.getJSONObject(r)
                if (rel.optString("type") == "scanlation_group") {
                    rel.optJSONObject("attributes")?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                        group = it
                    }
                }
            }

            chapters.add(
                MangaChapter(
                    id = id,
                    mangaId = mangaId,
                    chapterNumber = number,
                    title = attributes.optString("title", ""),
                    releaseDate = releaseDate,
                    scanlationGroup = group,
                    pageCount = attributes.optInt("pages", 0)
                )
            )
        }
        return Result.success(chapters)
    }

    private fun fetchMangaDexPages(chapterId: String): Result<List<MangaPage>> {
        val response = execute("https://api.mangadex.org/at-home/server/" + chapterId)
        val body = response.body?.string()
        if (!response.isSuccessful || body.isNullOrBlank()) {
            return Result.failure(Exception("تعذر جلب صفحات الفصل: رمز " + response.code))
        }

        val json = JSONObject(body)
        val baseUrl = json.optString("baseUrl")
        val chapter = json.optJSONObject("chapter") ?: JSONObject()
        val hash = chapter.optString("hash")
        val data = chapter.optJSONArray("data") ?: JSONArray()

        return Result.success(
            (0 until data.length()).map { index ->
                MangaPage(
                    pageNumber = index + 1,
                    imageUrl = baseUrl + "/data/" + hash + "/" + data.getString(index)
                )
            }
        )
    }

    private fun parseSearchDocument(document: Document, source: SourceItem): List<MangaItem> {
        val selectors = listOf(
            "div.c-tabs-item__content div.post-title a",
            ".manga__item .post-title a",
            ".page-item-detail.manga a",
            ".item-summary .post-title a",
            ".row.c-tabs-item__content a",
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
            if (!href.startsWith("http")) continue
            if (!sameSite(href, source.baseUrl)) continue
            if (!seen.add(href)) continue

            val container = anchor.closest(
                ".page-item-detail, .manga__item, .c-tabs-item__content, article, .row"
            )
            val title = firstNonBlank(
                anchor.text().trim(),
                container?.selectFirst(".post-title, .item-summary, h1, h2, h3, h4")?.text()?.trim(),
                anchor.selectFirst("img")?.attr("alt")?.trim()
            ) ?: continue

            if (title.length < 2 || looksLikeNavigation(title)) continue

            val cover = container?.selectFirst("img")?.let { imageUrl(it) }.orEmpty()
            val description = container?.selectFirst(
                ".summary-content, .post-content, .description, .summary__content"
            )?.text()?.trim().orEmpty()
            val author = container?.selectFirst(
                ".author-content a, .author a, .mg_author a"
            )?.text()?.trim().orEmpty()

            results.add(
                MangaItem(
                    id = genericId(href),
                    title = title,
                    coverUrl = cover,
                    author = author,
                    description = description,
                    sourceId = source.id,
                    sourceName = source.name
                )
            )
            if (results.size >= 25) break
        }
        return results
    }

    private fun execute(url: String) = client.newCall(buildRequest(url)).execute()

    private fun buildRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "ar,en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")

        try {
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                builder.header("Cookie", it)
            }
        } catch (_: Exception) {
        }
        return builder.build()
    }

    private fun isMangaDex(source: SourceItem?): Boolean {
        if (source == null) return false
        return source.baseUrl.contains("mangadex.org", ignoreCase = true) ||
            source.id.contains("mangadex", ignoreCase = true) ||
            source.name.contains("mangadex", ignoreCase = true)
    }

    private fun looksLikeHumanVerification(body: String): Boolean {
        val text = body.lowercase(Locale.ROOT)
        val markers = listOf(
            "cf-chl-", "cloudflare", "verify you are human",
            "checking your browser", "captcha", "attention required",
            "just a moment", "security verification"
        )
        return markers.count { text.contains(it) } >= 2
    }

    private fun looksLikeChapterLink(text: String, href: String): Boolean {
        val combined = (text + " " + href).lowercase(Locale.ROOT)
        return combined.contains("chapter") ||
            combined.contains("chap-") ||
            combined.contains("/ch/") ||
            combined.contains("episode") ||
            combined.contains("/ep/") ||
            combined.contains("الفصل") ||
            Regex("""(^|[^a-z])ch\.?\s*\d+""").containsMatchIn(combined)
    }

    private fun extractChapterNumber(text: String, href: String): Float? {
        val combined = text + " " + href
        val patterns = listOf(
            Regex("""(?:chapter|chap|ch\.?|episode|ep\.?|الفصل)\s*[-#:]*\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""/(?:chapter|chap|ch|episode|ep)[-/]?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:^|\s)(\d+(?:\.\d+)?)\s""")
        )
        for (pattern in patterns) {
            pattern.find(combined)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        }
        return null
    }

    private fun looksLikeNavigation(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        return t in setOf(
            "home", "login", "register", "search", "next", "previous",
            "menu", "الرئيسية", "بحث"
        )
    }

    private fun imageUrl(image: Element): String =
        firstNonBlank(
            image.absUrl("data-src"),
            image.absUrl("data-lazy-src"),
            image.absUrl("data-original"),
            image.absUrl("src")
        ).orEmpty()

    private fun sameSite(url: String, baseUrl: String): Boolean {
        return try {
            val a = java.net.URI(url)
            val b = java.net.URI(baseUrl)
            a.host.equals(b.host, ignoreCase = true)
        } catch (_: Exception) {
            true
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private fun genericId(url: String): String =
        "generic:" + Base64.encodeToString(
            url.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    private fun genericChapterId(url: String): String =
        "generic-chapter:" + Base64.encodeToString(
            url.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    private fun decodeId(id: String, prefix: String): String? {
        if (!id.startsWith(prefix)) return null
        return try {
            String(
                Base64.decode(id.removePrefix(prefix), Base64.URL_SAFE),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }
}
