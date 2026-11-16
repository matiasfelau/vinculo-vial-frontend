package ar.felaufernandezbottaro.vinculovial.domain.benefits

interface BenefitsRepository {

    /** Las promociones vigentes, ordenadas por vencimiento. */
    suspend fun getBenefits(): GetBenefitsResult

    /**
     * Los bytes del banner de [id], o `null` si no se pudo traer: el banner es decoración, su
     * falla nunca es un error para el usuario. Se guarda en memoria por `ETag`.
     */
    suspend fun getBanner(id: String): ByteArray?

    /** Pide el código de [id]. Idempotente: si el usuario ya lo tenía, devuelve el mismo. */
    suspend fun claim(id: String): ClaimResult
}
