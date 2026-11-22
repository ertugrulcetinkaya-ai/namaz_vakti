package com.example.namazvakti

import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.namazvakti.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val dismissSystemDialogsRule = DismissSystemDialogsRule()

    @Test
    fun systemBackFromImsakiyeReturnsToMonthlyAfterActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val monthly = context.getString(R.string.monthly_open)
            val imsakiye = context.getString(R.string.imsakiye_open)
            val nextMonth = context.getString(R.string.next_month)

            awaitNode(monthly) { it.text?.toString() == monthly }.click()
            awaitNode(imsakiye) { it.contentDescription?.toString() == imsakiye }.click()
            awaitImsakiyeTitle()

            scenario.recreate()
            awaitImsakiyeTitle()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

            awaitNode(nextMonth) { it.contentDescription?.toString() == nextMonth }
        }
    }

    @Test
    @Suppress("DEPRECATION") // isChecked also works on the API 35 test device.
    fun unsavedNotificationSettingSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val settings = context.getString(R.string.notification_settings_open)
            awaitNode(settings) { it.contentDescription?.toString() == settings && it.isEnabled }.click()

            val notificationSwitch = awaitNotificationSwitch()
            val enabled = notificationSwitch.isChecked
            notificationSwitch.click()

            scenario.recreate()

            assertEquals(!enabled, awaitNotificationSwitch().isChecked)
        }
    }

    private fun awaitNotificationSwitch(): AccessibilityNodeInfo {
        val title = context.getString(R.string.notification_settings_title)
        awaitNode(title) { it.text?.toString() == title }
        return awaitNode("notification switch") { it.isCheckable && it.isEnabled }
    }

    private fun AccessibilityNodeInfo.click() {
        val actionNode = generateSequence(this) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }
        assertTrue(
            "Could not click ${text ?: contentDescription}",
            actionNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        )
    }

    private fun awaitImsakiyeTitle() = awaitNode("İmsakiye title") {
        it.text?.toString()?.endsWith("İmsakiyesi") == true
    }

    private fun awaitNode(
        description: String,
        matches: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo {
        // Cold start on a CI emulator (first DataStore/Room access and the initial refresh) can exceed 10s.
        val deadline = SystemClock.uptimeMillis() + AWAIT_TIMEOUT_MS
        do {
            dismissSystemDialogsRule.dismissSystemDialogs()
            instrumentation.uiAutomation.rootInActiveWindow?.findNode(matches)?.let { return it }
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        val visible = instrumentation.uiAutomation.rootInActiveWindow?.visibleTexts().orEmpty()
        throw AssertionError("Compose screen did not show $description; visible: $visible")
    }

    private fun AccessibilityNodeInfo.visibleTexts(): List<String> = buildList {
        if (isVisibleToUser) {
            (text ?: contentDescription)?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        }
        for (index in 0 until childCount) {
            getChild(index)?.visibleTexts()?.let(::addAll)
        }
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 30_000L
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
}
