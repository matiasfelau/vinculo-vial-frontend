package ar.felaufernandezbottaro.vinculovial.data.edge.document

/**
 * Datos del titular tal como vienen en el código de barras.
 *
 * El apellido y los nombres se usan sólo para validar que el código realmente sea un DNI
 * (su forma es lo que distingue una disposición de otra) y no salen de esta capa. Lo que sí
 * cruza hacia el dominio es el trío que identifica el documento —número, sexo y trámite—,
 * porque es lo que pide la API de RENAPER para una verificación contra el padrón.
 */
data class ArgentineDni(
    val number: String,
    val surname: String,
    val givenNames: String,
    val sex: String,
    /**
     * Número de trámite (11 dígitos). Nullable a propósito: no está garantizado en todas las
     * disposiciones en circulación, y la verificación Edge no lo necesita. Quien sí lo
     * necesita es RENAPER, para identificar contra qué registro verificar — por eso viaja
     * en el payload de `/signup` (ver `CLAUDE.md`, pivote RENAPER-Ready).
     */
    val procedureNumber: String? = null,
) {
    /** Nunca renderizar los datos del titular. */
    override fun toString(): String = "ArgentineDni(***)"
}
