package ar.felaufernandezbottaro.vinculovial.domain.inbox

interface InboxRepository {

    /**
     * [cursor] es el valor opaco devuelto por la página anterior; ausente en la primera
     * carga. El `limit` nunca lo decide la app: siempre se usa el default del servidor.
     */
    suspend fun getInbox(cursor: String? = null): GetInboxResult
}
