package ar.felaufernandezbottaro.vinculovial.data.remote

/**
 * 502/503/504: el Gateway o una dependencia suya no está disponible -- transitorio,
 * reintentable sin que el usuario cambie nada (ver el contrato de resiliencia compartido
 * entre los 6 repos, documentado en CLAUDE.md).
 *
 * Clasifica por status, nunca por el cuerpo de la respuesta: el 504 nativo de Kong (ante
 * timeout de un upstream interno) no trae el cuerpo del contrato, así que cualquier
 * clasificación que dependiera de parsear ese cuerpo fallaría justo en ese caso. Una única
 * fuente de verdad acá, en vez de repetir el set de códigos en cada repositorio, para que
 * los ocho lugares que lo usan no puedan desalinearse entre sí.
 */
fun Int.isTransientUpstreamFailure(): Boolean = this == 502 || this == 503 || this == 504
