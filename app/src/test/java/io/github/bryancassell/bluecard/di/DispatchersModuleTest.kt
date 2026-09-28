package io.github.bryancassell.bluecard.di

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertSame
import org.junit.Test

class DispatchersModuleTest {
    @Test
    fun ioDispatcher_isDispatchersIo() {
        assertSame(Dispatchers.IO, DispatchersModule.provideIoDispatcher())
    }
}
