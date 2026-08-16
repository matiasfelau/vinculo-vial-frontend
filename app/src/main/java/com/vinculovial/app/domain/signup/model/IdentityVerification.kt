package com.vinculovial.app.domain.signup.model

/**
 * Identificación del documento tal como la necesita un verificador de identidad.
 *
 * Es deliberadamente más angosta que lo que trae el código de barras: el apellido y los
 * nombres del titular no se propagan más allá de la capa que los lee, porque ningún
 * verificador —ni el local ni el del Estado— los necesita para responder "¿esta cara es la
 * de este documento?".
 */
data class DocumentIdentity(
    /** Número de documento leído del código, no tipeado por el usuario. */
    val number: String,

    /** `M` o `F`, tal como viene en el documento. */
    val sex: String,

    /** Número de trámite. `null` si la disposición leída no lo trae. */
    val procedureNumber: String? = null,
) {
    /** Nunca renderizar los datos del titular. */
    override fun toString(): String = "DocumentIdentity(***)"
}

/**
 * Todo lo que un [com.vinculovial.app.domain.signup.IdentityVerificationProvider] necesita
 * para expedirse.
 *
 * No es `data class` a propósito: los JPEG son datos biométricos y `equals`/`hashCode`
 * generados sobre `ByteArray` comparan por referencia, lo que da una falsa sensación de
 * igualdad estructural; y un `toString` generado los volcaría a un log.
 *
 * El request **presta** los bytes, no los cede: quien lo construye sigue siendo responsable
 * de borrarlos, y ninguna implementación debe conservar la referencia después de responder.
 */
class IdentityVerificationRequest(
    val document: DocumentIdentity,
    /** JPEG del frente del documento, ya vertical. Contiene la foto impresa del titular. */
    val documentFrontJpeg: ByteArray,
    /** JPEG de la selfie, tomada después de superar la prueba de vida. */
    val selfieJpeg: ByteArray,
) {
    override fun toString(): String = "IdentityVerificationRequest(***)"
}

/**
 * Veredicto de un verificador de identidad.
 *
 * No incluye el número de documento ni ningún dato del titular: eso ya lo tiene quien armó
 * el request. Acá sólo viaja la decisión.
 */
sealed interface IdentityVerificationOutcome {

    /**
     * La cara de la selfie corresponde a la del documento.
     *
     * @param confidence qué tan holgada fue la decisión, en `0f..1f`. Es telemetría local
     * para calibrar el umbral; **no** viaja al backend.
     */
    data class Match(val confidence: Float) : IdentityVerificationOutcome

    /** Se pudo comparar y las caras no son de la misma persona. */
    data class NoMatch(val confidence: Float) : IdentityVerificationOutcome

    /**
     * No se pudo emitir un veredicto. Nunca equivale a [Match]: si no se pudo comparar, el
     * registro no avanza.
     */
    data class Unavailable(val reason: Reason) : IdentityVerificationOutcome {

        enum class Reason {
            /** No se encontró una cara utilizable en la foto impresa del documento. */
            NO_FACE_IN_DOCUMENT,

            /** No se encontró una cara utilizable en la selfie. */
            NO_FACE_IN_SELFIE,

            /** El motor de comparación no está disponible (modelo ausente, ilegible, etc.). */
            ENGINE_UNAVAILABLE,
        }
    }
}
