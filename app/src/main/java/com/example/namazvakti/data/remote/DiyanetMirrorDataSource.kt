package com.example.namazvakti.data.remote

import com.example.namazvakti.data.local.CachedDiyanetManifest
import com.example.namazvakti.data.local.DiyanetDataSourceName
import com.example.namazvakti.data.local.DiyanetSourceHealth
import com.example.namazvakti.data.local.DiyanetSourceStateStore
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimesApiException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class DiyanetMirrorDataSource(
    private val stateStore: DiyanetSourceStateStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val baseUrl: HttpUrl = MIRROR_BASE_URL.toHttpUrl(),
    private val timeProvider: PrayerTimeProvider = PrayerTimeProvider(),
    private val clock: Clock = Clock.systemUTC(),
    private val api: DiyanetPrayerTimesApi = DiyanetPrayerTimesApi(timeProvider = timeProvider)
) : DiyanetPrayerTimesRemoteDataSource {
    private val manifestMutex = Mutex()

    override suspend fun fetch(districtId: String): PrayerTimesCalendarResult {
        require(DISTRICT_ID_PATTERN.matches(districtId)) { "Invalid Diyanet district ID" }
        val manifest = loadManifest()
        val entry = manifest.files[districtId]
            ?: throw PrayerTimesApiException("Diyanet mirror has no data for district $districtId", statusCode = 404)
        val url = baseUrl.resolve(entry.path)
            ?: throw PrayerTimesApiException("Invalid Diyanet mirror path for district $districtId")
        val rawBody = execute(Request.Builder().url(url).get().header("User-Agent", USER_AGENT).build())
        val actualSha256 = sha256(rawBody)
        if (!actualSha256.equals(entry.sha256, ignoreCase = true)) {
            throw PrayerTimesApiException("Diyanet mirror SHA-256 mismatch for district $districtId")
        }

        val today = timeProvider.today()
        val result = api.parseResponse(String(rawBody, Charsets.UTF_8), today)
        val firstDate = result.days.firstOrNull()?.date
            ?: throw PrayerTimesApiException("Diyanet mirror response is empty")
        val lastDate = result.days.last().date
        if (firstDate.toString() != entry.firstDate || lastDate.toString() != entry.lastDate) {
            throw PrayerTimesApiException("Diyanet mirror date range does not match its manifest")
        }
        if (result.days.none { it.date == today }) {
            throw PrayerTimesApiException("Diyanet mirror has no data for $today")
        }
        return result
    }

    private suspend fun loadManifest(): MirrorManifest = manifestMutex.withLock {
        val now = clock.millis()
        val cached = runCatching { stateStore.readCachedManifest() }.getOrNull()
        if (cached != null && now >= cached.fetchedAtEpochMillis &&
            now - cached.fetchedAtEpochMillis < MANIFEST_CACHE_MILLIS
        ) {
            runCatching { parseManifest(cached.body) }.getOrNull()?.let { return it }
        }

        val url = baseUrl.resolve("manifest.json")
            ?: throw PrayerTimesApiException("Invalid Diyanet mirror manifest URL")
        val body = String(
            execute(Request.Builder().url(url).get().header("User-Agent", USER_AGENT).build()),
            Charsets.UTF_8
        )
        val manifest = parseManifest(body)
        try {
            stateStore.cacheManifest(CachedDiyanetManifest(body, now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warning("diyanet_manifest_cache_failure: ${e.message}")
        }
        manifest
    }

    private suspend fun execute(request: Request): ByteArray = suspendCancellableCoroutine { continuation ->
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
                        val body = it.body.bytes()
                        if (body.isEmpty()) throw PrayerTimesApiException("Diyanet mirror response is empty")
                        body
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }

    private fun parseManifest(body: String): MirrorManifest = try {
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("schema")?.asInt != 1 || root.get("source")?.asString != SOURCE) {
            throw IllegalArgumentException("Unsupported Diyanet mirror manifest")
        }
        val filesObject = root.getAsJsonObject("files")
            ?: throw IllegalArgumentException("Manifest files are missing")
        val files = filesObject.entrySet().associate { (districtId, jsonEntry) ->
            require(DISTRICT_ID_PATTERN.matches(districtId))
            val entry = jsonEntry.asJsonObject
            val path = entry.get("path")?.asString
                ?: throw IllegalArgumentException("Manifest path is missing for $districtId")
            val sha256 = entry.get("sha256")?.asString
                ?: throw IllegalArgumentException("Manifest SHA-256 is missing for $districtId")
            require(SHA256_PATTERN.matches(sha256))
            val firstDate = entry.get("firstDate")?.asString
                ?: throw IllegalArgumentException("Manifest firstDate is missing for $districtId")
            val lastDate = entry.get("lastDate")?.asString
                ?: throw IllegalArgumentException("Manifest lastDate is missing for $districtId")
            val parsedFirstDate = LocalDate.parse(firstDate)
            val parsedLastDate = LocalDate.parse(lastDate)
            require(!parsedLastDate.isBefore(parsedFirstDate))
            require(path == "${parsedFirstDate.year}/$districtId.json")
            districtId to MirrorFile(path, sha256, firstDate, lastDate)
        }
        MirrorManifest(files)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw PrayerTimesApiException("Invalid Diyanet mirror manifest", e)
    }

    private fun sha256(body: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(body)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class MirrorManifest(val files: Map<String, MirrorFile>)
    private data class MirrorFile(
        val path: String,
        val sha256: String,
        val firstDate: String,
        val lastDate: String
    )

    private companion object {
        const val SOURCE = "namazvakti.mtopal.dev"
        const val USER_AGENT = "NamazVakti Android"
        const val MANIFEST_CACHE_MILLIS = 24L * 60L * 60L * 1000L
        val MIRROR_BASE_URL = "https://raw.githubusercontent.com/ertugrulcetinkaya-ai/namaz_vakti/diyanet-data/v1/"
        val DISTRICT_ID_PATTERN = Regex("^[0-9]+$")
        val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
        val logger: Logger = Logger.getLogger("NamazPrayer")
    }
}

class FallbackDiyanetDataSource(
    private val mirror: DiyanetPrayerTimesRemoteDataSource,
    private val mtopal: DiyanetPrayerTimesRemoteDataSource,
    private val stateStore: DiyanetSourceStateStore,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : DiyanetPrayerTimesRemoteDataSource {
    override suspend fun fetch(districtId: String): PrayerTimesCalendarResult {
        try {
            val result = mirror.fetch(districtId)
            recordSource(DiyanetDataSourceName.MIRROR)
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (mirrorError: Exception) {
            logger.warning("diyanet_mirror_failure: ${mirrorError.message}")
        }

        val result = mtopal.fetch(districtId)
        recordSource(DiyanetDataSourceName.MTOPAL)
        return result
    }

    private suspend fun recordSource(source: DiyanetDataSourceName) {
        try {
            stateStore.recordSuccessfulSource(DiyanetSourceHealth(source, nowMillis()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warning("diyanet_source_health_write_failure: ${e.message}")
        }
    }

    private companion object {
        val logger: Logger = Logger.getLogger("NamazPrayer")
    }
}
