package ar.felaufernandezbottaro.vinculovial.presentation.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResendCountdownTest {

    @Test
    fun `arranca en cero hasta que se inicia`() = runTest {
        val countdown = ResendCountdown(backgroundScope, totalSeconds = 3)

        assertEquals(0, countdown.remainingSeconds.value)
    }

    @Test
    fun `al iniciar muestra el total y baja de a un segundo hasta cero`() = runTest {
        val countdown = ResendCountdown(backgroundScope, totalSeconds = 3)

        countdown.start()
        assertEquals(3, countdown.remainingSeconds.value)

        advanceTimeBy(1_001)
        assertEquals(2, countdown.remainingSeconds.value)
        advanceTimeBy(1_001)
        assertEquals(1, countdown.remainingSeconds.value)
        advanceTimeBy(1_001)
        assertEquals(0, countdown.remainingSeconds.value)
    }

    @Test
    fun `no baja de cero`() = runTest {
        val countdown = ResendCountdown(backgroundScope, totalSeconds = 1)

        countdown.start()
        advanceTimeBy(10_001)

        assertEquals(0, countdown.remainingSeconds.value)
    }

    @Test
    fun `volver a iniciar reinicia la cuenta sin dejar dos relojes corriendo`() = runTest {
        val countdown = ResendCountdown(backgroundScope, totalSeconds = 5)

        countdown.start()
        advanceTimeBy(3_001)
        assertEquals(2, countdown.remainingSeconds.value)

        countdown.start()
        assertEquals(5, countdown.remainingSeconds.value)
        advanceTimeBy(1_001)
        // Con dos relojes vivos bajaría de a dos.
        assertEquals(4, countdown.remainingSeconds.value)
    }

    @Test
    fun `reset detiene la cuenta y la deja en cero`() = runTest {
        val countdown = ResendCountdown(backgroundScope, totalSeconds = 5)
        countdown.start()
        advanceTimeBy(1_001)

        countdown.reset()
        advanceTimeBy(5_001)

        assertEquals(0, countdown.remainingSeconds.value)
    }
}
