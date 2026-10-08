package com.example.connector.kotatsu

import com.example.model.MangaChapter
import com.example.model.MangaItem
import com.example.model.MangaPage
import com.example.model.SourceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter as KotatsuChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class KotatsuSourceEngine {
    private val client = OkHttpClient.Builder()
        .cookieJar(AndroidCookieJar())
        .followRedirects(true)
        .build()

    private val context = AndroidMangaLoaderContext(
        client,
        "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    )

    private val parserCache = ConcurrentHashMap<String, MangaParser?>()

    suspend fun searchManga(query: String, source: SourceItem, page: Int): Result<List<MangaItem>>? =
        withContext(Dispatchers.IO) {
            val parser = resolveParser(source) ?: return@withContext null
            try {
                val offset = ((page - 1).coerceAtLeast(0)) * PAGE_SIZE
                val filter = if (query.isBlank()) MangaListFilter.EMPTY
                else MangaListFilter(query = query.trim())
                val sort = if (query.isBlank()) SortOrder.UPDATED else SortOrder.RELEVANCE
                Result.success(parser.getList(offset, sort, filter).map { mapManga(it, source) })
            } catch (e: KotatsuBrowserActionRequiredException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(Exception("Kotatsu parser " + parser.source.name + " failed: " + e.message, e))
            }
        }

    suspend fun fetchDetails(manga: MangaItem, source: SourceItem): Result<MangaItem>? =
        withContext(Dispatchers.IO) {
            val parser = resolveParser(source) ?: return@withContext null
            try {
                val url = decodeUrl(manga.id)
                    ?: return@withContext Result.failure(IllegalArgumentException("Kotatsu manga id is invalid"))
                Result.success(mapManga(parser.getDetails(createSeed(manga, url, parser)), source))
            } catch (e: KotatsuBrowserActionRequiredException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(Exception("Kotatsu details failed: " + e.message, e))
            }
        }

    suspend fun fetchChapters(mangaId: String, source: SourceItem): Result<List<MangaChapter>>? =
        withContext(Dispatchers.IO) {
            val parser = resolveParser(source) ?: return@withContext null
            try {
                val url = decodeUrl(mangaId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Kotatsu manga id is invalid"))
                val details = parser.getDetails(
                    createSeed(
                        MangaItem(mangaId, "", sourceId = source.id, sourceName = source.name),
                        url,
                        parser
                    )
                )
                Result.success(
                    details.chapters.orEmpty()
                        .sortedWith(compareBy<KotatsuChapter> { it.number }.thenBy { it.title.orEmpty() })
                        .map { mapChapter(it, mangaId) }
                )
            } catch (e: KotatsuBrowserActionRequiredException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(Exception("Kotatsu chapters failed: " + e.message, e))
            }
        }

    suspend fun fetchPages(chapterId: String, source: SourceItem): Result<List<MangaPage>>? =
        withContext(Dispatchers.IO) {
            val parser = resolveParser(source) ?: return@withContext null
            try {
                val chapterUrl = decodeUrl(chapterId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Kotatsu chapter id is invalid"))
                val chapter = KotatsuChapter(
                    id = stableLong(chapterUrl),
                    title = null,
                    number = 0f,
                    volume = 0,
                    url = chapterUrl,
                    scanlator = null,
                    uploadDate = 0L,
                    branch = null,
                    source = parser.source
                )
                val pages = parser.getPages(chapter)
                Result.success(pages.mapIndexed { index, page ->
                    MangaPage(index + 1, parser.getPageUrl(page))
                })
            } catch (e: KotatsuBrowserActionRequiredException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(Exception("Kotatsu pages failed: " + e.message, e))
            }
        }

    private fun resolveParser(source: SourceItem): MangaParser? {
        val cacheKey = source.id + "|" + source.baseUrl.trimEnd('/')
        if (parserCache.containsKey(cacheKey)) return parserCache[cacheKey]

        val byTitle = runCatching {
            MangaParserSource.values().firstOrNull {
                !it.isBroken && it.title.equals(source.name.trim(), ignoreCase = true)
            }
        }.getOrNull()

        if (byTitle != null) {
            val parser = runCatching { context.newParserInstance(byTitle) }.getOrNull()
            parserCache[cacheKey] = parser
            return parser
        }

        val host = runCatching {
            URI(source.baseUrl).host?.lowercase(Locale.ROOT)?.removePrefix("www.")
        }.getOrNull()

        if (host.isNullOrBlank()) {
            parserCache[cacheKey] = null
            return null
        }

        var resolved: MangaParser? = null
        for (candidate in MangaParserSource.values()) {
            if (candidate.isBroken) continue
            val parser = runCatching { context.newParserInstance(candidate) }.getOrNull() ?: continue
            val parserHost = runCatching {
                URI(parser.domain).host?.lowercase(Locale.ROOT)?.removePrefix("www.")
            }.getOrNull()
            if (parserHost == host) {
                resolved = parser
                break
            }
        }
        parserCache[cacheKey] = resolved
        return resolved
    }

    private fun mapManga(manga: Manga, source: SourceItem): MangaItem =
        MangaItem(
            id = encodeUrl(manga.publicUrl),
            title = manga.title,
            altTitles = manga.altTitles.toList(),
            coverUrl = manga.largeCoverUrl ?: manga.coverUrl.orEmpty(),
            author = manga.authors.joinToString(", "),
            status = manga.state?.let(::mapState) ?: "UNKNOWN",
            description = manga.description.orEmpty(),
            genres = manga.tags.map { it.toString() },
            sourceId = source.id,
            sourceName = source.name,
            totalChaptersCount = manga.chapters?.size ?: 0
        )

    private fun mapChapter(chapter: KotatsuChapter, mangaId: String): MangaChapter =
        MangaChapter(
            id = encodeUrl(chapter.url),
            mangaId = mangaId,
            chapterNumber = chapter.number,
            title = chapter.title.orEmpty(),
            releaseDate = if (chapter.uploadDate > 0L)
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(chapter.uploadDate))
            else "",
            scanlationGroup = chapter.scanlator.orEmpty(),
            pageCount = 0
        )

    private fun createSeed(item: MangaItem, url: String, parser: MangaParser): Manga =
        Manga(
            id = stableLong(url),
            title = item.title,
            altTitles = item.altTitles.toSet(),
            url = url,
            publicUrl = url,
            rating = -1f,
            contentRating = null,
            coverUrl = item.coverUrl.ifBlank { null },
            tags = emptySet(),
            state = null,
            authors = item.author.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet(),
            largeCoverUrl = item.coverUrl.ifBlank { null },
            description = item.description.ifBlank { null },
            chapters = null,
            source = parser.source
        )

    private fun mapState(state: MangaState): String = when (state) {
        MangaState.ONGOING -> "ONGOING"
        MangaState.FINISHED -> "COMPLETED"
        MangaState.ABANDONED -> "ABANDONED"
        MangaState.PAUSED -> "HIATUS"
        MangaState.UPCOMING -> "UPCOMING"
        MangaState.RESTRICTED -> "RESTRICTED"
    }

    private fun encodeUrl(url: String): String =
        "kotatsu:" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(url.toByteArray(Charsets.UTF_8))

    private fun decodeUrl(id: String): String? {
        if (!id.startsWith("kotatsu:")) return null
        return runCatching {
            String(
                java.util.Base64.getUrlDecoder().decode(id.removePrefix("kotatsu:")),
                Charsets.UTF_8
            )
        }.getOrNull()
    }

    private fun stableLong(value: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        var result = 0L
        repeat(8) { result = (result shl 8) or (digest[it].toLong() and 0xff) }
        return result
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
