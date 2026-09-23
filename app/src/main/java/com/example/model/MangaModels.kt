package com.example.model

import java.io.Serializable

data class MangaItem(
    val id: String,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val coverUrl: String = "",
    val author: String = "",
    val status: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val sourceId: String = "mangadex",
    val sourceName: String = "MangaDex",
    val inLibrary: Boolean = false,
    val categoryIds: List<String> = emptyList(),
    val addedAt: Long = 0L,
    val totalChaptersCount: Int = 0
) : Serializable

data class MangaCategory(
    val id: String,
    val name: String,
    val order: Int = 0
) : Serializable

data class MangaChapter(
    val id: String,
    val mangaId: String,
    val chapterNumber: Float,
    val title: String,
    val releaseDate: String = "",
    val scanlationGroup: String = "",
    val pageCount: Int = 0,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val isDownloaded: Boolean = false,
    val lastReadPage: Int = 0,
    val localDirectory: String? = null
) : Serializable {
    val displayTitle: String
        get() = if (title.isNotBlank()) {
            "فصل ${formatChapterNumber(chapterNumber)}: $title"
        } else {
            "فصل ${formatChapterNumber(chapterNumber)}"
        }

    companion object {
        fun formatChapterNumber(num: Float): String {
            return if (num % 1.0f == 0.0f) {
                num.toInt().toString()
            } else {
                num.toString()
            }
        }
    }
}

data class MangaPage(
    val pageNumber: Int,
    val imageUrl: String,
    val localPath: String? = null
) : Serializable

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}

data class DownloadTask(
    val id: String,
    val chapterId: String,
    val chapterTitle: String,
    val mangaId: String,
    val mangaTitle: String,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f,
    val totalPages: Int = 0,
    val downloadedPages: Int = 0,
    val errorMsg: String? = null
) : Serializable

enum class ReadingMode(val titleAr: String, val descriptionAr: String) {
    WEBTOON("تمرير عمودي (ويب-تون)", "قراءة متصلة ومستمرة من الأعلى للأسفل"),
    RTL_PAGING("تقليب صفحات (يمين لليسار)", "الوضع الأصلي للمانجا اليابانية"),
    LTR_PAGING("تقليب صفحات (يسار لليمين)", "تقليب كلاسيكي من اليسار لليمين")
}

data class SourceItem(
    val id: String,
    val name: String,
    val baseUrl: String,
    val lang: String = "ar",
    val description: String = "",
    val requiresVerification: Boolean = false
) : Serializable

data class ReadingProgress(
    val mangaId: String,
    val chapterId: String,
    val mangaTitle: String,
    val chapterTitle: String,
    val lastPage: Int,
    val totalPages: Int,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
