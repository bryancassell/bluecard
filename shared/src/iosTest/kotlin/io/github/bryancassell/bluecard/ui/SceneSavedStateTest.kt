package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import io.github.bryancassell.bluecard.ui.navigation.BadgeDetail
import io.github.bryancassell.bluecard.ui.navigation.BlueCardNavKey
import io.github.bryancassell.bluecard.ui.navigation.Home
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SceneSavedStateTest {
    private fun sceneState(saved: String = "", build: String = BUILD) =
        SceneSavedState(saved, build)

    /** [value], saved under a key, as a new [SceneSavedState] restores it from the saved text. */
    private fun savedAndRestored(value: Any): Any? {
        val state = sceneState()
        state.registry.registerProvider(KEY) { value }
        return sceneState(state.save()).registry.consumeRestored(KEY)
    }

    @Test
    fun newState_restoresNothing() {
        assertNull(sceneState().registry.consumeRestored(KEY))
    }

    @Test
    fun restore_stateAnotherBuildSaved_restoresNothing() {
        val state = sceneState(build = "0.1.0 (1)")
        state.registry.registerProvider(KEY) { "Camping" }

        val restored = sceneState(state.save(), build = "0.1.0 (2)").registry

        assertNull(restored.consumeRestored(KEY))
    }

    @Test
    fun save_keepsEachValueUnderItsKey() {
        val state = sceneState()
        state.registry.registerProvider("page") { "Camping" }
        state.registry.registerProvider("scroll") { 12 }

        val restored = sceneState(state.save()).registry

        assertEquals("Camping", restored.consumeRestored("page"))
        assertEquals(12, restored.consumeRestored("scroll"))
    }

    @Test
    fun save_keepsEachValuesType() {
        // Pages read a value back as the type they saved, so 1 mustn't come back as a Long.
        assertIs<Int>(savedAndRestored(1))
        assertIs<Long>(savedAndRestored(1L))
        assertIs<Float>(savedAndRestored(1f))
        assertIs<Double>(savedAndRestored(1.0))
        assertEquals('a', savedAndRestored('a'))
        assertEquals(true, savedAndRestored(true))
        assertEquals("Camping", savedAndRestored("Camping"))
        assertEquals(Long.MAX_VALUE, savedAndRestored(Long.MAX_VALUE))
    }

    @Test
    fun save_keepsNumbersJsonCantWrite() {
        assertEquals(Float.NaN, savedAndRestored(Float.NaN))
        assertEquals(Double.NEGATIVE_INFINITY, savedAndRestored(Double.NEGATIVE_INFINITY))
        assertEquals(0.1f, savedAndRestored(0.1f))
    }

    @Test
    fun save_keepsArrays() {
        assertContentEquals(
            booleanArrayOf(true, false),
            savedAndRestored(booleanArrayOf(true, false)) as BooleanArray
        )
        assertContentEquals(
            charArrayOf('a', '"'),
            savedAndRestored(charArrayOf('a', '"')) as CharArray
        )
        assertContentEquals(intArrayOf(1, 2), savedAndRestored(intArrayOf(1, 2)) as IntArray)
        assertContentEquals(longArrayOf(1, 2), savedAndRestored(longArrayOf(1, 2)) as LongArray)
        assertContentEquals(floatArrayOf(0.5f), savedAndRestored(floatArrayOf(0.5f)) as FloatArray)
        assertContentEquals(doubleArrayOf(0.5), savedAndRestored(doubleArrayOf(0.5)) as DoubleArray)
        assertEquals(listOf("a", null), (savedAndRestored(arrayOf("a", null)) as Array<*>).toList())
    }

    @Test
    fun save_keepsMapsMutable() {
        // SaveableStateHolder takes a page's state out of the map it restores.
        val pages = savedAndRestored(mapOf("Home" to listOf(1, null), 2 to "Badges"))

        assertEquals(mapOf("Home" to listOf(1, null), 2 to "Badges"), pages)
        assertIs<MutableMap<Any?, Any?>>(pages).remove("Home")
        assertEquals(mapOf<Any?, Any?>(2 to "Badges"), pages)
    }

    @Test
    fun save_keepsWhatASavedStateHolds() {
        val restored = savedAndRestored(
            savedState {
                putBoolean("boolean", true)
                putChar("char", 'a')
                putDouble("double", 0.5)
                putFloat("float", 0.5f)
                putInt("int", 1)
                putLong("long", 1)
                putNull("null")
                putString("string", "Camping")
                putIntList("intList", listOf(1, 2))
                putStringList("stringList", listOf("a"))
                putStringArray("stringArray", arrayOf("a"))
                putIntArray("intArray", intArrayOf(1))
                putSavedState("nested", savedState { putString("text", "Pitched a tent") })
                putSavedStateList("nestedList", listOf(savedState { putInt("int", 2) }))
            }
        )

        assertIs<SavedState>(restored)
        restored.read {
            assertTrue(getBoolean("boolean"))
            assertEquals('a', getChar("char"))
            assertEquals(0.5, getDouble("double"))
            assertEquals(0.5f, getFloat("float"))
            assertEquals(1, getInt("int"))
            assertEquals(1L, getLong("long"))
            assertTrue(isNull("null"))
            assertEquals("Camping", getString("string"))
            assertEquals(listOf(1, 2), getIntList("intList"))
            assertEquals(listOf("a"), getStringList("stringList"))
            assertContentEquals(arrayOf("a"), getStringArray("stringArray"))
            assertContentEquals(intArrayOf(1), getIntArray("intArray"))
            assertEquals("Pitched a tent", getSavedState("nested").read { getString("text") })
            assertEquals(2, getSavedStateList("nestedList").single().read { getInt("int") })
        }
    }

    @Test
    fun save_keepsTheBackStack() {
        val serializer = NavBackStackSerializer(BlueCardNavKey.serializer())
        val backStack = NavBackStack<BlueCardNavKey>(Home, BadgeDetail("camping"))

        val restored = savedAndRestored(encodeToSavedState(serializer, backStack))

        assertEquals(
            backStack.toList(),
            decodeFromSavedState(serializer, assertIs<SavedState>(restored)).toList()
        )
    }

    @Test
    fun save_keepsAMutableStatesValueAndPolicy() {
        val policies = listOf<SnapshotMutationPolicy<Any?>>(
            structuralEqualityPolicy(),
            referentialEqualityPolicy(),
            neverEqualPolicy()
        )
        for (policy in policies) {
            val restored = savedAndRestored(mutableStateOf<Any?>("Camping", policy))

            assertIs<SnapshotMutableState<*>>(restored)
            assertEquals("Camping", restored.value)
            assertSame(policy, restored.policy)
        }
    }

    @Test
    fun canBeSaved_refusesWhatItCantSave() {
        val registry = sceneState().registry
        val ownPolicy = object : SnapshotMutationPolicy<Int> {
            override fun equivalent(a: Int, b: Int) = a == b
        }

        assertFalse(registry.canBeSaved(Any()))
        assertFalse(registry.canBeSaved(listOf(Any())))
        assertFalse(registry.canBeSaved(mapOf("key" to Any())))
        assertFalse(registry.canBeSaved(savedState(mapOf("key" to Any()))))
        assertFalse(registry.canBeSaved(mutableStateOf(Any())))
        assertFalse(registry.canBeSaved(mutableStateOf(1, ownPolicy)))
        assertTrue(registry.canBeSaved(mutableStateOf(listOf(1))))
    }

    @Test
    fun save_whenAValueCantBeSaved_fails() {
        // A bug, as an unsaveable value in a Bundle is on Android, so it isn't caught.
        val state = sceneState()
        val holder: MutableState<Any> = mutableStateOf("Camping")
        state.registry.registerProvider(KEY) { holder }
        holder.value = Any()

        assertFailsWith<IllegalStateException> { state.save() }
    }

    private companion object {
        const val KEY = "key"
        const val BUILD = "0.1.0 (1)"
    }
}
