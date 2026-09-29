package io.github.bryancassell.bluecard.di

import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineScopesModuleTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScopesModule.provideApplicationScope(dispatcher)

    @Test
    fun applicationScope_runsOnGivenDispatcher() {
        var ranOn: Any? = null

        scope.launch { ranOn = coroutineContext[ContinuationInterceptor] }

        assertEquals(dispatcher, ranOn)
        scope.cancel()
    }

    @Test
    fun applicationScope_keepsRunningWhenOneCoroutineFails() {
        val failures = mutableListOf<Throwable>()

        scope.launch(CoroutineExceptionHandler { _, e -> failures += e }) {
            throw IllegalStateException("A bug")
        }

        assertEquals(1, failures.size)
        assertTrue(scope.isActive)
        scope.cancel()
    }
}
