package com.vinculovial.app.presentation.camera

import com.vinculovial.app.domain.signup.SignUpError
import com.vinculovial.app.domain.signup.model.DniFrontQuality
import com.vinculovial.app.presentation.signup.presentation
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guarda de regresión sobre un bug de UX que dejó la app inusable en el dispositivo.
 *
 * Los mensajes pedían acercar el DNI hasta llenar el recuadro. Nada validaba el encuadre —el
 * texto era la única fuente de esa exigencia—, pero el usuario obediente se acercaba hasta
 * cruzar la distancia focal mínima del lente, sacaba una foto borrosa, la app la rechazaba y
 * el mensaje volvía a mandarlo a acercarse. Un círculo cerrado, causado por la copy.
 *
 * La instrucción correcta es la opuesta: sostener el documento a una distancia donde la
 * cámara enfoque. Por eso estos términos no pueden volver a aparecer en un mensaje de captura.
 */
class DocumentCaptureMessagesTest {

    /** "acerc" cubre acercá/acercate/acercar; "llen" cubre llená/llene/llenar. */
    private val terminosProhibidos = listOf("acerc", "llen", "más cerca", "mas cerca")

    @Test
    fun `ningun motivo de rechazo manda a acercar el documento`() {
        DniFrontQuality.entries.filterNot { it.isReadable }.forEach { quality ->
            val mensaje = quality.failureMessage().lowercase()

            terminosProhibidos.forEach { termino ->
                assertTrue("$quality usa '$termino': $mensaje", !mensaje.contains(termino))
            }
        }
    }

    @Test
    fun `cada motivo de rechazo dice algo`() {
        DniFrontQuality.entries.filterNot { it.isReadable }.forEach { quality ->
            assertTrue("$quality sin mensaje", quality.failureMessage().isNotBlank())
        }
    }

    @Test
    fun `los errores de captura del flujo tampoco mandan a acercarse`() {
        listOf(
            SignUpError.DocumentUnreadable,
            SignUpError.DocumentNotRecognized,
        ).forEach { error ->
            val mensaje = error.presentation().message.lowercase()

            terminosProhibidos.forEach { termino ->
                assertTrue("$error usa '$termino': $mensaje", !mensaje.contains(termino))
            }
        }
    }

    @Test
    fun `no se pide el reverso, que no existe en el flujo`() {
        // El DNI tarjeta trae el PDF417 en el frente y el paso de reverso se retiró; pedirlo
        // mandaba al usuario a fotografiar una cara del documento que nadie lee.
        val mensaje = SignUpError.DocumentUnreadable.presentation().message.lowercase()

        assertTrue("el mensaje pide el reverso: $mensaje", !mensaje.contains("reverso"))
    }
}
