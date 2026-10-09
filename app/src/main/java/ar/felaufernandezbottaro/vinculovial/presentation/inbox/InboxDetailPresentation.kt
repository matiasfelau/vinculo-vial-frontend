package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem

/** Badge de la lista y del detalle cuando la validación de IA no fue concluyente (YELLOW). */
const val REVIEW_BADGE_TEXT = "No verificada por completo"

/** Lo que se muestra en lugar de la foto cuando el servidor responde 410 (retención vencida). */
const val EVIDENCE_EXPIRED_MESSAGE = "La evidencia ya no está disponible"

/** La alerta no trae fotos redactadas: IA no pudo redactarlas o la retención ya venció. */
const val NO_EVIDENCE_MESSAGE = "Esta alerta no tiene evidencia para mostrar."

const val REPORTED_BUTTON_TEXT = "Reportado"

fun AlertItem.reviewBadgeText(): String? = if (requiresReview) REVIEW_BADGE_TEXT else null

/** El rol del contrato en palabras. Un rol que la app no conoce todavía se muestra como "Foto". */
fun evidenceRoleLabel(role: String): String = when (role) {
    "GENERAL" -> "Vehículo"
    "PLATE" -> "Patente"
    "DETAIL" -> "Daño"
    "CONTEXT" -> "Contexto"
    else -> "Foto"
}
