package ar.felaufernandezbottaro.vinculovial.domain.inbox

/**
 * Qué alertas del buzón ya abrió el usuario, sólo en este dispositivo: los `alert_id` abiertos
 * nunca se envían al servidor. Una alerta cuyo id no está acá se muestra como no leída.
 */
interface ReadAlertsStore {

    /** Los ids de las alertas abiertas. */
    fun readIds(): Set<String>

    /** Marca [alertId] como abierta. Idempotente. */
    fun markRead(alertId: String)
}
