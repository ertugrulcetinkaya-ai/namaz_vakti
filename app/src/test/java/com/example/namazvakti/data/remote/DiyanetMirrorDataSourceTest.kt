package com.example.namazvakti.data.remote

import com.example.namazvakti.data.local.CachedDiyanetManifest
import com.example.namazvakti.data.local.DataStoreDiyanetSourceStateStore
import com.example.namazvakti.data.local.DiyanetDataSourceName
import com.example.namazvakti.data.local.DiyanetSourceHealth
import com.example.namazvakti.data.local.DiyanetSourceStateStore
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimesApiException
import kotlinx.coroutines.test.runTest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DiyanetMirrorDataSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val instant = Instant.parse("2026-11-16T12:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)
    private val timeProvider = PrayerTimeProvider(clock)
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mirrorSuccessDoesNotCallMtopalAndRecordsMirrorHealth() = runTest {
        val raw = fixture("9206")
        server.enqueue(MockResponse().setBody(manifest("9206" to raw)))
        server.enqueue(MockResponse().setBody(raw.toString(Charsets.UTF_8)))
        val state = FakeStateStore()
        val mtopal = FakeDiyanetSource(result(), IOException("should not be called"))
        val source = fallback(state, mtopal)

        val response = source.fetch("9206")

        assertTrue(response.days.any { it.date == timeProvider.today() })
        assertEquals(0, mtopal.calls)
        assertEquals(DiyanetDataSourceName.MIRROR, state.health?.source)
        assertEquals(instant.toEpochMilli(), state.health?.succeededAtEpochMillis)
        assertEquals("/v1/manifest.json", server.takeRequest().path)
        assertEquals("/v1/2026/9206.json", server.takeRequest().path)
    }

    @Test
    fun manifest404FallsBackToMtopal() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val state = FakeStateStore()
        val mtopal = FakeDiyanetSource(result())

        val response = fallback(state, mtopal).fetch("9206")

        assertEquals(result(), response)
        assertEquals(1, mtopal.calls)
        assertEquals(DiyanetDataSourceName.MTOPAL, state.health?.source)
    }

    @Test
    fun sha256MismatchFallsBackToMtopal() = runTest {
        val raw = fixture("9206")
        server.enqueue(MockResponse().setBody(manifest("9206" to raw, sha256Override = "0".repeat(64))))
        server.enqueue(MockResponse().setBody(raw.toString(Charsets.UTF_8)))
        val state = FakeStateStore()
        val mtopal = FakeDiyanetSource(result())

        fallback(state, mtopal).fetch("9206")

        assertEquals(1, mtopal.calls)
        assertEquals(DiyanetDataSourceName.MTOPAL, state.health?.source)
    }

    @Test
    fun bothDiyanetSourcesFailAndTheMtopalExceptionIsPropagated() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val failure = IOException("mtopal is offline")
        val mtopal = FakeDiyanetSource(result(), failure)

        val thrown = runCatching { fallback(FakeStateStore(), mtopal).fetch("9206") }.exceptionOrNull()

        assertSame(failure, thrown)
        assertEquals(1, mtopal.calls)
    }

    @Test
    fun manifestIsReusedFromStateStoreForTwentyFourHours() = runTest {
        val first = fixture("9206")
        val second = fixture("9541")
        server.enqueue(MockResponse().setBody(manifest("9206" to first, "9541" to second)))
        server.enqueue(MockResponse().setBody(first.toString(Charsets.UTF_8)))
        server.enqueue(MockResponse().setBody(second.toString(Charsets.UTF_8)))
        val state = FakeStateStore()
        val mirror = mirror(state)

        mirror.fetch("9206")
        mirror.fetch("9541")

        assertEquals(3, server.requestCount)
        assertEquals("/v1/manifest.json", server.takeRequest().path)
        assertEquals("/v1/2026/9206.json", server.takeRequest().path)
        assertEquals("/v1/2026/9541.json", server.takeRequest().path)
        assertEquals(instant.toEpochMilli(), state.cachedManifest?.fetchedAtEpochMillis)
    }

    @Test
    fun cachedManifestExpiresAfterTwentyFourHours() = runTest {
        val raw = fixture("9206")
        stateStoreWithExpiredManifest(raw).also { state ->
            server.enqueue(MockResponse().setBody(manifest("9206" to raw)))
            server.enqueue(MockResponse().setBody(raw.toString(Charsets.UTF_8)))

            mirror(state).fetch("9206")

            assertEquals(2, server.requestCount)
            assertEquals(instant.toEpochMilli(), state.cachedManifest?.fetchedAtEpochMillis)
        }
    }

    @Test
    fun dataStorePersistsManifestCacheAndSuccessfulSourceHealth() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "diyanet.preferences_pb") }
        )
        val stateStore = DataStoreDiyanetSourceStateStore(dataStore)
        val cachedManifest = CachedDiyanetManifest("{\"schema\":1}", instant.toEpochMilli())
        val health = DiyanetSourceHealth(DiyanetDataSourceName.MIRROR, instant.toEpochMilli())

        stateStore.cacheManifest(cachedManifest)
        stateStore.recordSuccessfulSource(health)

        assertEquals(cachedManifest, stateStore.readCachedManifest())
        assertEquals(health, stateStore.readSourceHealth())
    }

    private fun fallback(state: FakeStateStore, mtopal: FakeDiyanetSource) = FallbackDiyanetDataSource(
        mirror(state),
        mtopal,
        state,
        nowMillis = { instant.toEpochMilli() }
    )

    private fun mirror(state: FakeStateStore) = DiyanetMirrorDataSource(
        stateStore = state,
        client = okhttp3.OkHttpClient(),
        baseUrl = server.url("/v1/"),
        timeProvider = timeProvider,
        clock = clock
    )

    private fun result(): PrayerTimesCalendarResult = DiyanetPrayerTimesApi(timeProvider = timeProvider)
        .parseResponse(fixture("9206").toString(Charsets.UTF_8), timeProvider.today())

    private fun fixture(id: String): ByteArray = javaClass.getResource("/diyanet/$id.json")!!.readBytes()

    private fun manifest(
        vararg calendars: Pair<String, ByteArray>,
        sha256Override: String? = null
    ): String {
        val files = calendars.associate { (id, raw) ->
            val digest = sha256Override ?: MessageDigest.getInstance("SHA-256").digest(raw)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            id to mapOf(
                "path" to "2026/$id.json",
                "sha256" to digest,
                "firstDate" to "2026-11-16",
                "lastDate" to "2026-12-31"
            )
        }
        return com.google.gson.Gson().toJson(
            mapOf(
                "schema" to 1,
                "generatedAt" to "2026-11-16T00:00:00Z",
                "source" to "namazvakti.mtopal.dev",
                "files" to files
            )
        )
    }

    private fun stateStoreWithExpiredManifest(raw: ByteArray): FakeStateStore {
        val state = FakeStateStore()
        state.cachedManifest = CachedDiyanetManifest(
            manifest("9206" to raw),
            instant.minusSeconds(24 * 60 * 60).toEpochMilli()
        )
        return state
    }

    private class FakeDiyanetSource(
        private val response: PrayerTimesCalendarResult,
        private val failure: Exception? = null
    ) : DiyanetPrayerTimesRemoteDataSource {
        var calls = 0
            private set

        override suspend fun fetch(districtId: String): PrayerTimesCalendarResult {
            calls++
            failure?.let { throw it }
            return response
        }
    }

    private class FakeStateStore : DiyanetSourceStateStore {
        var cachedManifest: CachedDiyanetManifest? = null
        var health: DiyanetSourceHealth? = null

        override suspend fun readCachedManifest(): CachedDiyanetManifest? = cachedManifest

        override suspend fun cacheManifest(manifest: CachedDiyanetManifest) {
            cachedManifest = manifest
        }

        override suspend fun recordSuccessfulSource(health: DiyanetSourceHealth) {
            this.health = health
        }
    }
}
