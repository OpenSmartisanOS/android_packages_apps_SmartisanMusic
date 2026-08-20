package com.smartisan.music.netease

import android.content.Context
import com.google.gson.Gson
import com.smartisan.music.netease.internal.AllowedCookieJar
import com.smartisan.music.netease.internal.EncryptedCookieStore
import com.smartisan.music.netease.internal.NeteaseHttpTransport
import com.smartisan.music.netease.internal.NeteaseMusicApiImpl
import java.io.Closeable

data class NeteaseApiConfig(
    val connectTimeoutMillis: Long = 15_000,
    val readTimeoutMillis: Long = 30_000,
    val maxResponseBytes: Long = 8L * 1024L * 1024L,
)

/**
 * An explicitly owned NetEase API client. Creating it reads only local encrypted session state;
 * no network request is made until a method on [api] is called.
 */
class NeteaseApiClient private constructor(
    val api: NeteaseMusicApi,
    val sessionManager: NeteaseSessionManager,
    private val transport: NeteaseHttpTransport,
) : Closeable {
    override fun close() {
        transport.close()
    }

    companion object {
        suspend fun create(
            context: Context,
            config: NeteaseApiConfig = NeteaseApiConfig(),
        ): NeteaseApiClient {
            require(config.connectTimeoutMillis in 1_000..120_000) {
                "Connect timeout must be between 1 and 120 seconds"
            }
            require(config.readTimeoutMillis in 1_000..120_000) {
                "Read timeout must be between 1 and 120 seconds"
            }
            require(config.maxResponseBytes in 64L * 1024L..16L * 1024L * 1024L) {
                "Response limit must be between 64 KiB and 16 MiB"
            }
            val gson = Gson()
            val store = EncryptedCookieStore(context, gson)
            val cookieJar = AllowedCookieJar(store.load(), store)
            val transport = NeteaseHttpTransport(
                gson = gson,
                cookieJar = cookieJar,
                connectTimeoutMillis = config.connectTimeoutMillis,
                readTimeoutMillis = config.readTimeoutMillis,
                maxResponseBytes = config.maxResponseBytes,
            )
            return NeteaseApiClient(
                api = NeteaseMusicApiImpl(transport),
                sessionManager = cookieJar,
                transport = transport,
            )
        }
    }
}
