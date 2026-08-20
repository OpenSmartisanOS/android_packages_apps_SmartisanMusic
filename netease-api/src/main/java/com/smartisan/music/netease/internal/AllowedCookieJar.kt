package com.smartisan.music.netease.internal

import com.smartisan.music.netease.NeteaseApiError
import com.smartisan.music.netease.NeteaseResult
import com.smartisan.music.netease.NeteaseSessionManager
import com.smartisan.music.netease.NeteaseSessionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.Locale

internal class AllowedCookieJar(
    initialCookies: List<Cookie>,
    private val store: CookieStore?,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : CookieJar, NeteaseSessionManager {
    private val lock = Any()
    private var cookies: List<Cookie> = prune(initialCookies)
    private var lastPersistenceError: NeteaseApiError.Storage? = null
    private val mutableSnapshot = MutableStateFlow(snapshotOf(cookies))

    override val snapshot: StateFlow<NeteaseSessionSnapshot> = mutableSnapshot.asStateFlow()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!isAllowedHost(url.host)) return emptyList()
        return synchronized(lock) {
            val active = prune(cookies)
            if (active.size != cookies.size) {
                val persistenceError = persist(active)
                lastPersistenceError = persistenceError
                if (persistenceError == null) cookies = active
                mutableSnapshot.value = snapshotOf(active, persistenceError)
            }
            active.filter { cookie -> cookie.matches(url) }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!isAllowedHost(url.host)) return
        val accepted = cookies.filter { cookie ->
            cookie.domain == parentCookieDomain || cookie.domain in allowedHosts
        }
        if (accepted.isEmpty()) return
        synchronized(lock) {
            val merged = merge(this.cookies, accepted)
            val persistenceError = persist(merged)
            lastPersistenceError = persistenceError
            if (persistenceError == null) this.cookies = merged
            mutableSnapshot.value = snapshotOf(
                source = if (persistenceError == null) merged else this.cookies,
                persistenceError = persistenceError,
            )
        }
    }

    override suspend fun importCookieHeader(
        cookieHeader: String,
    ): NeteaseResult<NeteaseSessionSnapshot> {
        val parsed = try {
            parseImportedCookies(cookieHeader)
        } catch (exception: IllegalArgumentException) {
            return NeteaseResult.Failure(
                NeteaseApiError.InvalidRequest(exception.message ?: "Invalid Cookie header"),
            )
        }
        return try {
            store?.save(parsed)
            synchronized(lock) {
                cookies = parsed
                lastPersistenceError = null
                snapshotOf(parsed).also { mutableSnapshot.value = it }
            }.let { snapshot -> NeteaseResult.Success(snapshot) }
        } catch (exception: Exception) {
            val error = NeteaseApiError.Storage("Unable to save the encrypted NetEase session", exception)
            synchronized(lock) {
                lastPersistenceError = error
                mutableSnapshot.value = snapshotOf(cookies, error)
            }
            NeteaseResult.Failure(error)
        }
    }

    override suspend fun clear(): NeteaseResult<Unit> {
        return try {
            store?.clear()
            synchronized(lock) {
                cookies = emptyList()
                lastPersistenceError = null
                mutableSnapshot.value = snapshotOf(emptyList())
            }
            NeteaseResult.Success(Unit)
        } catch (exception: Exception) {
            val error = NeteaseApiError.Storage("Unable to clear the encrypted NetEase session", exception)
            synchronized(lock) {
                lastPersistenceError = error
                mutableSnapshot.value = snapshotOf(cookies, error)
            }
            NeteaseResult.Failure(error)
        }
    }

    internal fun allCookiesForTest(): List<Cookie> = synchronized(lock) { cookies.toList() }

    private fun parseImportedCookies(header: String): List<Cookie> {
        require(header.isNotBlank()) { "Cookie header must not be blank" }
        require(header.length <= maxImportedCookieHeaderLength) { "Cookie header is too large" }
        val expiresAt = clockMillis() + importedCookieLifetimeMillis
        val ignoredAttributes = setOf(
            "domain", "path", "expires", "max-age", "samesite", "secure", "httponly", "partitioned",
        )
        val parsed = linkedMapOf<String, Cookie>()
        header.split(';').forEach { segment ->
            val pair = segment.trim()
            if (pair.isEmpty()) return@forEach
            val separator = pair.indexOf('=')
            if (separator <= 0) return@forEach
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            if (name.lowercase(Locale.US) in ignoredAttributes) return@forEach
            val cookie = runCatching {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(parentCookieDomain)
                    .path("/")
                    .expiresAt(expiresAt)
                    .secure()
                    .httpOnly()
                    .build()
            }.getOrElse { throw IllegalArgumentException("Invalid Cookie pair: $name") }
            parsed[name] = cookie
        }
        require(parsed.isNotEmpty()) { "Cookie header did not contain any valid cookies" }
        return parsed.values.toList()
    }

    private fun merge(existing: List<Cookie>, updates: List<Cookie>): List<Cookie> {
        val result = prune(existing).associateByTo(linkedMapOf()) { cookie -> cookie.storageKey() }
        updates.forEach { cookie ->
            if (cookie.expiresAt <= clockMillis()) {
                result.remove(cookie.storageKey())
            } else {
                result[cookie.storageKey()] = cookie
            }
        }
        return result.values.toList()
    }

    private fun prune(source: List<Cookie>): List<Cookie> {
        val now = clockMillis()
        return source.filter { cookie -> !cookie.persistent || cookie.expiresAt > now }
    }

    private fun snapshotOf(
        source: List<Cookie>,
        persistenceError: NeteaseApiError.Storage? = lastPersistenceError,
    ): NeteaseSessionSnapshot {
        val active = prune(source)
        return NeteaseSessionSnapshot(
            authenticated = active.any { it.name == "MUSIC_U" || it.name == "MUSIC_A" },
            cookieCount = active.size,
            earliestExpiryEpochMillis = active
                .filter { cookie -> cookie.persistent }
                .minOfOrNull { cookie -> cookie.expiresAt },
            lastPersistenceError = persistenceError,
        )
    }

    private fun persist(value: List<Cookie>): NeteaseApiError.Storage? {
        val targetStore = store ?: return null
        return try {
            runBlocking { targetStore.save(value) }
            null
        } catch (exception: Exception) {
            NeteaseApiError.Storage("Unable to persist the updated NetEase session", exception)
        }
    }

    private fun Cookie.storageKey(): String = "$name\u0000$domain\u0000$path"

    internal companion object {
        val allowedHosts = setOf(
            "music.163.com",
            "interface.music.163.com",
            "interface3.music.163.com",
        )
        private const val parentCookieDomain = "music.163.com"
        private const val maxImportedCookieHeaderLength = 16 * 1024
        private const val importedCookieLifetimeMillis = 365L * 24L * 60L * 60L * 1000L

        fun isAllowedHost(host: String): Boolean = host.lowercase(Locale.US) in allowedHosts
    }
}
