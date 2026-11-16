package ar.felaufernandezbottaro.vinculovial.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.GetVehicleCountUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.LinkVehicleUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.UnlinkAllVehiclesUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.UnlinkVehicleUseCase
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleError
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.VehicleResult
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.PlateRules
import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ViewModel de "Mis vehículos" (US-14): cuenta, vincula y desvincula. Sólo habla con `domain`:
 * nunca ve códigos HTTP ni excepciones de red.
 *
 * La app nunca muestra qué patentes tiene vinculadas el usuario, sólo cuántas. Para desvincular
 * una hay que volver a escribirla, o usar "Desvincular todos". La patente que se escribe vive sólo
 * en el estado del modal abierto, y se descarta al cerrarlo.
 */
@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val getCount: GetVehicleCountUseCase,
    private val link: LinkVehicleUseCase,
    private val unlink: UnlinkVehicleUseCase,
    private val unlinkAll: UnlinkAllVehiclesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehiclesUiState())
    val uiState: StateFlow<VehiclesUiState> = _uiState.asStateFlow()

    /** Barrera sincrónica contra un doble toque en un botón que llama a la red. */
    private val inFlight = AtomicBoolean(false)

    init {
        load()
    }

    /** Trae cuántos vehículos hay vinculados. También es el "Reintentar" de una carga fallida. */
    fun load() {
        _uiState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = getCount()) {
                is VehicleResult.Success ->
                    _uiState.update { it.copy(count = result.value, loading = false, loadError = null) }

                is VehicleResult.Failed ->
                    _uiState.update { it.copy(loading = false, loadError = result.error) }
            }
        }
    }

    // --- Abrir y cerrar los modales ---------------------------------------------------------

    /** "Vincular vehículo": sólo si todavía hay lugar (el botón además queda deshabilitado). */
    fun onLinkClicked() {
        if (!_uiState.value.canLink) return
        _uiState.update { it.copy(dialog = VehicleDialog.Plate(PlateAction.LINK)) }
    }

    fun onUnlinkClicked() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(dialog = VehicleDialog.Plate(PlateAction.UNLINK)) }
    }

    fun onUnlinkAllClicked() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(dialog = VehicleDialog.UnlinkAll()) }
    }

    fun onDialogDismissed() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(dialog = VehicleDialog.None) }
    }

    // --- Modal de patente --------------------------------------------------------------------

    /** Mayúsculas, sin espacios ni guiones, hasta 7 caracteres. Limpia el error anterior. */
    fun onPlateChange(raw: String) = updatePlateDialog { it.copy(input = PlateRules.normalize(raw), problem = null, error = null) }

    /** Valida el formato y pasa a la confirmación. Una patente inválida no sale del dispositivo. */
    fun onPlateContinue() = updatePlateDialog { dialog ->
        when {
            dialog.input.isEmpty() -> dialog.copy(problem = PlateProblem.REQUIRED)
            !PlateRules.isValid(dialog.input) -> dialog.copy(problem = PlateProblem.FORMAT)
            else -> dialog.copy(step = PlateStep.CONFIRM, problem = null, error = null)
        }
    }

    /** Vuelve de la confirmación a corregir la patente. */
    fun onPlateBack() = updatePlateDialog { it.copy(step = PlateStep.ENTRY) }

    /** Confirma: manda el alta o la baja según el modal. */
    fun onPlateConfirmed() {
        val dialog = _uiState.value.dialog as? VehicleDialog.Plate ?: return
        if (dialog.step != PlateStep.CONFIRM) return
        if (!inFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(busy = true) }
                val result = when (dialog.action) {
                    PlateAction.LINK -> link(dialog.input)
                    PlateAction.UNLINK -> unlink(dialog.input)
                }
                when (result) {
                    is VehicleResult.Success -> _uiState.update {
                        it.copy(count = result.value, dialog = VehicleDialog.None, busy = false)
                    }

                    // Un rechazo vuelve a la patente, para corregirla, con el motivo a la vista. Con
                    // el modal ya cerrado por el usuario no hay nada que mostrar.
                    is VehicleResult.Failed -> _uiState.update { state ->
                        val current = state.dialog as? VehicleDialog.Plate
                        state.copy(
                            busy = false,
                            dialog = current?.copy(step = PlateStep.ENTRY, error = result.error) ?: state.dialog,
                        )
                    }
                }
                // El servidor dice que ya no hay lugar: el conteo que se ve estaba desactualizado.
                if (result is VehicleResult.Failed && result.error == VehicleError.LimitReached) refreshCount()
            } finally {
                inFlight.set(false)
            }
        }
    }

    // --- Desvincular todos -------------------------------------------------------------------

    fun onUnlinkAllConfirmed() {
        if (_uiState.value.dialog !is VehicleDialog.UnlinkAll) return
        if (!inFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(busy = true) }
                when (val result = unlinkAll()) {
                    is VehicleResult.Success -> _uiState.update { state ->
                        // El DELETE no trae conteo: queda en cero con el mismo máximo.
                        state.copy(
                            count = VehicleCount(count = 0, max = state.count?.max ?: 0),
                            dialog = VehicleDialog.None,
                            busy = false,
                        )
                    }

                    is VehicleResult.Failed -> _uiState.update { state ->
                        state.copy(busy = false, dialog = VehicleDialog.UnlinkAll(error = result.error))
                    }
                }
            } finally {
                inFlight.set(false)
            }
        }
    }

    /** Actualiza el conteo sin mostrar la carga ni tocar el modal abierto. */
    private suspend fun refreshCount() {
        val result = getCount()
        if (result is VehicleResult.Success) _uiState.update { it.copy(count = result.value) }
    }

    private fun updatePlateDialog(transform: (VehicleDialog.Plate) -> VehicleDialog.Plate) {
        _uiState.update { state ->
            val dialog = state.dialog as? VehicleDialog.Plate ?: return@update state
            state.copy(dialog = transform(dialog))
        }
    }
}
