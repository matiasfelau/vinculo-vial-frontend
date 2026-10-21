package ar.felaufernandezbottaro.vinculovial.domain.scan

/**
 * Cifra la patente para Vehicle, el único que puede abrirla. La patente en claro vive sólo hasta
 * acá: el resto del flujo maneja el `pat_enc`, que viaja en el `metadata` de `POST /alerts`.
 */
interface PlateEncryptor {

    /** El `pat_enc` en base64, o `null` si no se pudo cifrar (clave mal configurada o falla criptográfica). */
    fun encrypt(patente: String): String?
}
