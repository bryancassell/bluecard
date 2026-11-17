package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HoldableTextToolbarTest {
    private val platform = RecordingTextToolbar()
    private val toolbar = HoldableTextToolbar(platform)
    private val rect = Rect(left = 10f, top = 20f, right = 110f, bottom = 64f)
    private val autofill = {}

    // As a text field calls it, with Autofill.
    private fun showAsTheFieldDoes() = toolbar.showMenu(
        rect,
        onCopyRequested = null,
        onPasteRequested = null,
        onCutRequested = null,
        onSelectAllRequested = {},
        onAutofillRequested = autofill
    )

    @Test
    fun showMenu_whenNotHeld_showsThePlatformsMenu_withItsItems() {
        showAsTheFieldDoes()

        assertEquals(listOf("show at $rect"), platform.calls)
        assertSame(autofill, platform.autofill)
    }

    @Test
    fun showMenu_withoutAutofill_whenNotHeld_showsThePlatformsMenu() {
        toolbar.showMenu(rect, onCopyRequested = {})

        assertEquals(listOf("show at $rect without Autofill"), platform.calls)
    }

    @Test
    fun hold_hidesTheMenu_andKeepsItHidden() {
        toolbar.hold()

        showAsTheFieldDoes()
        toolbar.showMenu(rect, onCopyRequested = {})

        assertEquals(listOf("hide"), platform.calls)
    }

    // So the menu shows again as the keyboard comes back after Cancel and moves the field.
    @Test
    fun onceReleased_theMenuShowsAgain() {
        val release = toolbar.hold()

        release()
        showAsTheFieldDoes()

        assertEquals(listOf("hide", "show at $rect"), platform.calls)
    }

    @Test
    fun withTwoHolds_theMenuStaysHidden_untilBothAreReleased() {
        val releaseFirst = toolbar.hold()
        val releaseSecond = toolbar.hold()

        releaseFirst()
        showAsTheFieldDoes()
        releaseSecond()
        showAsTheFieldDoes()

        assertEquals(listOf("hide", "hide", "show at $rect"), platform.calls)
    }

    // An alert UIKit won't show lets go of the menu then, and again as it closes.
    @Test
    fun releasingTwice_letsGoOfOneHoldOnly() {
        val releaseFirst = toolbar.hold()
        toolbar.hold()

        releaseFirst()
        releaseFirst()
        showAsTheFieldDoes()

        assertEquals(listOf("hide", "hide"), platform.calls)
    }

    @Test
    fun hideAndStatus_areThePlatforms() {
        toolbar.hide()
        platform.status = TextToolbarStatus.Shown

        assertEquals(listOf("hide"), platform.calls)
        assertEquals(TextToolbarStatus.Shown, toolbar.status)
    }

    private class RecordingTextToolbar : TextToolbar {
        val calls = mutableListOf<String>()
        var autofill: (() -> Unit)? = null
        override var status = TextToolbarStatus.Hidden

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
            onAutofillRequested: (() -> Unit)?
        ) {
            autofill = onAutofillRequested
            calls += "show at $rect"
        }

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?
        ) {
            calls += "show at $rect without Autofill"
        }

        override fun hide() {
            calls += "hide"
        }
    }
}
