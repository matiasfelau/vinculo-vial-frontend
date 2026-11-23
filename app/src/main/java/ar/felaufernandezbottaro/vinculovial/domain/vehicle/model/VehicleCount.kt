package ar.felaufernandezbottaro.vinculovial.domain.vehicle.model

/**
 * Cuántos vehículos tiene vinculados el usuario y cuántos puede tener (`VEHICLE_MAX_PER_USER`).
 *
 * Es todo lo que la app sabe de los vehículos: ninguna respuesta de Vehicle devuelve patentes, así
 * que nadie con acceso a una sesión puede ver qué vehículos tiene esa persona.
 */
data class VehicleCount(
    val count: Int,
    val max: Int,
) {
    /** Ya no se puede vincular otro: el botón queda deshabilitado. */
    val isFull: Boolean get() = count >= max
}
