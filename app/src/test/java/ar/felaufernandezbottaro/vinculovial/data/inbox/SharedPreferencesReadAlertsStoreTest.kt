package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPreferencesReadAlertsStoreTest {

    private val preferences = FakeSharedPreferences()

    private fun store() = SharedPreferencesReadAlertsStore(preferences)

    @Test
    fun `sin nada guardado no hay alertas abiertas`() {
        assertEquals(emptySet<String>(), store().readIds())
    }

    @Test
    fun `una alerta marcada como abierta queda abierta`() {
        val store = store()

        store.markRead("alerta-1")

        assertEquals(setOf("alerta-1"), store.readIds())
    }

    @Test
    fun `lo abierto sobrevive a una instancia nueva, o sea al reiniciar la app`() {
        store().markRead("alerta-1")
        store().markRead("alerta-2")

        assertEquals(setOf("alerta-1", "alerta-2"), store().readIds())
    }

    @Test
    fun `marcar dos veces la misma alerta no la duplica`() {
        val store = store()

        store.markRead("alerta-1")
        store.markRead("alerta-1")

        assertEquals(setOf("alerta-1"), store.readIds())
    }

    @Test
    fun `un id vacio o con salto de linea se ignora`() {
        val store = store()

        store.markRead("")
        store.markRead("   ")
        store.markRead("a\nb")

        assertEquals(emptySet<String>(), store.readIds())
    }

    @Test
    fun `conserva a lo sumo MAX_IDS y descarta los mas viejos`() {
        val store = store()
        val total = SharedPreferencesReadAlertsStore.MAX_IDS + 5

        repeat(total) { store.markRead("alerta-$it") }

        val ids = store.readIds()
        assertEquals(SharedPreferencesReadAlertsStore.MAX_IDS, ids.size)
        assertFalse("alerta-0" in ids)
        assertFalse("alerta-4" in ids)
        assertTrue("alerta-5" in ids)
        assertTrue("alerta-${total - 1}" in ids)
    }

    @Test
    fun `lo guardado es solo el alert_id, nada del contenido de la alerta`() {
        store().markRead("3f2b8c1e-0000-4000-8000-000000000001")

        val stored = preferences.all.values.map { it.toString() }

        assertEquals(listOf("3f2b8c1e-0000-4000-8000-000000000001"), stored)
    }
}
