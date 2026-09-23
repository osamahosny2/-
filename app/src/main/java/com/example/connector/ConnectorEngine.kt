package com.example.connector

import android.webkit.CookieManager
import com.example.model.MangaChapter
import com.example.model.MangaItem
import com.example.model.MangaPage
import com.example.model.SourceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ConnectorEngine {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    /**
     * Search manga from real sources added dynamically by the user.
     */
    suspend fun searchManga(query: String, source: SourceItem?): Result<List<MangaItem>> = withContext(Dispatchers.IO) {
        if (source == null) {
            return@withContext Result.success(emptyList())
        }
        try {
            val trimmedQuery = query.trim()
            val isMangadex = source.baseUrl.contains("mangadex.org", ignoreCase = true) ||
                    source.id.contains("mangadex", ignoreCase = true) ||
                    source.name.contains("mangadex", ignoreCase = true)

            val url = if (isMangadex) {
                if (trimmedQuery.isNotEmpty()) {
                    val encoded = URLEncoder.encode(trimmedQuery, "UTF-8")
                    "https://api.mangadex.org/manga?title=$encoded&limit=25&includes[]=cover_art&includes[]=author&contentRating[]=safe&contentRating[]=suggestive"
                } else {
                    "https://api.mangadex.org/manga?limit=25&order[followedCount]=desc&includes[]=cover_art&includes[]=author&contentRating[]=safe&contentRating[]=suggestive"
                }
            } else {
                if (trimmedQuery.isNotEmpty()) {
                    val encoded = URLEncoder.encode(trimmedQuery, "UTF-8")
                    "${source.baseUrl.trimEnd('/')}/search?q=$encoded"
                } else {
                    source.baseUrl
                }
            }

            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                if (!isMangadex) {
                    return@withContext Result.failure(Exception("تعذر جلب البيانات تلقائياً من ${source.name}. يمكنك فتح الموقع عبر المتصفح المدمج لحل الكابتشا والتصفح."))
                }
                return@withContext Result.failure(Exception("فشل الاتصال بالمصدر: رمز الاستجابة ${response.code}"))
            }

            if (isMangadex) {
                val json = JSONObject(responseBody)
                val dataArray = json.optJSONArray("data") ?: JSONArray()
                val mangaList = mutableListOf<MangaItem>()

                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val id = item.optString("id")
                    val attributes = item.optJSONObject("attributes") ?: JSONObject()

                    // Titles extraction (prefer Arabic or English)
                    val titleObj = attributes.optJSONObject("title") ?: JSONObject()
                    val titleAr = titleObj.optString("ar", "")
                    val titleEn = titleObj.optString("en", "")
                    val titleJa = titleObj.optString("ja-ro", "")

                    val title = when {
                        titleAr.isNotBlank() -> titleAr
                        titleEn.isNotBlank() -> titleEn
                        titleJa.isNotBlank() -> titleJa
                        else -> {
                            val firstKey = titleObj.keys().asSequence().firstOrNull()
                            if (firstKey != null) titleObj.optString(firstKey) else "مانجا غير معنونة"
                        }
                    }

                    // Alt titles
                    val altTitlesList = mutableListOf<String>()
                    val altTitlesArr = attributes.optJSONArray("altTitles")
                    if (altTitlesArr != null) {
                        for (j in 0 until altTitlesArr.length()) {
                            val altObj = altTitlesArr.getJSONObject(j)
                            for (key in altObj.keys()) {
                                val v = altObj.optString(key)
                                if (v.isNotBlank()) altTitlesList.add(v)
                            }
                        }
                    }

                    // Description
                    val descObj = attributes.optJSONObject("description") ?: JSONObject()
                    val description = descObj.optString("ar", descObj.optString("en", "لا يوجد وصف متاح لهذا العمل."))

                    val status = attributes.optString("status", "unknown")
                    val statusAr = when (status) {
                        "ongoing" -> "مستمر"
                        "completed" -> "مكتمل"
                        "hiatus" -> "متوقف مؤقتاً"
                        "cancelled" -> "ملغى"
                        else -> status
                    }

                    // Relationships (Cover Art & Author)
                    var coverFileName: String? = null
                    var authorName = "غير معروف"
                    val rels = item.optJSONArray("relationships") ?: JSONArray()
                    for (r in 0 until rels.length()) {
                        val rel = rels.getJSONObject(r)
                        val relType = rel.optString("type")
                        if (relType == "cover_art") {
                            val coverAttrs = rel.optJSONObject("attributes")
                            coverFileName = coverAttrs?.optString("fileName")
                        } else if (relType == "author") {
                            val authorAttrs = rel.optJSONObject("attributes")
                            val aName = authorAttrs?.optString("name")
                            if (!aName.isNullOrBlank()) authorName = aName
                        }
                    }

                    val coverUrl = if (!coverFileName.isNullOrBlank()) {
                        "https://uploads.mangadex.org/covers/$id/$coverFileName.512.jpg"
                    } else ""

                    // Tags / Genres
                    val tagsArr = attributes.optJSONArray("tags") ?: JSONArray()
                    val genres = mutableListOf<String>()
                    for (t in 0 until tagsArr.length()) {
                        val tagObj = tagsArr.getJSONObject(t)
                        val tagAttrs = tagObj.optJSONObject("attributes") ?: JSONObject()
                        val tagNameObj = tagAttrs.optJSONObject("name") ?: JSONObject()
                        val tagName = tagNameObj.optString("en")
                        if (tagName.isNotBlank()) genres.add(tagName)
                    }

                    mangaList.add(
                        MangaItem(
                            id = id,
                            title = title,
                            altTitles = altTitlesList,
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

                Result.success(mangaList)
            } else {
                try {
                    if (responseBody.trim().startsWith("{")) {
                        val json = JSONObject(responseBody)
                        val dataArray = json.optJSONArray("data") ?: json.optJSONArray("results") ?: JSONArray()
                        val mangaList = mutableListOf<MangaItem>()
                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.getJSONObject(i)
                            val id = item.optString("id", java.util.UUID.randomUUID().toString())
                            val title = item.optString("title", item.optString("name", "عمل بدون عنوان"))
                            val cover = item.optString("cover", item.optString("coverUrl", ""))
                            val desc = item.optString("description", "")
                            mangaList.add(
                                MangaItem(
                                    id = id,
                                    title = title,
                                    coverUrl = cover,
                                    description = desc,
                                    sourceId = source.id,
                                    sourceName = source.name
                                )
                            )
                        }
                        if (mangaList.isNotEmpty()) {
                            Result.success(mangaList)
                        } else {
                            Result.failure(Exception("لم يتم العثور على أعمال متوافقة من ${source.name}. يمكنك استخدام المتصفح المدمج لحل الكابتشا أو التصفح المباشر."))
                        }
                    } else {
                        Result.failure(Exception("تم الاتصال بـ ${source.name}. يمكنك فتح الموقع بالمتصفح المدمج لحل الكابتشا أو تصفح المحتوى."))
                    }
                } catch (e: Exception) {
                    Result.failure(Exception("تعذر معالجة الاستجابة من ${source.name}. يمكنك فتح الموقع بالمتصفح المدمج."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch real chapters for a specific manga.
     */
    suspend fun fetchChapters(mangaId: String): Result<List<MangaChapter>> = withContext(Dispatchers.IO) {
        try {
            // First fetch Arabic chapters, fallback/merge with English if needed
            val url = "https://api.mangadex.org/manga/$mangaId/feed?translatedLanguage[]=ar&translatedLanguage[]=en&order[chapter]=asc&limit=100&includes[]=scanlation_group"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                return@withContext Result.failure(Exception("تعذر جلب الفصول: رمز ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val dataArray = json.optJSONArray("data") ?: JSONArray()
            val chaptersList = mutableListOf<MangaChapter>()

            for (i in 0 until dataArray.length()) {
                val chapterObj = dataArray.getJSONObject(i)
                val id = chapterObj.optString("id")
                val attributes = chapterObj.optJSONObject("attributes") ?: JSONObject()

                val chapterStr = attributes.optString("chapter", "${i + 1}")
                val chapterNum = chapterStr.toFloatOrNull() ?: (i + 1).toFloat()
                val chapterTitle = attributes.optString("title", "")
                val pagesCount = attributes.optInt("pages", 0)
                val publishAt = attributes.optString("publishAt", "")
                val releaseDate = if (publishAt.length >= 10) publishAt.substring(0, 10) else ""

                var groupName = "فريق الترجمة"
                val rels = chapterObj.optJSONArray("relationships") ?: JSONArray()
                for (r in 0 until rels.length()) {
                    val rel = rels.getJSONObject(r)
                    if (rel.optString("type") == "scanlation_group") {
                        val gAttrs = rel.optJSONObject("attributes")
                        val gName = gAttrs?.optString("name")
                        if (!gName.isNullOrBlank()) groupName = gName
                    }
                }

                chaptersList.add(
                    MangaChapter(
                        id = id,
                        mangaId = mangaId,
                        chapterNumber = chapterNum,
                        title = chapterTitle,
                        releaseDate = releaseDate,
                        scanlationGroup = groupName,
                        pageCount = pagesCount
                    )
                )
            }

            Result.success(chaptersList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch real page image URLs for a chapter using the MangaDex At-Home server API.
     */
    suspend fun fetchChapterPages(chapterId: String): Result<List<MangaPage>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.mangadex.org/at-home/server/$chapterId"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                return@withContext Result.failure(Exception("تعذر جلب صفحات الفصل: رمز ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val baseUrl = json.optString("baseUrl")
            val chapterObj = json.optJSONObject("chapter") ?: JSONObject()
            val hash = chapterObj.optString("hash")
            val dataArray = chapterObj.optJSONArray("data") ?: JSONArray()

            val pages = mutableListOf<MangaPage>()
            for (i in 0 until dataArray.length()) {
                val fileName = dataArray.getString(i)
                val imageUrl = "$baseUrl/data/$hash/$fileName"
                pages.add(
                    MangaPage(
                        pageNumber = i + 1,
                        imageUrl = imageUrl
                    )
                )
            }

            Result.success(pages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads an image byte array from a URL.
     */
    suspend fun downloadImageBytes(imageUrl: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(imageUrl)
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("فشل تنزيل الصورة: رمز ${response.code}"))
            }
            val bytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("استجابة فارغة"))
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "ar,en-US,en;q=0.9")

        // Include webview cookies if available
        try {
            val cookies = CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrBlank()) {
                builder.header("Cookie", cookies)
            }
        } catch (ignored: Exception) {}

        return builder.build()
    }
}
