package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CameraFrame
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Sesión de escaneo inerte: el ViewModel sólo la expone hacia la pantalla de escaneo. */
class FakeQrCedulaScanner : QrCedulaScanner {

    private val _state = MutableStateFlow(CedulaScanState())
    override val state: StateFlow<CedulaScanState> = _state

    var starts = 0
    var closes = 0

    override fun start() {
        starts++
        _state.value = CedulaScanState()
    }

    override suspend fun submitFrame(frame: CameraFrame) = Unit

    override fun close() {
        closes++
    }
}
