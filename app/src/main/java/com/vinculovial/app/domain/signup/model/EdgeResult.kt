package com.vinculovial.app.domain.signup.model

/**
 * Resultado del onboarding biométrico, que corre entero en el dispositivo.
 *
 * Es lo único que sobrevive a las imágenes: los bytes de las tres capturas se
 * sobreescriben apenas se produce este objeto.
 */
sealed interface EdgeResult {

    /**
     * Las tres verificaciones dieron positivo.
     *
     * @param dni número leído del PDF417 del reverso, no tipeado por el usuario.
     * @param faceSimilarity similitud coseno entre la foto del DNI y la selfie. Se
     * conserva sólo para telemetría local; **no** viaja al backend.
     */
    data class Verified(
        val dni: String,
        val faceSimilarity: Float,
    ) : EdgeResult

    data class Rejected(val reason: Reason) : EdgeResult

    enum class Reason {
        /** No se encontró un PDF417 legible en el reverso del documento. */
        BARCODE_NOT_FOUND,

        /** Se leyó el código pero su contenido no es un DNI argentino válido. */
        BARCODE_NOT_A_DNI,

        /** No se detectó un rostro en el frente del documento. */
        NO_FACE_IN_DOCUMENT,

        /** No se detectó un rostro en la selfie. */
        NO_FACE_IN_SELFIE,

        /** La prueba de vida no se completó. */
        LIVENESS_FAILED,

        /** Hay dos rostros, pero no son de la misma persona. */
        FACE_MISMATCH,

        /** Falta el modelo de reconocimiento o el motor no pudo inicializarse. */
        BIOMETRICS_UNAVAILABLE,

        /** Falta alguna de las capturas. */
        INCOMPLETE_CAPTURE,
    }
}
