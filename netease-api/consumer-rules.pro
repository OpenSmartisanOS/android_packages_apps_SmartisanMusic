# Wire responses are mapped manually. Only the encrypted on-device Cookie record uses Gson fields.
-keepclassmembers,allowobfuscation class com.smartisan.music.netease.internal.EncryptedCookieStore$StoredCookie {
    @com.google.gson.annotations.SerializedName <fields>;
}
