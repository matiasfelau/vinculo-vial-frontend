package com.vinculovial.app.data.edge.document

/**
 * Datos del titular tal como vienen en el código de barras. Sólo el número sale de esta
 * capa; el resto se usa para validar que el código realmente sea un DNI.
 */
data class ArgentineDni(
    val number: String,
    val surname: String,
    val givenNames: String,
    val sex: String,
) {
    /** Nunca renderizar los datos del titular. */
    override fun toString(): String = "ArgentineDni(***)"
}
