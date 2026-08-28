package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.CheckVehicleError
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError

data class ScanUiState(val status: ScanStatus = ScanStatus.Scanning) {
    val isBusy: Boolean get() = status is ScanStatus.Checking || status is ScanStatus.SendingAlert
}

sealed interface ScanStatus {

    /** Buscando una patente en el stream de la cámara. */
    data object Scanning : ScanStatus

    /**
     * Patente reconocida y validada por `PlateRegexFilter`, a la espera de que el humano
     * confirme que el OCR la leyó bien -- validación humana en el bucle, no un paso
     * cosmético: el OCR tiene confusiones insalvables entre ciertos caracteres (ej. 'Q'/'O')
     * en condiciones reales, y el check contra el backend no arranca hasta esta confirmación.
     */
    data class Detected(val patente: String) : ScanStatus

    /** El humano marcó que [ScanStatus.Detected] leyó mal: ofrece reintentar o tipear a mano. */
    data object Rejected : ScanStatus

    /**
     * Fallback manual: [error] es el motivo del último intento inválido, si lo hubo -- el
     * diálogo se mantiene abierto para reintentar en vez de volver a Scanning.
     */
    data class ManualEntry(val error: String? = null) : ScanStatus

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
