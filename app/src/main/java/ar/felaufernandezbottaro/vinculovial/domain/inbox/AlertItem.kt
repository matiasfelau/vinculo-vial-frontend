package ar.felaufernandezbottaro.vinculovial.domain.inbox

import java.time.Instant

/**
 * Ítem del buzón tal como lo expone el contrato Zero-PII de `/alerts/inbox`. Ni el emisor, ni
 * el vehículo, ni ningún otro identificador viajan hasta acá -- el backend los filtra antes de
 * responder, no la app.
 *
 * @property requiresReview la validación de IA no fue concluyente (YELLOW). Se parsea porque es
 * parte del contrato, pero la app no distingue: todas las alertas piden revisar las fotos.
 * @property evidence las fotos redactadas disponibles; vacío si IA no pudo redactar o si la
 * retención ya venció.
 * @property evidenceExpiresAt hasta cuándo se conserva la evidencia, si el servidor lo informa.
 * @property myFeedback la valoración que dio el usuario a la validación, si dio alguna.
 * @property reported el usuario ya reportó al emisor de esta alerta.
 * @property seen este destinatario ya abrió el chat. Lo fija el servidor (`PUT .../seen`): no hay
 * ningún dato de "leído" guardado en el dispositivo.
 */
data class AlertItem(
    val id: String,
    val alertType: String,
    val createdAt: Instant,
    val requiresReview: Boolean = false,
    val evidence: List<EvidenceRef> = emptyList(),
    val evidenceExpiresAt: Instant? = null,
    val myFeedback: FeedbackRating? = null,
    val reported: Boolean = false,
    val seen: Boolean = false,
)

/**
 * Una foto de evidencia redactada. [role] es el del contrato (`GENERAL`, `PLATE`, `DETAIL`,
 * `CONTEXT`) tal cual lo manda el servidor: es un `String` y no un enum porque el catálogo puede
 * crecer antes de que la app se actualice, y se reenvía sin interpretarlo en el GET de la imagen.
 */
data class EvidenceRef(val role: String, val index: Int)

/** Valoración del dueño a la validación de IA (👍/👎), insumo del reentrenamiento. */
enum class FeedbackRating { UP, DOWN }

/** Motivo del reporte al emisor. */
enum class ReportReason(val label: String) {
    FALSE_ALERT("La alerta no corresponde"),
    SPAM("Spam o acoso"),
}
