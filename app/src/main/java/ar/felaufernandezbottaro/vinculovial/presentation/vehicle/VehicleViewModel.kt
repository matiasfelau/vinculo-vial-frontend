package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.QrCedulaScanner
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ViewModel de la vinculación de vehículo. Sólo habla con `domain`: nunca ve el contenido
 * crudo del QR ni bytes de imagen (eso vive en `data/scanner`, con Zero Retention).
 */
@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val linkVehicle: LinkVehicleUseCase,
    /**
     * La pantalla consume la sesión de escaneo directamente: es estado sobre el stream de
     * cámara, no una operación puntual que el ViewModel pueda envolver -- mismo criterio
     * que `documentScanSession` en `SignUpViewModel`.
     */
    val qrCedulaScanner: QrCedulaScanner,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    /** Barrera sincrónica contra procesar el mismo escaneo dos veces. Ver [onCedulaScanned]. */
    private val linkInFlight = AtomicBoolean(false)

    /** El escaneo en vivo leyó la cédula: cifra y publica el vínculo de inmediato. */
    fun onCedulaScanned(cedula: CedulaData) {
        if (!linkInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            _uiState.update { it.copy(status = VehicleStatus.Linking) }

            _uiState.update {
                when (val result = linkVehicle(cedula)) {
                    is LinkVehicleResult.Linked -> it.copy(status = VehicleStatus.Linked)
                    is LinkVehicleResult.Failed -> it.copy(status = VehicleStatus.Failed(result.error, cedula))
                }
            }

            linkInFlight.set(false)
        }
    }

    /** Cierra el error y deja la pantalla lista para volver a escanear. */
    fun onErrorDismissed() {
        if (_uiState.value.status is VehicleStatus.Failed) {
            _uiState.update { it.copy(status = VehicleStatus.Idle) }
        }
    }
}
