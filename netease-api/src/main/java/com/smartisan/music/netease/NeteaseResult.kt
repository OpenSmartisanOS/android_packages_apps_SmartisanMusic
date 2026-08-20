package com.smartisan.music.netease

/** A network result that never exposes transport or JSON implementation types. */
sealed interface NeteaseResult<out T> {
    data class Success<T>(val value: T) : NeteaseResult<T>

    data class Failure(val error: NeteaseApiError) : NeteaseResult<Nothing>
}

sealed interface NeteaseApiError {
    val message: String

    data class InvalidRequest(override val message: String) : NeteaseApiError

    data class Network(
        override val message: String,
        val cause: Throwable? = null,
    ) : NeteaseApiError

    data class Http(
        val statusCode: Int,
        override val message: String,
        val retryAfterMillis: Long? = null,
    ) : NeteaseApiError

    data class Api(
        val code: Int,
        override val message: String,
    ) : NeteaseApiError

    data class AuthenticationRequired(
        val code: Int,
        override val message: String,
    ) : NeteaseApiError

    data class Parse(
        override val message: String,
        val cause: Throwable? = null,
    ) : NeteaseApiError

    data class Storage(
        override val message: String,
        val cause: Throwable? = null,
    ) : NeteaseApiError

    data class ResponseTooLarge(
        val limitBytes: Long,
        override val message: String = "Response exceeded the configured $limitBytes byte limit",
    ) : NeteaseApiError

    data class NoPlayableResource(
        val songId: Long,
        val attemptedQualities: List<NeteaseAudioQuality>,
        override val message: String = "No playable resource is available for song $songId",
    ) : NeteaseApiError
}
