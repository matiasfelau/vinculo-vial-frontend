package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidenceStep

/** En qué momento está la captura guiada. */
enum class EvidencePhase {
    /** Todavía no se eligió el tipo de alerta. */
    IDLE,

    /**
     * Ya se eligió el mensaje y ViVIAna explicó qué fotos hacen falta, pero la cámara todavía no
     * se abrió: el usuario lee y recién cuando quiere toca "Tomar fotos". Es también el estado al
     * que se vuelve si se sale de la cámara sin terminar.
     */
    READY,

    /** Esperando la foto del paso actual: cámara en vivo. */
    CAPTURING,

    /** Reescalando, re-codificando y guardando la foto recién sacada. */
    PROCESSING,

    /** Vista previa de la foto del paso actual, con repetir / continuar. */
    REVIEWING,

    /** Todos los pasos tienen sus fotos. */
    COMPLETE,
}

/** Por qué no se pudo usar una foto: el paso queda en [EvidencePhase.CAPTURING] para repetirla. */
enum class EvidenceError {
    /** La cámara no entregó la foto. */
    CAPTURE_FAILED,

    /** La imagen no se pudo decodificar. */
    UNREADABLE,

    /** Ni a la calidad mínima entra en el tamaño máximo. */
    TOO_LARGE,
}

/**
 * Estado de la captura guiada de evidencia. Las fotos mismas no están acá: viven en
 * `EvidenceBuffers`, dentro del `ViewModel`. [reviewVersion] cambia cada vez que se guarda una
 * foto, para que la vista previa se vuelva a decodificar.
 */
data class EvidenceCaptureState(
    val steps: List<EvidenceStep> = emptyList(),
    val stepIndex: Int = 0,
    val photoIndex: Int = 0,
    val phase: EvidencePhase = EvidencePhase.IDLE,
    val error: EvidenceError? = null,
    val reviewVersion: Int = 0,
) {
    val currentStep: EvidenceStep? get() = steps.getOrNull(stepIndex)

    val isLastStep: Boolean get() = stepIndex == steps.lastIndex

    /** `true` si el paso actual pide otra foto más (hoy ningún paso pide más de una). */
    val canAddAnother: Boolean get() = currentStep?.let { photoIndex + 1 < it.maxPhotos } == true

    /**
     * Cuántas fotos se sacaron hasta ahora. Los pasos anteriores al actual tienen una sola foto
     * (ningún paso pide más de una); el actual, tantas como índices recorrió.
     * Con la captura [EvidencePhase.COMPLETE] es el total que se adjunta.
     */
    fun photoCount(): Int = if (steps.isEmpty()) 0 else stepIndex + photoIndex + 1
}
