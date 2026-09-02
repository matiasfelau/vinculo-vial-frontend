package ar.felaufernandezbottaro.vinculovial.domain.inbox

import java.time.Instant

/**
 * Ítem del buzón tal como lo expone el contrato Zero-PII de `/alerts/inbox`: sólo estos tres
 * campos. Ni el emisor, ni el vehículo, ni ningún otro identificador viajan hasta acá -- el
 * backend los filtra antes de responder, no la app.
 */
data class AlertItem(
    val id: String,
    val alertType: String,
    val createdAt: Instant,
)
