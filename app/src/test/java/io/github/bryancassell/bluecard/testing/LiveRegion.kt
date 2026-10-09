package io.github.bryancassell.bluecard.testing

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityRecord
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/** A polite live region, which screen readers announce when it changes. */
val isPoliteLiveRegion =
    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

private val isHidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

/**
 * Runs [show], which brings up [message], and checks that screen readers announce it: it's a
 * polite live region, in the node composed hidden in its place while the screen loaded (see
 * `LoadingOrMessage`).
 */
fun SemanticsNodeInteractionsProvider.assertAnnouncedWhenShown(message: String, show: () -> Unit) {
    onNodeWithText(message).assertDoesNotExist()
    val hiddenBefore = onAllNodes(isHidden).fetchSemanticsNodes().map { it.id }
    val wasHiddenBefore = SemanticsMatcher("was hidden in its place before it was shown") {
        it.id in hiddenBefore
    }
    show()
    onNodeWithText(message).assert(isPoliteLiveRegion and !isHidden).assert(wasHiddenBefore)
}

/** Turns on a screen reader, as far as Compose can tell. Call it before setting the content. */
fun turnOnScreenReader() {
    val accessibilityManager = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(AccessibilityManager::class.java)
    shadowOf(accessibilityManager).apply {
        setEnabled(true)
        setTouchExplorationEnabled(true)
        setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
    }
}

/**
 * Turns on a screen reader, as far as Compose can tell, and lists what TalkBack would read out
 * from live regions: the content description, or else the text, of each live region that's the
 * source of a content change. TalkBack reads one on any such change, whatever changed, and on
 * nothing else. Create it before setting
 * the content.
 */
class LiveRegionReadouts {
    private val readouts = mutableListOf<String>()
    private var readoutsListed = 0
    private var listeningTo: View? = null

    init {
        turnOnScreenReader()
    }

    /**
     * Listens to the events [view] sends. Call it while composing [view]'s content, before it
     * sends any.
     *
     * Each event's source is looked at as the event is sent. TalkBack looks at it a little later,
     * so it can see the same state, and one that lasted only until the next frame was read out
     * (#278).
     */
    fun listenTo(view: View) {
        // Called again as the content recomposes.
        if (view === listeningTo) return
        listeningTo = view
        (view.parent as ViewGroup).accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onRequestSendAccessibilityEvent(
                host: ViewGroup,
                child: View,
                event: AccessibilityEvent
            ): Boolean {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    val sourceId = shadowOf(event as AccessibilityRecord).virtualDescendantId
                    val liveRegion = child.accessibilityNodeProvider
                        ?.createAccessibilityNodeInfo(sourceId)
                        ?.takeIf { it.liveRegion != View.ACCESSIBILITY_LIVE_REGION_NONE }
                    // TalkBack reads a content description in place of the text. Without one,
                    // it reads the text and then each child's, which this doesn't list, so a test
                    // can't pass on a readout it missed.
                    val readout = liveRegion?.run {
                        val description = contentDescription.takeUnless { it.isNullOrEmpty() }
                        check(description != null || childCount == 0) {
                            "TalkBack would read this live region's children too: $this"
                        }
                        description ?: text
                    }
                    if (!readout.isNullOrEmpty()) readouts += readout.toString()
                }
                return super.onRequestSendAccessibilityEvent(host, child, event)
            }
        }
    }

    /** What TalkBack would have read out since the last call. */
    fun sinceLastCall(): List<String> = readouts.drop(readoutsListed).also {
        readoutsListed = readouts.size
    }
}

/**
 * Waits [milliseconds], then as long as Compose takes to send the accessibility events for what
 * changed, a frame at a time, running the work posted to the main thread between frames, as a
 * phone does. Compose sends accessibility events from posted work, at most every 100 ms, which can
 * run before the next frame composes the rest of a change.
 *
 * State the test changed is applied before each frame, as Compose applies a change made outside
 * composition straight away on a phone (`GlobalSnapshotManager`). The test clock applies it only
 * after one of its coroutines resumes, so with none waiting, a change made just before this went
 * unseen, though only after other test classes had run.
 */
fun ComposeTestRule.waitRunningPostedWork(milliseconds: Long = 0) {
    val clock = mainClock
    val autoAdvance = clock.autoAdvance
    clock.autoAdvance = false
    val end = clock.currentTime + milliseconds + 500
    while (clock.currentTime < end) {
        Snapshot.sendApplyNotifications()
        clock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idle()
    }
    clock.autoAdvance = autoAdvance
}
