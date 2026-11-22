package com.example.namazvakti

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class DismissSystemDialogsRule : TestWatcher() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    override fun starting(description: Description) {
        hideErrorDialogs()
        dismissSystemDialogs()
    }

    fun dismissSystemDialogs() {
        val deadline = SystemClock.uptimeMillis() + DISMISS_WINDOW_MILLIS
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return
            if (!root.containsSystemAnrMessage()) return

            val waitButton = root.findNode { node ->
                node.isVisibleToUser && node.text?.toString()?.let(::isWaitButton) == true
            } ?: return
            val clickTarget = generateSequence(waitButton) { it.parent }
                .firstOrNull { it.isClickable && it.isEnabled }
                ?: waitButton
            if (!clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return

            SystemClock.sleep(RECHECK_DELAY_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
    }

    private fun hideErrorDialogs() {
        try {
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "settings put global hide_error_dialogs 1"
                )
            ).use { it.readBytes() }
        } catch (_: Exception) {
            // Some emulator images do not allow this shell setting; keep the test running.
        }
    }

    private fun isWaitButton(text: String): Boolean =
        text.trim().equals("Wait", ignoreCase = true) ||
            text.trim().equals("Bekle", ignoreCase = true)

    private fun AccessibilityNodeInfo.containsSystemAnrMessage(): Boolean {
        val labels = listOfNotNull(text?.toString(), contentDescription?.toString())
        if (labels.any { label ->
                label.contains("isn't responding", ignoreCase = true) ||
                    label.contains("yanıt vermiyor", ignoreCase = true)
            }
        ) {
            return true
        }

        for (index in 0 until childCount) {
            if (getChild(index)?.containsSystemAnrMessage() == true) return true
        }
        return false
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

    private companion object {
        const val DISMISS_WINDOW_MILLIS = 1_000L
        const val RECHECK_DELAY_MILLIS = 100L
    }
}
