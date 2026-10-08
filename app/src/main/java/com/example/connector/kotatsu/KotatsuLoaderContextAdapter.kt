package com.example.connector.kotatsu

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect as AndroidRect
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap as KotatsuBitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect as KotatsuRect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.webview.InterceptedRequest
import org.koitharu.kotatsu.parsers.webview.InterceptionConfig
import java.io.ByteArrayOutputStream

class KotatsuBrowserActionRequiredException(val url: String) :
    Exception("Browser action required for: $url")

class AndroidCookieJar : CookieJar {
    private val cookieManager = CookieManager.getInstance()
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = runCatching { cookieManager.getCookie(url.toString()) }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val pieces = part.trim().split("=", limit = 2)
            if (pieces.size != 2 || pieces[0].isBlank()) return@mapNotNull null
            runCatching {
                Cookie.Builder().name(pieces[0].trim()).value(pieces[1].trim())
                    .domain(url.host).path("/").build()
            }.getOrNull()
        }
    }
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            runCatching {
                cookieManager.setCookie(url.toString(), cookie.name + "=" + cookie.value + "; Path=" + cookie.path)
            }
        }
        runCatching { cookieManager.flush() }
    }
}

private class AndroidKotatsuBitmap(val bitmap: android.graphics.Bitmap) : KotatsuBitmap {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun drawBitmap(sourceBitmap: KotatsuBitmap, src: KotatsuRect, dst: KotatsuRect) {
        val source = (sourceBitmap as? AndroidKotatsuBitmap)?.bitmap ?: return
        val sourceRect = AndroidRect(src.left, src.top, src.right, src.bottom)
        val destRect = AndroidRect(dst.left, dst.top, dst.right, dst.bottom)
        Canvas(bitmap).drawBitmap(source, sourceRect, destRect, null)
    }
}

class AndroidMangaLoaderContext(
    httpClientImpl: OkHttpClient,
    private val userAgent: String
) : MangaLoaderContext() {
    private val config = object : MangaSourceConfig {
        override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
    }
    override val httpClient: OkHttpClient = httpClientImpl
    override val cookieJar: CookieJar = AndroidCookieJar()
    override suspend fun evaluateJs(script: String): String? = evaluateJs("", script, 15_000L)
    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? =
        throw UnsupportedOperationException("JavaScript execution is delegated to Iruma WebView")
    override fun requestBrowserAction(parser: MangaParser, url: String): Nothing =
        throw KotatsuBrowserActionRequiredException(url)
    override fun requestCloudflareVerification(parser: MangaParser, url: String): Nothing =
        throw KotatsuBrowserActionRequiredException(url)
    override fun getConfig(source: MangaSource): MangaSourceConfig = config
    override fun getDefaultUserAgent(): String = userAgent
    override fun redrawImageResponse(response: Response, redraw: (image: KotatsuBitmap) -> KotatsuBitmap): Response {
        val body = response.body ?: return response
        val bytes = body.bytes()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return response
        val result = redraw(AndroidKotatsuBitmap(decoded))
        val output = ByteArrayOutputStream()
        val resultBitmap = (result as? AndroidKotatsuBitmap)?.bitmap ?: decoded
        resultBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        return response.newBuilder().body(output.toByteArray().toResponseBody(body.contentType())).build()
    }
    override fun createBitmap(width: Int, height: Int): KotatsuBitmap =
        AndroidKotatsuBitmap(android.graphics.Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888))
    override suspend fun interceptWebViewRequests(url: String, interceptorScript: String, timeout: Long): List<InterceptedRequest> =
        throw UnsupportedOperationException("WebView request interception is delegated to Iruma WebView")
    override suspend fun interceptWebViewRequests(url: String, config: InterceptionConfig): List<InterceptedRequest> =
        throw UnsupportedOperationException("WebView request interception is delegated to Iruma WebView")
    override suspend fun captureWebViewUrls(pageUrl: String, urlPattern: Regex, timeout: Long): List<String> =
        throw UnsupportedOperationException("WebView URL capture is delegated to Iruma WebView")
}
