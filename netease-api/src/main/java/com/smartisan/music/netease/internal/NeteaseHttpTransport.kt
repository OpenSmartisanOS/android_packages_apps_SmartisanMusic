package com.smartisan.music.netease.internal

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

internal data class NeteaseEndpoints(
    val music: HttpUrl,
    val interfaceMusic: HttpUrl,
    val interface3: HttpUrl,
) {
    val allowedOrigins: Set<String> = setOf(music, interfaceMusic, interface3)
        .mapTo(linkedSetOf()) { url -> url.originKey() }

    companion object {
        val Production = NeteaseEndpoints(
            music = "https://music.163.com/".toHttpUrl(),
            interfaceMusic = "https://interface.music.163.com/".toHttpUrl(),
            interface3 = "https://interface3.music.163.com/".toHttpUrl(),
        )
    }
}

internal class NeteaseHttpTransport(
    private val gson: Gson,
    cookieJar: CookieJar,
    private val endpoints: NeteaseEndpoints = NeteaseEndpoints.Production,
    connectTimeoutMillis: Long = 15_000,
    readTimeoutMillis: Long = 30_000,
    private val maxResponseBytes: Long = 8L * 1024L * 1024L,
) {
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .addInterceptor(DefaultHeadersInterceptor())
        .addNetworkInterceptor(AllowedOriginInterceptor(endpoints.allowedOrigins))
        .build()

    suspend fun get(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): JsonObject {
        val url = buildUrl(endpoints.music, path, query)
        return executeJson(Request.Builder().url(url).get().build())
    }

    suspend fun postForm(
        path: String,
        fields: Map<String, String>,
    ): JsonObject {
        val body = FormBody.Builder().apply {
            fields.forEach { (name, value) -> add(name, value) }
        }.build()
        val request = Request.Builder()
            .url(buildUrl(endpoints.music, path))
            .post(body)
            .build()
        return executeJson(request)
    }

    suspend fun eapiPost(
        path: String,
        payload: Map<String, Any?>,
        useInterface3: Boolean = false,
    ): JsonObject {
        require(path.startsWith("/eapi/")) { "EAPI path must start with /eapi/" }
        val payloadJson = gson.toJson(payload)
        val body = FormBody.Builder()
            .add("params", EapiCrypto.encryptParams(path, payloadJson))
            .build()
        val baseUrl = if (useInterface3) endpoints.interface3 else endpoints.music
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .post(body)
            .build()
        return executeJson(request)
    }

    internal fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun buildUrl(
        baseUrl: HttpUrl,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): HttpUrl {
        require(path.startsWith('/') && !path.startsWith("//")) { "Invalid API path" }
        val builder = baseUrl.newBuilder()
            .encodedPath(path)
        query.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    private suspend fun executeJson(request: Request): JsonObject {
        val response = client.newCall(request).await()
        response.use {
            if (!it.isSuccessful) {
                throw HttpStatusException(
                    statusCode = it.code,
                    retryAfterMillis = it.header("Retry-After")?.toLongOrNull()?.times(1000),
                )
            }
            val body = it.body ?: throw InvalidResponseException("Response body was empty")
            val contentLength = body.contentLength()
            if (contentLength > maxResponseBytes) throw ResponseTooLargeException(maxResponseBytes)
            val bytes = body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxResponseBytes) throw ResponseTooLargeException(maxResponseBytes)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8))
            if (!root.isJsonObject) throw InvalidResponseException("Response root was not an object")
            return root.asJsonObject
        }
    }

    private class DefaultHeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .header("Referer", "https://music.163.com/")
                .header("Accept", "application/json")
                .build()
            return chain.proceed(request)
        }
    }

    private class AllowedOriginInterceptor(
        private val allowedOrigins: Set<String>,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val origin = chain.request().url.originKey()
            if (origin !in allowedOrigins) throw IOException("Blocked API redirect to an untrusted origin")
            return chain.proceed(chain.request())
        }
    }

    private companion object {
        const val userAgent =
            "Mozilla/5.0 (Linux; Android 8.1+) AppleWebKit/537.36 SmartisanMusicRevived/0.2"
    }
}

private fun HttpUrl.originKey(): String = "$scheme://$host:$port"

internal class HttpStatusException(
    val statusCode: Int,
    val retryAfterMillis: Long?,
) : IOException("HTTP request failed with status $statusCode")

internal class ResponseTooLargeException(val limitBytes: Long) : IOException()

internal class InvalidResponseException(message: String) : IOException(message)

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, responseToClose, _ -> responseToClose.close() }
            }
        },
    )
}
