package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError

data class ScanUiState(val status: ScanStatus = ScanStatus.Scanning) {
    val isBusy: Boolean get() = status is ScanStatus.Checking || status is ScanStatus.SendingAlert
}

sealed interface ScanStatus {

    /** Buscando una patente en el stream de la cámara. */
    data object Scanning : ScanStatus

    /** Patente reconocida: pausa breve (feedback háptico) antes de arrancar el check. */
    data class Detected(val patente: String) : ScanStatus

    /** Cifrando y consultando `/vehicles/check`. */
    data object Checking : ScanStatus

    /** 404: el vehículo no está en la red. No se ofrece alertar igual. */
    data object NotInNetwork : ScanStatus

    /** 200: se abre el catálogo cerrado de incidentes. */
    data object AwaitingAlertType : ScanStatus

    data object SendingAlert : ScanStatus

    /** 202: confirmación genérica -- nunca cuántos dueños ni si llegó. */
    data object AlertSent : ScanStatus

    data class CheckFailed(val error: CheckVehicleError) : ScanStatus

    data class AlertFailed(val error: SendAlertError) : ScanStatus
}
