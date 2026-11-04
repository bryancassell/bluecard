package io.github.bryancassell.bluecard.text

import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class IosPhoneNumberFormatTest : PhoneNumberFormatContract() {
    override val phoneNumberFormat = IosPhoneNumberFormat(UnconfinedTestDispatcher())

    @Test
    fun format_runsOnTheIoDispatcher() = runTest {
        var dispatched = false
        val io = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatched = true
                block.run()
            }
        }

        IosPhoneNumberFormat(io).format("5551234567")

        assertTrue(dispatched)
    }
}
