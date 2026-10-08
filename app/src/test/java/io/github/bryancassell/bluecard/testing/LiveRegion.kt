package io.github.bryancassell.bluecard.testing

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityRecord
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/** A polite live region, which screen readers announce when it changes. */
val isPoliteLiveRegion =
    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

private val isHidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

/**
 * Runs [show], which brings up [message], and checks that screen readers announce it: it's in a
 * polite live region that was already composed, hidden while it was empty, which Compose announces
 * when its text changes, unlike a new one (see `ScreenMessage`).
 */
fun SemanticsNodeInteractionsProvider.assertAnnouncedWhenShown(message: String, show: () -> Unit) {
    onNodeWithText(message).assertDoesNotExist()
    val hiddenBefore = onAllNodes(isPoliteLiveRegion and isHidden).fetchSemanticsNodes()
        .map { it.id }
    val wasHiddenBefore = SemanticsMatcher("was hidden in its place before it was shown") {
        it.id in hiddenBefore
    }
    show()
    onNodeWithText(message).assert(isPoliteLiveRegion and !isHidden).assert(wasHiddenBefore)
}

/**
 * Turns on a screen reader, as far as Compose can tell, and lists what TalkBack would read out
 * from live regions: the text of each live region that's the source of a content change. TalkBack
 * reads one on any such change, whatever changed, and on nothing else. Create it before setting
 * the content.
 */
class LiveRegionReadouts {
    private val readouts = mutableListOf<String>()
    private var readoutsListed = 0
    private var listeningTo: View? = null

    init {
        val accessibilityManager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(AccessibilityManager::class.java)
        shadowOf(accessibilityManager).apply {
            setEnabled(true)
            setTouchExplorationEnabled(true)
            setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
        }
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
                    if (liveRegion != null) readouts += liveRegion.text.toString()
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
