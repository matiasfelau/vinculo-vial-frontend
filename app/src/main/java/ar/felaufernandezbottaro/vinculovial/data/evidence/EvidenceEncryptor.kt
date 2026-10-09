package ar.felaufernandezbottaro.vinculovial.data.evidence

import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.toCryptogramBytes
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureImageBuffer
import ar.felaufernandezbottaro.vinculovial.di.IaHpkePublicKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra las fotos de evidencia para `vinculo-vial-ia`: HPKE contra la clave de IA (nunca la de
 * Identity ni la de Vehicle) con el `attempt_id` como `info`. Alert transporta el resultado sin
 * poder leerlo.
 *
 * No libera el buffer en claro: la app lo conserva hasta el envío exitoso, la cancelación o
 * `onCleared`, porque si el intento vence hay que volver a cifrar con el `attempt_id` nuevo.
 */
@Singleton
class EvidenceEncryptor @Inject constructor(
    private val encryptor: HpkeEncryptor,
    @IaHpkePublicKey private val iaPublicKey: String,
) {

    /** `enc‖ct` en bytes crudos, sin base64: es lo que viaja como parte `application/octet-stream`. */
    fun encrypt(jpeg: ByteArray, attemptId: String): ByteArray {
        val contextInfo = attemptId.toByteArray(Charsets.UTF_8)
        return encryptor.encrypt(jpeg, iaPublicKey, contextInfo).toCryptogramBytes()
    }

    fun encrypt(photo: SecureImageBuffer, attemptId: String): ByteArray =
        photo.read { jpeg -> encrypt(jpeg, attemptId) }
}
