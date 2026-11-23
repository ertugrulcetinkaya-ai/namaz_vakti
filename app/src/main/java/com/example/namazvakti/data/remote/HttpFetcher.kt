package com.example.namazvakti.data.remote

import com.example.namazvakti.domain.model.PrayerTimesApiException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

internal fun createPrayerHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

internal class HttpFetcher(private val client: OkHttpClient) {
    suspend fun fetchBytes(request: Request): ByteArray = fetch(request) { response ->
        response.body.bytes()
    }

    suspend fun fetchText(request: Request, emptyBodyMessage: String): String = fetch(request) { response ->
        response.body.string().also { body ->
            if (body.isBlank()) throw PrayerTimesApiException(emptyBodyMessage)
        }
    }

    private suspend fun <T> fetch(request: Request, readBody: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) {
                                throw PrayerTimesApiException("HTTP ${it.code}", statusCode = it.code)
                            }
                            readBody(it)
                        }
                    }
                    continuation.resumeWith(result)
                }
            })
        }
}
