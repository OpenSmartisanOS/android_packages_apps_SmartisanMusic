package com.smartisan.music.netease.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class EapiCryptoTest {
    @Test
    fun encryptParamsMatchesKnownNcrustVector() {
        val encrypted = EapiCrypto.encryptParams(
            path = "/eapi/song/enhance/player/url/v1",
            payloadJson = "{\"ids\":\"[347230]\",\"level\":\"standard\"}",
        )

        assertEquals(
            "fa90b329e9614f79e79598f37dc2edb487f00d1bc4c9b24cd57e6c318b907356" +
                "9338432cd7d98d1a3626e997a2c53121c461ee0e88d3d1bf3f42e78643807a29" +
                "b83d00d24ceca2c01f229a64e4d80cbb5eef4a69dcb79e93c1d2301d38dac26" +
                "511d81bb3f926495784500b9a0c9f7dd47e1396f5d6b610c295193b8a1fcba1ad",
            encrypted,
        )
    }
}
