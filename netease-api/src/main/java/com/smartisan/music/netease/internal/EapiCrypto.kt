package com.smartisan.music.netease.internal

import android.annotation.SuppressLint
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * NetEase EAPI signing/encryption, adapted from Ncrust.
 * Copyright (c) 2026 Takahashi_Rinta, licensed under the MIT License.
 */
internal object EapiCrypto {
    private val aesKey = "e82ckenh8dichen8".toByteArray(Charsets.UTF_8)
    private const val magicPrefix = "nobody"
    private const val magicSuffix = "md5forencrypt"
    private const val separator = "-36cd479b6b5-"

    fun encryptParams(path: String, payloadJson: String): String {
        require(path.startsWith("/eapi/")) { "EAPI path must start with /eapi/" }
        val apiPath = path.replaceFirst("/eapi/", "/api/")
        val digest = md5("${magicPrefix}${apiPath}use${payloadJson}${magicSuffix}")
        return aesEncrypt("${apiPath}${separator}${payloadJson}${separator}${digest}")
    }

    @SuppressLint("GetInstance") // NetEase's fixed EAPI wire protocol mandates AES-128-ECB.
    private fun aesEncrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"))
        return cipher.doFinal(data.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
