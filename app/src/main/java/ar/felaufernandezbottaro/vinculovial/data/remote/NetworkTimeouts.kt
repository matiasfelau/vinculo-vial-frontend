package ar.felaufernandezbottaro.vinculovial.data.remote

/**
 * Timeouts de las llamadas HTTP salientes, en milisegundos.
 *
 * Es una clase y no una lectura directa de `BuildConfig` dentro de `NetworkModule` para que
 * `NetworkModuleTest` pueda construir los clientes con valores centinela distintos de los
 * defaults de OkHttp (10 s de connect/read/write, y 0 = sin límite para `callTimeout`). Con
 * `BuildConfig` real, un build configurado con 10000 coincidía con el default y el test pasaba
 * aunque se borraran las líneas de timeout del módulo.
 */
data class NetworkTimeouts(
    val connectMs: Long,
    val readMs: Long,
    val writeMs: Long,
    val attestationMs: Long,
) {
    /**
     * Presupuesto total de una llamada: `AttestationInterceptor` ejecuta el Task de Play
     * Integrity *antes* de abrir el socket, así que el peor caso real es attestation +
     * connect + read, no sólo esto último. Se deriva, no se declara aparte: la invariante
     * `callTimeout > attestation + connect + read` no puede desalinearse si alguien cambia uno
     * de los timeouts de base sin acordarse de este.
     *
     * (`write` queda fuera a propósito: los cuerpos de esta app son JSON chico -- un
     * criptograma HPKE, nunca un archivo -- su timeout nunca es el cuello de botella real.)
     */
    val callMs: Long get() = attestationMs + connectMs + readMs + CALL_MARGIN_MS

    companion object {
        /** Para que la desigualdad sea estricta incluso si los timeouts de base quedan iguales. */
        const val CALL_MARGIN_MS = 1_000L
    }
}
