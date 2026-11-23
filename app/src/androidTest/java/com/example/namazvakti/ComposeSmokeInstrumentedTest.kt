package com.example.namazvakti

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.namazvakti.app.NamazVaktiApp
import com.example.namazvakti.data.local.PrayerNotificationSettingsSource
import com.example.namazvakti.data.local.PrayerPreferences
import com.example.namazvakti.domain.model.CachedPrayerDay
import com.example.namazvakti.domain.model.MonthDataSource
import com.example.namazvakti.domain.model.MonthResult
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerDataSource
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.PrayerTimeProvider
import com.example.namazvakti.domain.model.PrayerTimes
import com.example.namazvakti.domain.model.RamadanRange
import com.example.namazvakti.domain.model.RefreshResult
import com.example.namazvakti.domain.port.PrayerRefreshRepository
import com.example.namazvakti.domain.port.PrayerRefreshScheduler
import com.example.namazvakti.domain.port.PrayerWidgetUpdatePort
import com.example.namazvakti.domain.policy.PrayerCachePolicy
import com.example.namazvakti.ui.main.PrayerViewModel
import com.example.namazvakti.ui.main.PrayerUiState
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeSmokeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val dismissSystemDialogsRule = DismissSystemDialogsRule()

    @Test
    fun initialScreenMovesFromLoadingToReadyWhenLocalStoreResponds() {
        val gate = CompletableDeferred<Unit>()
        val harness = launchHarness(storeReadGate = gate)
        try {
            harness.scenario.use {
                assertEquals(PrayerUiState.Loading, harness.viewModel.state.value)
                gate.complete(Unit)
                awaitCondition("ready ViewModel state") { harness.viewModel.state.value is PrayerUiState.Ready }
                awaitNode("ready city") { it.text?.toString() == "ANKARA" }
            }
        } finally {
            harness.scope.cancel()
        }
    }

    @Test
    fun aboutScreenShowsVersionAndBackReturnsHome() {
        val harness = launchHarness()
        try {
            harness.scenario.use {
                val about = context.getString(R.string.about_open)
                awaitNode("about entry") {
                    it.text?.toString() == about && it.hasClickableAncestor()
                }.click()

                awaitNode("application version and package") {
                    it.text?.toString()?.let { text ->
                        text.contains(com.example.namazvakti.BuildConfig.VERSION_NAME) &&
                            text.contains(com.example.namazvakti.BuildConfig.APPLICATION_ID)
                    } == true
                }

                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

                awaitNode("home app title") {
                    it.text?.toString() == context.getString(R.string.app_name)
                }
            }
        } finally {
            harness.scope.cancel()
        }
    }

    @Test
    fun provinceAndDistrictSelectionHasTwoStepsAndBackNavigation() {
        val harness = launchHarness()
        try {
            harness.scenario.use {
                awaitCondition("initial ready state") { harness.viewModel.state.value is PrayerUiState.Ready }
                awaitNode("home city") { it.text?.toString() == "ANKARA" }
                    .click()
                awaitNode(context.getString(R.string.city_selection_title)) {
                    it.text?.toString() == context.getString(R.string.city_selection_title)
                }

                searchFor("istanbul")
                awaitNode("İstanbul province") {
                    it.text?.toString() == "İSTANBUL" && it.hasClickableAncestor()
                }.click()
                awaitNode("Istanbul district step") {
                    it.text?.toString() == "İSTANBUL" && !it.hasClickableAncestor()
                }

                awaitNode(context.getString(R.string.back)) {
                    it.contentDescription?.toString() == context.getString(R.string.back)
                }.click()
                awaitNode("province step after Back") {
                    it.text?.toString() == context.getString(R.string.city_selection_title)
                }

                searchFor("istanbul")
                awaitNode("İstanbul province after Back") {
                    it.text?.toString() == "İSTANBUL" && it.hasClickableAncestor()
                }.click()
                searchFor("arnavutkoy")
                val districtOption = awaitNode("Arnavutkoy district") {
                    it.text?.toString() == "ARNAVUTKOY" && !it.isEditable && it.hasClickableAncestor()
                }
                districtOption.click()

                awaitCondition(
                    "Arnavutkoy district saved"
                ) {
                    harness.store.location.diyanetDistrictId != null
                }
                awaitNode("selected district on home") {
                    it.text?.toString() == harness.store.location.displayName
                }
            }
        } finally {
            harness.scope.cancel()
        }
    }

    @Test
    fun systemBackFromImsakiyeReturnsToMonthlyScreen() {
        val harness = launchHarness()
        try {
            harness.scenario.use {
                val monthly = context.getString(R.string.monthly_open)
                val imsakiye = context.getString(R.string.imsakiye_open)
                awaitNode("monthly action") { it.text?.toString() == monthly }.click()
                awaitNode(imsakiye) { it.contentDescription?.toString() == imsakiye }.click()
                awaitImsakiyeTitle()

                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

                awaitNode(context.getString(R.string.next_month)) {
                    it.contentDescription?.toString() == context.getString(R.string.next_month)
                }
            }
        } finally {
            harness.scope.cancel()
        }
    }

    @Test
    fun estimatedRamadanOutsidePublishedTablesShowsEstimatedLabelAndEidDate() {
        val range = estimated2036RamadanRange()
        val harness = launchHarness(ramadanRange = range)
        try {
            harness.scenario.use {
                awaitCondition("initial ready state") { harness.viewModel.state.value is PrayerUiState.Ready }
                awaitCondition("estimated Ramadan range") { harness.viewModel.ramadanRange.value == range }
                val monthly = context.getString(R.string.monthly_open)
                awaitNode(monthly) { it.text?.toString() == monthly }.click()
                val imsakiye = context.getString(R.string.imsakiye_open)
                awaitNode(imsakiye) { it.contentDescription?.toString() == imsakiye }.click()

                awaitImsakiyeTitle()
                awaitNode(context.getString(R.string.ramadan_estimated_label)) {
                    it.text?.toString() == context.getString(R.string.ramadan_estimated_label)
                }
                val expectedEidRow = context.getString(
                    R.string.eid_date,
                    range.eidDate.format(ACCESSIBLE_DATE_FORMATTER)
                )
                awaitNodeAfterScrolling("estimated Eid date from RamadanRange.eidDate") {
                    it.text?.toString() == expectedEidRow
                }
            }
        } finally {
            harness.scope.cancel()
        }
    }

    @Test
    fun estimatedRamadanYearIsLabeledOnReligiousDaysScreen() {
        val range = estimated2036RamadanRange()
        val harness = launchHarness(ramadanRange = range)
        try {
            harness.scenario.use {
                awaitCondition("initial ready state") { harness.viewModel.state.value is PrayerUiState.Ready }
                awaitCondition("estimated Ramadan range") { harness.viewModel.ramadanRange.value == range }
                val monthly = context.getString(R.string.monthly_open)
                awaitNode(monthly) { it.text?.toString() == monthly }.click()
                val religiousDays = context.getString(R.string.religious_days_open)
                awaitNode(religiousDays) {
                    it.text?.toString() == religiousDays && it.hasClickableAncestor()
                }.click()

                var year = PrayerTimeProvider().today().year.coerceAtLeast(2025)
                while (year != range.startDate.year) {
                    val movingForward = year < range.startDate.year
                    val buttonLabel = context.getString(
                        if (movingForward) R.string.next_year else R.string.previous_year
                    )
                    awaitNode(buttonLabel) { it.contentDescription?.toString() == buttonLabel }.click()
                    year += if (movingForward) 1 else -1
                    awaitNode("religious year $year") { it.text?.toString() == year.toString() }
                }

                awaitNode(context.getString(R.string.ramadan_estimated_label)) {
                    it.text?.toString() == context.getString(R.string.ramadan_estimated_label)
                }
            }
        } finally {
            harness.scope.cancel()
        }
    }

    @Test
    fun staleOfflineScreenShowsRetryAndRetryCallsRepository() {
        val location = PrayerLocation("Ankara", "Turkey", "ANKARA")
        val yesterday = PrayerTimeProvider().today(location.timezone).minusDays(1)
        val harness = launchHarness(
            location = location,
            initialCacheDate = yesterday,
            failRefresh = true
        )
        try {
            harness.scenario.use {
                awaitCondition(
                    "automatic offline refresh",
                    diagnostics = {
                        "state=${harness.viewModel.state.value}, repository calls=${harness.repository.refreshCalls}"
                    }
                ) {
                    harness.repository.refreshCalls >= 1
                }
                awaitNode(context.getString(R.string.retry)) {
                    it.text?.toString() == context.getString(R.string.retry)
                }
                val callsBeforeRetry = harness.repository.refreshCalls
                awaitNode(context.getString(R.string.retry)) {
                    it.text?.toString() == context.getString(R.string.retry)
                }.click()
                awaitCondition("retry repository call") {
                    harness.repository.refreshCalls > callsBeforeRetry
                }
            }
        } finally {
            harness.scope.cancel()
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun notificationSettingIsSavedAndRestoredWhenSheetReopens() {
        val initialSettings = PrayerNotificationSettings(notificationsEnabled = true)
        val harness = launchHarness(notificationSettings = initialSettings)
        try {
            harness.scenario.use {
                awaitCondition("initial notification setting") {
                    harness.viewModel.notificationSettings.value.notificationsEnabled
                }
                val open = context.getString(R.string.notification_settings_open)
                awaitNode(open) { it.contentDescription?.toString() == open }.click()
                awaitNode(context.getString(R.string.notification_settings_title)) {
                    it.text?.toString() == context.getString(R.string.notification_settings_title)
                }
                val notificationsSwitch = awaitCheckableForLabel(
                    context.getString(R.string.notifications_enabled)
                )
                assertTrue(notificationsSwitch.isChecked)
                notificationsSwitch.click()
                awaitNodeAfterScrolling(context.getString(R.string.notification_save)) {
                    it.text?.toString() == context.getString(R.string.notification_save)
                }.click()
                awaitCondition("notification preference write") {
                    !harness.notificationSettings.current.notificationsEnabled
                }

                awaitNode(open) { it.contentDescription?.toString() == open }.click()
                assertEquals(
                    false,
                    awaitCheckableForLabel(context.getString(R.string.notifications_enabled)).isChecked
                )
            }
        } finally {
            harness.scope.cancel()
        }
    }

    private fun launchHarness(
        location: PrayerLocation = PrayerLocation("Ankara", "Turkey", "ANKARA"),
        initialCacheDate: LocalDate = PrayerTimeProvider().today(location.timezone),
        storeReadGate: CompletableDeferred<Unit>? = null,
        failRefresh: Boolean = false,
        notificationSettings: PrayerNotificationSettings = PrayerNotificationSettings(),
        ramadanRange: RamadanRange = sampleRamadanRange()
    ): Harness {
        val store = FakeStore(location, cache(location, initialCacheDate), storeReadGate)
        val repository = FakeRepository(store, failRefresh, ramadanRange)
        val notificationStore = FakeNotificationSettings(notificationSettings)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val viewModel = PrayerViewModel(
            application = ApplicationProvider.getApplicationContext<NamazVaktiApp>(),
            store = store,
            repository = repository,
            scheduler = FakeScheduler(),
            widgetUpdater = FakeWidgetUpdater(),
            timeProvider = PrayerTimeProvider(),
            settings = PrayerCalculationSettings(),
            testScope = scope,
            ioDispatcher = Dispatchers.IO,
            cachePolicy = PrayerCachePolicy(),
            notificationSettingsStore = notificationStore
        )
        val scenario = ActivityScenario.launch(ComposeSmokeTestActivity::class.java)
        scenario.onActivity { activity ->
            activity.show(viewModel)
        }
        return Harness(scenario, scope, store, repository, notificationStore, viewModel)
    }

    private fun searchFor(query: String) {
        val searchField = awaitNode("search field") { it.isEditable }
        assertTrue(
            "Could not enter search text '$query'",
            searchField.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                }
            )
        )
    }

    private fun awaitImsakiyeTitle() = awaitNode("Ramadan schedule title") {
        it.text?.toString()?.endsWith("İmsakiyesi") == true
    }

    private fun awaitCondition(
        description: String,
        diagnostics: () -> String = { "" },
        condition: () -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        do {
            if (condition()) return
            SystemClock.sleep(POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Timed out waiting for $description ${diagnostics()}")
    }

    private fun awaitNode(
        description: String,
        matches: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        do {
            dismissSystemDialogsRule.dismissSystemDialogs()
            instrumentation.uiAutomation.rootInActiveWindow?.findNode(matches)?.let { return it }
            SystemClock.sleep(POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        val visibleNodes = instrumentation.uiAutomation.rootInActiveWindow?.visibleNodeSummary().orEmpty()
        throw AssertionError("Compose screen did not show $description. Visible nodes: $visibleNodes")
    }

    private fun awaitNodeAfterScrolling(
        description: String,
        matches: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        do {
            dismissSystemDialogsRule.dismissSystemDialogs()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.findNode(matches)?.let { return it }
            // Keep the original behavior first: scroll the first scrollable node forward (the notification
            // sheet relies on this). Only when it cannot move any more, scroll another node down; the imsakiye
            // screen has a horizontal table scroller before its vertical day list.
            val scrolled = root?.findNode { it.isScrollable }
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true ||
                root?.collectScrollables().orEmpty().any {
                    it.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)
                }
            if (!scrolled) break
            SystemClock.sleep(POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        return awaitNode(description, matches)
    }

    private fun AccessibilityNodeInfo.collectScrollables(): List<AccessibilityNodeInfo> = buildList {
        if (isVisibleToUser && isScrollable) add(this@collectScrollables)
        for (index in 0 until childCount) {
            getChild(index)?.collectScrollables()?.let(::addAll)
        }
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.visibleNodeSummary(): String {
        val node = this
        return buildString {
            if (node.isVisibleToUser) {
                append("[")
                append(node.text ?: node.contentDescription ?: node.className)
                append(if (node.isCheckable) ", checked=${node.isChecked}" else "")
                append("] ")
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { append(it.visibleNodeSummary()) }
            }
        }
    }

    private fun awaitCheckableForLabel(label: String): AccessibilityNodeInfo {
        val labelNode = awaitNode(label) { it.text?.toString() == label }
        var parent = labelNode.parent
        while (parent != null) {
            val switchNode = parent.findDescendant { it.isCheckable && it.isEnabled }
            if (switchNode != null) return switchNode
            parent = parent.parent
        }
        throw AssertionError("No enabled switch found beside '$label'")
    }

    private fun AccessibilityNodeInfo.findDescendant(
        matches: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        for (index in 0 until childCount) {
            val child = getChild(index) ?: continue
            if (child.isVisibleToUser && matches(child)) return child
            child.findDescendant(matches)?.let { return it }
        }
        return null
    }

    private fun AccessibilityNodeInfo.findNode(
        matches: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (isVisibleToUser && matches(this)) return this
        for (index in 0 until childCount) {
            getChild(index)?.findNode(matches)?.let { return it }
        }
        return null
    }

    private fun AccessibilityNodeInfo.hasClickableAncestor(): Boolean =
        generateSequence(this) { it.parent }.any { it.isClickable && it.isEnabled }

    private fun AccessibilityNodeInfo.click() {
        val actionNode = generateSequence(this) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }
        assertTrue(
            "Could not click ${text ?: contentDescription}",
            actionNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        )
    }

    private class Harness(
        val scenario: ActivityScenario<ComposeSmokeTestActivity>,
        val scope: CoroutineScope,
        val store: FakeStore,
        val repository: FakeRepository,
        val notificationSettings: FakeNotificationSettings,
        val viewModel: PrayerViewModel
    )

    private class FakeStore(
        var location: PrayerLocation,
        initialCache: CachedPrayerDay,
        private val locationReadGate: CompletableDeferred<Unit>?
    ) : PrayerPreferences {
        private val caches = mutableMapOf(initialCache.date to initialCache)

        override suspend fun readLocation(): PrayerLocation {
            locationReadGate?.await()
            return location
        }

        override suspend fun readCache(): CachedPrayerDay? = caches.values
            .filter { it.location.cacheKey == location.cacheKey }
            .maxByOrNull(CachedPrayerDay::date)

        override suspend fun readCache(
            date: LocalDate,
            location: PrayerLocation,
            settings: PrayerCalculationSettings
        ): CachedPrayerDay? = caches[date]?.takeIf { it.matches(date, location, settings) }

        override suspend fun saveCache(cache: CachedPrayerDay) {
            caches[cache.date] = cache
        }

        override suspend fun saveLocation(location: PrayerLocation) {
            this.location = location
        }

        override suspend fun clearCache() {
            caches.clear()
        }
    }

    private class FakeRepository(
        private val store: FakeStore,
        private val failRefresh: Boolean,
        private val range: RamadanRange = sampleRamadanRange()
    ) : PrayerRefreshRepository {
        @Volatile
        var refreshCalls = 0
            private set

        override suspend fun refreshAndCache(): RefreshResult {
            refreshCalls += 1
            if (failRefresh) {
                val old = store.readCache() ?: return RefreshResult.Failure(IOException("offline"))
                return RefreshResult.StaleCache(old, IOException("offline"))
            }
            val refreshed = cache(store.location, PrayerTimeProvider().today(store.location.timezone))
            store.saveCache(refreshed)
            return RefreshResult.Success(refreshed)
        }

        override suspend fun ensureMonth(yearMonth: YearMonth): MonthResult = MonthResult(
            yearMonth = yearMonth,
            days = (1..yearMonth.lengthOfMonth()).map { day ->
                cache(store.location, yearMonth.atDay(day))
            },
            source = MonthDataSource.CACHE
        )

        override suspend fun ramadanRange() = range
    }

    private class FakeNotificationSettings(
        initial: PrayerNotificationSettings
    ) : PrayerNotificationSettingsSource {
        private val state = MutableStateFlow(initial)
        val current: PrayerNotificationSettings get() = state.value
        override val settings: Flow<PrayerNotificationSettings> = state
        override suspend fun read() = state.first()
        override suspend fun save(settings: PrayerNotificationSettings) {
            state.value = settings
        }
    }

    private class FakeScheduler : PrayerRefreshScheduler {
        override suspend fun enqueueRefresh(force: Boolean) = Unit
        override suspend fun scheduleBoundary() = Unit
        override suspend fun scheduleNotifications() = Unit
    }

    private class FakeWidgetUpdater : PrayerWidgetUpdatePort {
        override suspend fun updateAll() = Unit
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
        val ACCESSIBLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM EEEE", Locale.forLanguageTag("tr-TR"))

        fun cache(location: PrayerLocation, date: LocalDate) = CachedPrayerDay(
            date = date,
            location = location,
            timezone = location.timezone,
            settings = PrayerCalculationSettings(),
            prayerTimes = PrayerTimes(
                LocalTime.of(4, 30), LocalTime.of(6, 0), LocalTime.of(12, 30),
                LocalTime.of(16, 0), LocalTime.of(19, 0), LocalTime.of(21, 0)
            ),
            hijriText = "10 Rebiülevvel 1448",
            fetchedAt = Instant.now(),
            source = PrayerDataSource.ALADHAN
        )

        fun sampleRamadanRange(): RamadanRange {
            val firstDay = LocalDate.of(2026, 2, 18)
            val lastDay = firstDay.plusDays(28)
            return RamadanRange(
                hijriYear = 1447,
                startDate = firstDay,
                endDate = lastDay,
                eidDate = lastDay.plusDays(1),
                estimated = false
            )
        }

        fun estimated2036RamadanRange(): RamadanRange {
            val start = LocalDate.of(2036, 11, 10)
            val eid = LocalDate.of(2036, 12, 9)
            return RamadanRange(
                hijriYear = 1458,
                startDate = start,
                endDate = eid.minusDays(1),
                eidDate = eid,
                estimated = true
            )
        }
    }
}
