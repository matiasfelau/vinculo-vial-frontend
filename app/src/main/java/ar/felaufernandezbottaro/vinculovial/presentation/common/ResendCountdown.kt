package ar.felaufernandezbottaro.vinculovial.presentation.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cuenta regresiva del botón "Reenviar" del código por email (`EMAIL_CODE_RESEND_SECONDS`, el
 * mismo valor que usa Identity para su throttle). [remainingSeconds] llega a cero y ahí el botón
 * se habilita; volver a llamar a [start] reinicia la cuenta.
 *
 * Corre en el scope del ViewModel: si la pantalla se va, la cuenta se cancela sola.
 */
class ResendCountdown(
    private val scope: CoroutineScope,
    private val totalSeconds: Int,
) {

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private var job: Job? = null

    fun start() {
        job?.cancel()
        _remainingSeconds.value = totalSeconds
        job = scope.launch {
            while (_remainingSeconds.value > 0) {
                delay(ONE_SECOND_MS)
                _remainingSeconds.value -= 1
            }
        }
    }

    fun reset() {
        job?.cancel()
        _remainingSeconds.value = 0
    }

    private companion object {
        const val ONE_SECOND_MS = 1_000L
    }
}
