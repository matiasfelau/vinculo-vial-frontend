package ar.felaufernandezbottaro.vinculovial.domain.inbox

/**
 * Una página del buzón. [nextCursor] es opaco: la app lo reenvía tal cual para pedir la
 * siguiente página, sin interpretarlo ni construirlo. `null` significa que no hay más historial.
 */
data class InboxPage(
    val items: List<AlertItem>,
    val nextCursor: String?,
)
