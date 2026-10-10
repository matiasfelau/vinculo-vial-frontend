package ar.felaufernandezbottaro.vinculovial.domain.inbox

/** Almacén falso de alertas abiertas, en memoria. */
class FakeReadAlertsStore(initial: Set<String> = emptySet()) : ReadAlertsStore {

    private val ids = initial.toMutableSet()

    /** Los ids que se marcaron, en orden: sirve para comprobar que no se repiten llamadas. */
    val marked = mutableListOf<String>()

    override fun readIds(): Set<String> = ids.toSet()

    override fun markRead(alertId: String) {
        marked += alertId
        ids += alertId
    }
}
