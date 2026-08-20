package com.smartisan.music.netease.internal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import okhttp3.Cookie
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal val neteaseSessionCorruptionHandler = ReplaceFileCorruptionHandler<Preferences> {
    emptyPreferences()
}

private val Context.neteaseSessionDataStore by preferencesDataStore(
    name = "netease_api_session",
    corruptionHandler = neteaseSessionCorruptionHandler,
)

internal interface CookieStore {
    suspend fun load(): List<Cookie>

    suspend fun save(cookies: List<Cookie>)

    suspend fun clear()
}

internal class EncryptedCookieStore(
    context: Context,
    private val gson: Gson,
    private val dataStore: DataStore<Preferences> = context.applicationContext.neteaseSessionDataStore,
) : CookieStore {

    override suspend fun load(): List<Cookie> {
        val encoded = dataStore.data.first()[encryptedCookiesKey] ?: return emptyList()
        return try {
            decodeCookies(decrypt(encoded))
        } catch (_: Exception) {
            clear()
            emptyList()
        }
    }

    override suspend fun save(cookies: List<Cookie>) {
        if (cookies.isEmpty()) {
            dataStore.edit { preferences -> preferences.remove(encryptedCookiesKey) }
            return
        }
        val encrypted = encrypt(encodeCookies(cookies))
        dataStore.edit { preferences -> preferences[encryptedCookiesKey] = encrypted }
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(encryptedCookiesKey) }
        deleteKey()
    }

    private fun encodeCookies(cookies: List<Cookie>): String = gson.toJson(
        cookies.map { cookie ->
            StoredCookie(
                name = cookie.name,
                value = cookie.value,
                expiresAt = cookie.expiresAt,
                domain = cookie.domain,
                path = cookie.path,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                persistent = cookie.persistent,
                hostOnly = cookie.hostOnly,
            )
        },
    )

    private fun decodeCookies(json: String): List<Cookie> {
        val type = object : TypeToken<List<StoredCookie>>() {}.type
        val records: List<StoredCookie> = gson.fromJson(json, type) ?: emptyList()
        return records.mapNotNull { record -> record.toCookieOrNull() }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(cipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return listOf(
            blobVersion,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        ).joinToString(":")
    }

    private fun decrypt(encoded: String): String {
        val parts = encoded.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == blobVersion) { "Unsupported session blob" }
        val cipher = Cipher.getInstance(cipherTransformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(gcmTagBits, Base64.decode(parts[1], Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun deleteKey() {
        runCatching {
            KeyStore.getInstance(androidKeyStore).apply {
                load(null)
                if (containsAlias(keyAlias)) deleteEntry(keyAlias)
            }
        }
    }

    private data class StoredCookie(
        @SerializedName("name") val name: String,
        @SerializedName("value") val value: String,
        @SerializedName("expiresAt") val expiresAt: Long,
        @SerializedName("domain") val domain: String,
        @SerializedName("path") val path: String,
        @SerializedName("secure") val secure: Boolean,
        @SerializedName("httpOnly") val httpOnly: Boolean,
        @SerializedName("persistent") val persistent: Boolean,
        @SerializedName("hostOnly") val hostOnly: Boolean,
    ) {
        fun toCookieOrNull(): Cookie? = runCatching {
            Cookie.Builder()
                .name(name)
                .value(value)
                .apply {
                    if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                    path(path)
                    if (persistent) expiresAt(expiresAt)
                    if (secure) secure()
                    if (httpOnly) httpOnly()
                }
                .build()
        }.getOrNull()
    }

    internal companion object {
        private val encryptedCookiesKey = stringPreferencesKey("encrypted_cookie_jar")
        private const val blobVersion = "v1"
        private const val androidKeyStore = "AndroidKeyStore"
        private const val keyAlias = "smartisan_music_netease_session_v1"
        private const val cipherTransformation = "AES/GCM/NoPadding"
        private const val gcmTagBits = 128
    }
}
