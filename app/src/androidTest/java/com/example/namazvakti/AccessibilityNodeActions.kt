package com.example.namazvakti

import android.app.Instrumentation
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Reacquires a visible node and clicks only after its clickable bounds stop moving. */
internal fun Instrumentation.clickStableAccessibilityNode(
    matches: (AccessibilityNodeInfo) -> Boolean,
    timeoutMillis: Long,
    pollMillis: Long,
    beforePoll: () -> Unit = {}
): Boolean {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    var previousBounds: Rect? = null
    var stableSamples = 0
    do {
        beforePoll()
        val candidate = uiAutomation.rootInActiveWindow?.findVisibleNode(matches)
        val clickTarget = candidate?.clickableAncestor()
        if (candidate?.isVisibleToUser == true && clickTarget != null) {
            val bounds = Rect().also(clickTarget::getBoundsInScreen)
            if (!bounds.isEmpty) {
                if (bounds == previousBounds) {
                    stableSamples += 1
                } else {
                    previousBounds = Rect(bounds)
                    stableSamples = 1
                }
                if (stableSamples >= STABLE_SAMPLES) {
                    val freshCandidate = uiAutomation.rootInActiveWindow?.findVisibleNode(matches)
                    val freshTarget = freshCandidate?.clickableAncestor()
                    if (freshCandidate?.isVisibleToUser == true && freshTarget != null &&
                        freshTarget.isEnabled && freshTarget.refresh()
                    ) {
                        val freshBounds = Rect().also(freshTarget::getBoundsInScreen)
                        if (freshBounds == bounds &&
                            freshTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        ) {
                            return true
                        }
                    }
                }
            } else {
                previousBounds = null
                stableSamples = 0
            }
        } else {
            previousBounds = null
            stableSamples = 0
        }
        SystemClock.sleep(pollMillis)
    } while (SystemClock.uptimeMillis() < deadline)
    return false
}

private fun AccessibilityNodeInfo.findVisibleNode(
    matches: (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo? {
    if (isVisibleToUser && matches(this)) return this
    for (index in 0 until childCount) {
        getChild(index)?.findVisibleNode(matches)?.let { return it }
    }
    return null
}

private fun AccessibilityNodeInfo.clickableAncestor(): AccessibilityNodeInfo? =
    generateSequence(this) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }

private const val STABLE_SAMPLES = 3
