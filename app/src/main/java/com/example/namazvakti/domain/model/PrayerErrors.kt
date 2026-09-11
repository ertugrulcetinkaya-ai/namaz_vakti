package com.example.namazvakti.domain.model

import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

sealed interface PrayerError {
    data object Network : PrayerError
    data class Service(val statusCode: Int) : PrayerError
    data object InvalidData : PrayerError
    data object Storage : PrayerError
    data object Unknown : PrayerError
}

class PrayerTimesApiException(
    message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null
) : RuntimeException(message, cause)

fun classifyPrayerError(cause: Throwable): PrayerError = when (cause) {
    is PrayerTimesApiException -> cause.statusCode
        ?.let(PrayerError::Service)
        ?: PrayerError.InvalidData
    is IOException -> PrayerError.Network
    else -> PrayerError.Unknown
}

val PrayerError.isRetryable: Boolean
    get() = when (this) {
        PrayerError.Network -> true
        is PrayerError.Service -> statusCode == HTTP_TOO_MANY_REQUESTS ||
            statusCode in HTTP_SERVER_ERROR_RANGE
        PrayerError.InvalidData,
        PrayerError.Storage,
        PrayerError.Unknown -> false
    }

val PrayerError.code: String
    get() = when (this) {
        PrayerError.Network -> "network"
        is PrayerError.Service -> "service_$statusCode"
        PrayerError.InvalidData -> "invalid_data"
        PrayerError.Storage -> "storage"
        PrayerError.Unknown -> "unknown"
    }

fun interface PrayerEventLogger {
    fun warning(event: String, cause: Throwable)
}

object DefaultPrayerEventLogger : PrayerEventLogger {
    private val logger = Logger.getLogger("NamazPrayer")

    override fun warning(event: String, cause: Throwable) {
        logger.log(Level.WARNING, event, cause)
    }
}

private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERROR_RANGE = 500..599
