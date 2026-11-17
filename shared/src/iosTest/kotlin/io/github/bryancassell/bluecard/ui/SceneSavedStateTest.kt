package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import io.github.bryancassell.bluecard.testing.FakeWarningLog
import io.github.bryancassell.bluecard.ui.navigation.BadgeDetail
import io.github.bryancassell.bluecard.ui.navigation.BlueCardNavKey
import io.github.bryancassell.bluecard.ui.navigation.Home
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import platform.Foundation.NSUserDefaults

class SceneSavedStateTest {
    private val defaults = NSUserDefaults(suiteName = SUITE)
    private val warningLog = FakeWarningLog()

    @BeforeTest
    @AfterTest
    fun removeDefaults() {
        defaults.removePersistentDomainForName(SUITE)
    }

    private fun sceneState(saved: String = "", build: String = BUILD) =
        SceneSavedState(saved, build, defaults, warningLog)

    /** [value], saved under a key, as a new [SceneSavedState] restores it from the saved text. */
    private fun savedAndRestored(value: Any): Any? {
        val state = sceneState()
        state.registry.registerProvider(KEY) { value }
        return sceneState(state.paused()).registry.consumeRestored(KEY)
    }

    /** The text a state saved by this build, with [value] under [KEY], would be. */
    private fun savedText(value: String) = """{"build":"$BUILD","values":{"$KEY":[$value]}}"""

    @Test
    fun newState_restoresNothing() {
        assertNull(sceneState().registry.consumeRestored(KEY))
        assertEquals(emptyList(), warningLog.warnings)
    }

    @Test
    fun restore_stateAnotherBuildSaved_restoresNothing() {
        val state = sceneState(build = "0.1.0 (1)")
        state.registry.registerProvider(KEY) { "Camping" }

        val restored = sceneState(state.paused(), build = "0.1.0 (2)").registry

        assertNull(restored.consumeRestored(KEY))
        assertEquals(emptyList(), warningLog.warnings)
    }

    @Test
    fun restore_stateSavedWithoutABuild_restoresNothing() {
        // As debug builds of this class's first version saved it.
        val restored = sceneState("""{"$KEY":[{"String":"Camping"}]}""").registry

        assertNull(restored.consumeRestored(KEY))
        assertEquals(emptyList(), warningLog.warnings)
    }

    @Test
    fun restore_textItCantRead_restoresNothingAndWarns() {
        val unreadable = listOf(
            "{",
            "Camping",
            "[]",
            """{"build":"$BUILD"}""",
            """{"build":"$BUILD","values":[]}""",
            savedText("""{"Unknown":1}"""),
            savedText("{}"),
            savedText("""{"Int":"twelve"}"""),
            savedText("""{"Boolean":"maybe"}"""),
            savedText("""{"Char":""}"""),
            savedText("""{"MutableState":["structural"]}"""),
            savedText("""{"MutableState":["own",{"Int":1}]}""")
        )
        for (text in unreadable) {
            // Else the last state, still open, would keep this one from being read.
            removeDefaults()
            val restored = sceneState(text).registry

            assertNull(restored.consumeRestored(KEY), text)
            val warning = warningLog.warnings.last()
            assertEquals("SceneSavedState", warning.tag, text)
            assertNotNull(warning.error, text)
        }
        assertEquals(unreadable.size, warningLog.warnings.size)
    }

    @Test
    fun restore_afterARunThatEndedWhileOpen_restoresNothingAndWarns() {
        val state = sceneState()
        state.registry.registerProvider(KEY) { "Camping" }
        val saved = state.paused()
        state.resumed()

        val restored = sceneState(saved).registry

        assertNull(restored.consumeRestored(KEY))
        assertEquals("SceneSavedState", warningLog.warnings.single().tag)
    }

    @Test
    fun restore_afterARunThatEndedAsItRestored_restoresNothing() {
        // As if restoring the pages crashed the app.
        val state = sceneState()
        state.registry.registerProvider(KEY) { "Camping" }
        val saved = state.paused()
        sceneState(saved)

        assertNull(sceneState(saved).registry.consumeRestored(KEY))
    }

    @Test
    fun restore_afterARunThatEndedPaused_restores() {
        val state = sceneState()
        state.registry.registerProvider(KEY) { "Camping" }
        state.resumed()
        val saved = state.paused()

        assertEquals("Camping", sceneState(saved).registry.consumeRestored(KEY))
    }

    @Test
    fun paused_keepsEachValueUnderItsKey() {
        val state = sceneState()
        state.registry.registerProvider("page") { "Camping" }
        state.registry.registerProvider("scroll") { 12 }

        val restored = sceneState(state.paused()).registry

        assertEquals("Camping", restored.consumeRestored("page"))
        assertEquals(12, restored.consumeRestored("scroll"))
    }

    @Test
    fun paused_keepsEachValuesType() {
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
    fun paused_keepsNumbersJsonCantWrite() {
        assertEquals(Float.NaN, savedAndRestored(Float.NaN))
        assertEquals(Double.NEGATIVE_INFINITY, savedAndRestored(Double.NEGATIVE_INFINITY))
        assertEquals(0.1f, savedAndRestored(0.1f))
    }

    @Test
    fun paused_keepsArrays() {
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
    fun paused_keepsMapsMutable() {
        // SaveableStateHolder takes a page's state out of the map it restores.
        val pages = savedAndRestored(mapOf("Home" to listOf(1, null), 2 to "Badges"))

        assertEquals(mapOf("Home" to listOf(1, null), 2 to "Badges"), pages)
        assertIs<MutableMap<Any?, Any?>>(pages).remove("Home")
        assertEquals(mapOf<Any?, Any?>(2 to "Badges"), pages)
    }

    @Test
    fun paused_keepsWhatASavedStateHolds() {
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
    fun paused_keepsTheBackStack() {
        val serializer = NavBackStackSerializer(BlueCardNavKey.serializer())
        val backStack = NavBackStack<BlueCardNavKey>(Home, BadgeDetail("camping"))

        val restored = savedAndRestored(encodeToSavedState(serializer, backStack))

        assertEquals(
            backStack.toList(),
            decodeFromSavedState(serializer, assertIs<SavedState>(restored)).toList()
        )
    }

    @Test
    fun paused_keepsAMutableStatesValueAndPolicy() {
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
    fun paused_keepsPrimitiveStatesAsTheirOwnTypes() {
        // A page reads one back as the type it remembered, as Android's Parcelable ones are.
        assertEquals(1, assertIs<MutableIntState>(savedAndRestored(mutableIntStateOf(1))).intValue)
        assertEquals(
            Long.MAX_VALUE,
            assertIs<MutableLongState>(
                savedAndRestored(mutableLongStateOf(Long.MAX_VALUE))
            ).longValue
        )
        assertEquals(
            0.1f,
            assertIs<MutableFloatState>(savedAndRestored(mutableFloatStateOf(0.1f))).floatValue
        )
        assertEquals(
            Double.NaN,
            assertIs<MutableDoubleState>(
                savedAndRestored(mutableDoubleStateOf(Double.NaN))
            ).doubleValue
        )
    }

    @Test
    fun paused_keepsASnapshotStateList() {
        val restored = savedAndRestored(mutableStateListOf("Camping", null))

        assertEquals(listOf("Camping", null), assertIs<SnapshotStateList<*>>(restored).toList())
    }

    @Test
    fun canBeSaved_acceptsEveryValue() {
        // Android saves enums and an unkeyed lazy item's key, which are Serializable or
        // Parcelable there, so refusing them would crash shared code on iOS alone.
        val registry = sceneState().registry

        assertTrue(registry.canBeSaved(Any()))
        assertTrue(registry.canBeSaved(Tab.Badges))
        assertTrue(registry.canBeSaved(mutableStateMapOf("key" to 1)))
    }

    @Test
    fun paused_leavesOutWhatItCantWrite() {
        val state = sceneState()
        val ownPolicy = object : SnapshotMutationPolicy<Int> {
            override fun equivalent(a: Int, b: Int) = a == b
        }
        state.registry.registerProvider("kept") { "Camping" }
        state.registry.registerProvider("enum") { Tab.Badges }
        state.registry.registerProvider("ownPolicy") { mutableStateOf(1, ownPolicy) }
        state.registry.registerProvider("stateMap") { mutableStateMapOf("key" to 1) }
        // As SaveableStateHolder saves a page, with a lazy item's default key.
        state.registry.registerProvider("pages") {
            mapOf(
                "Badges" to mapOf("tab" to listOf(Tab.Badges), "text" to listOf("Pitched a tent")),
                LazyKey(0) to mapOf("text" to listOf("Item"))
            )
        }
        state.registry.registerProvider("savedState") {
            savedState(mapOf("kept" to 1, "enum" to Tab.Badges))
        }

        val restored = sceneState(state.paused()).registry

        assertEquals("Camping", restored.consumeRestored("kept"))
        assertNull(restored.consumeRestored("enum"))
        assertNull(restored.consumeRestored("ownPolicy"))
        assertNull(restored.consumeRestored("stateMap"))
        assertEquals(
            mapOf("Badges" to mapOf("text" to listOf("Pitched a tent"))),
            restored.consumeRestored("pages")
        )
        assertIs<SavedState>(restored.consumeRestored("savedState")).read {
            assertEquals(1, getInt("kept"))
            assertFalse(contains("enum"))
        }
        val warning = warningLog.warnings.single()
        assertEquals("SceneSavedState", warning.tag)
        assertTrue(warning.message.startsWith("Left out 6 "), warning.message)
    }

    @Test
    fun paused_whenAValueChangesToOneItCantWrite_leavesItOut() {
        val state = sceneState()
        val holder: MutableState<Any> = mutableStateOf("Camping")
        state.registry.registerProvider(KEY) { holder }
        holder.value = Any()

        assertNull(sceneState(state.paused()).registry.consumeRestored(KEY))
    }

    @Test
    fun appBuild_isTheVersionAndBuildNumber() {
        val info = mapOf("CFBundleShortVersionString" to "0.1.0", "CFBundleVersion" to "12")

        assertEquals("0.1.0 (12)", appBuild { info[it] })
    }

    private enum class Tab { Badges }

    /** As Compose's DefaultLazyKey is on iOS, a plain data class. */
    private data class LazyKey(val index: Int)

    private companion object {
        const val KEY = "key"
        const val BUILD = "0.1.0 (1)"
        const val SUITE = "SceneSavedStateTest"
    }
}
