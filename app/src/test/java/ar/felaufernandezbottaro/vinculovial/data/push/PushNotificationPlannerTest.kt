package ar.felaufernandezbottaro.vinculovial.data.push

import ar.felaufernandezbottaro.vinculovial.presentation.push.PUSH_TYPE_INBOX_UPDATED
import ar.felaufernandezbottaro.vinculovial.presentation.push.PUSH_TYPE_SENT_UPDATED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Qué notificación se publica para cada clase de mensaje de FCM. */
class PushNotificationPlannerTest {

    private fun plan(data: Map<String, String>, title: String? = null, body: String? = null) =
        PushNotificationPlanner.plan(data, title, body)

    @Test
    fun `un mensaje sin tipo ni alert_type no publica nada`() {
        assertNull(plan(mapOf("otro" to "dato")))
        assertNull(plan(emptyMap()))
    }

    @Test
    fun `una alerta nueva usa el nombre del incidente y el cuerpo generico`() {
        val plan = plan(mapOf(EXTRA_ALERT_TYPE to "LUCES_ENCENDIDAS", EXTRA_ALERT_ID to "a-1"))!!

        assertEquals("Luces encendidas", plan.title)
        assertEquals(PushNotificationPlanner.GENERIC_BODY, plan.body)
        assertEquals("a-1".hashCode(), plan.notificationId)
        assertNull("la alerta nueva no reenvia type", plan.pushTypeExtra)
        assertEquals("LUCES_ENCENDIDAS", plan.alertType)
        assertEquals("a-1", plan.alertId)
    }

    @Test
    fun `una alerta nueva sin id usa el id fijo`() {
        val plan = plan(mapOf(EXTRA_ALERT_TYPE to "CRASHED"))!!

        assertEquals(PushNotificationPlanner.NOTIFICATION_ID, plan.notificationId)
        assertNull(plan.alertId)
    }

    @Test
    fun `un alert_id en blanco se trata como ausente`() {
        val plan = plan(mapOf(EXTRA_ALERT_TYPE to "CRASHED", EXTRA_ALERT_ID to "  "))!!

        assertNull(plan.alertId)
        assertEquals(PushNotificationPlanner.NOTIFICATION_ID, plan.notificationId)
    }

    @Test
    fun `un tipo de incidente desconocido se muestra como incidente reportado`() {
        val plan = plan(mapOf(EXTRA_ALERT_TYPE to "ALGO_NUEVO"))!!

        assertEquals("Incidente reportado", plan.title)
    }

    @Test
    fun `las novedades al emisor usan el bloque notification si viene`() {
        val plan = plan(
            mapOf(EXTRA_PUSH_TYPE to PUSH_TYPE_SENT_UPDATED, EXTRA_ALERT_ID to "a-2"),
            title = "Titulo del servidor",
            body = "Cuerpo del servidor",
        )!!

        assertEquals("Titulo del servidor", plan.title)
        assertEquals("Cuerpo del servidor", plan.body)
        assertEquals(PUSH_TYPE_SENT_UPDATED, plan.pushTypeExtra)
        assertEquals("a-2", plan.alertId)
    }

    @Test
    fun `las novedades al emisor sin bloque notification usan el texto por defecto`() {
        val plan = plan(mapOf(EXTRA_PUSH_TYPE to PUSH_TYPE_SENT_UPDATED))!!

        assertEquals(PushNotificationPlanner.SENT_UPDATE_TITLE, plan.title)
        assertEquals(PushNotificationPlanner.SENT_UPDATE_BODY, plan.body)
        assertEquals(PushNotificationPlanner.NOTIFICATION_ID, plan.notificationId)
    }

    @Test
    fun `el aviso de alertas sin leer tiene su propio id y abre Recibidos`() {
        val plan = plan(mapOf(EXTRA_PUSH_TYPE to PUSH_TYPE_INBOX_UPDATED))!!

        assertEquals(PushNotificationPlanner.INBOX_UPDATE_TITLE, plan.title)
        assertEquals(PushNotificationPlanner.INBOX_UPDATE_BODY, plan.body)
        assertEquals(PushNotificationPlanner.INBOX_NOTIFICATION_ID, plan.notificationId)
        assertNotEquals(PushNotificationPlanner.NOTIFICATION_ID, plan.notificationId)
        assertEquals(PUSH_TYPE_INBOX_UPDATED, plan.pushTypeExtra)
    }

    @Test
    fun `el aviso de alertas sin leer usa el bloque notification si viene`() {
        val plan = plan(mapOf(EXTRA_PUSH_TYPE to PUSH_TYPE_INBOX_UPDATED), title = "T", body = "B")!!

        assertEquals("T", plan.title)
        assertEquals("B", plan.body)
    }

    @Test
    fun `alertas distintas conviven y las de la misma se reemplazan`() {
        val a = plan(mapOf(EXTRA_ALERT_TYPE to "CRASHED", EXTRA_ALERT_ID to "a"))!!
        val a2 = plan(mapOf(EXTRA_ALERT_TYPE to "CRASHED", EXTRA_ALERT_ID to "a"))!!
        val b = plan(mapOf(EXTRA_ALERT_TYPE to "CRASHED", EXTRA_ALERT_ID to "b"))!!

        assertEquals(a.notificationId, a2.notificationId)
        assertNotEquals(a.notificationId, b.notificationId)
    }
}
