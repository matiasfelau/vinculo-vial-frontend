package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import java.time.Instant

data class ScanUiState(
    val status: ScanStatus = ScanStatus.Scanning,
    /** El tipo elegido en `ReportChatScreen`, para renderizarlo como la burbuja del usuario. */
    val selectedAlertType: AlertType? = null,
    /** La captura guiada de evidencia del tipo elegido. */
    val evidence: EvidenceCaptureState = EvidenceCaptureState(),
) {
    val isBusy: Boolean
        get() = status is ScanStatus.Checking ||
            status is ScanStatus.SendingAlert ||
            evidence.phase == EvidencePhase.PROCESSING
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

    /** Cifrando la patente y abriendo el intento en `/alerts/attempts`. */
    data object Checking : ScanStatus

    /**
     * 409 `ALERT_DAILY_LIMIT` en el intento (US-16): el emisor ya alertó esta patente en las
     * últimas 24 h. Se abre el chat para que ViVIAna lo explique, sin catálogo ni captura de fotos.
     * [availableAt] es cuándo se libera el cupo.
     */
    data class DailyLimitReached(val availableAt: Instant) : ScanStatus

    /** 201: se abre el chat con el catálogo cerrado de incidentes. */
    data object AwaitingAlertType : ScanStatus

    data object SendingAlert : ScanStatus

    /** 202: confirmación genérica -- nunca cuántos dueños ni si llegó. */
    data object AlertSent : ScanStatus

    data class CheckFailed(val failure: StartAlertAttemptResult.Failure) : ScanStatus

    data class AlertFailed(val error: SendAlertError) : ScanStatus
}
