package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DropUnlessResumedTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val calls = mutableListOf<String>()

    private fun callback(): (String) -> Unit {
        lateinit var callback: (String) -> Unit
        composeTestRule.setContent {
            callback = dropUnlessResumed(lifecycleOwner) { value: String -> calls += value }
        }
        return callback
    }

    @Test
    fun resumed_runsBlock() {
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED

        callback()("camping")

        assertEquals(listOf("camping"), calls)
    }

    @Test
    fun started_dropsCall() {
        // As NavDisplay holds a screen while it animates in or out.
        lifecycleOwner.registry.currentState = Lifecycle.State.STARTED

        callback()("camping")

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun checksStateWhenCalled() {
        lifecycleOwner.registry.currentState = Lifecycle.State.STARTED
        val callback = callback()

        callback("dropped")
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        callback("camping")

        assertEquals(listOf("camping"), calls)
    }
}
