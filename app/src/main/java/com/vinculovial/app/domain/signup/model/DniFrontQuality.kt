package com.vinculovial.app.domain.signup.model

/**
 * Veredicto de la revisión rápida que corre sobre una foto del frente del DNI recién
 * disparada, antes de aceptarla.
 *
 * Existe como enum y no como `Boolean` por una razón concreta de UX: la pantalla necesita
 * decirle al usuario **qué** corregir. Un booleano obligaba a un único mensaje genérico
 * ("no pudimos leer el DNI, asegurate de que esté nítido") que se mostraba por igual cuando
 * no se encontraba el código de barras y cuando no se veía la foto del titular — dos
 * problemas con acciones correctivas opuestas, y ninguno de los dos necesariamente
 * relacionado con la nitidez.
 */
enum class DniFrontQuality {

    /** Código de barras leído y foto del titular localizada: la captura sirve. */
    READABLE,

    /** No se encontró un PDF417 decodificable, o el que hay no es de un DNI argentino. */
    BARCODE_UNREADABLE,

    /** El código se leyó, pero no se detectó la foto impresa del titular. */
    PHOTO_NOT_FOUND,
    ;

    val isReadable: Boolean get() = this == READABLE
}
