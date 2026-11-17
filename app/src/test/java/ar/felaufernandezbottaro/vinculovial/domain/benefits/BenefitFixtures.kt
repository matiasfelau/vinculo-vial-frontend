package ar.felaufernandezbottaro.vinculovial.domain.benefits

import java.time.Instant

/** Una promoción de prueba; cada test cambia solo lo que le importa. */
fun benefit(
    id: String = "benefit-1",
    partnerName: String = "Estación de prueba",
    title: String = "20 % en combustible",
    description: String = "Un descuento en tu próxima carga.",
    terms: String = "Válido una vez por persona.",
    openUrl: String = "https://partner.example/app",
    endsAt: Instant = Instant.parse("2030-11-30T23:59:00-03:00"),
    code: String? = null,
) = Benefit(id, partnerName, title, description, terms, openUrl, endsAt, code)

/**
 * Repositorio falso: devuelve [benefits] (o [GetBenefitsResult.Failed] si [listFails]), los bytes
 * de [banners] y el resultado de [claimResult], y registra cada llamada.
 */
class FakeBenefitsRepository(
    var benefits: List<Benefit> = emptyList(),
    var listFails: Boolean = false,
    var claimResult: ClaimResult = ClaimResult.Success("CODIGO-1234"),
) : BenefitsRepository {

    val banners = mutableMapOf<String, ByteArray>()
    val bannerRequests = mutableListOf<String>()
    val claimRequests = mutableListOf<String>()
    var listCalls = 0
        private set

    /** Si no es `null`, cada `claim` espera a que se complete: sirve para probar pedidos en vuelo. */
    var claimGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun getBenefits(): GetBenefitsResult {
        listCalls++
        return if (listFails) GetBenefitsResult.Failed else GetBenefitsResult.Success(benefits)
    }

    override suspend fun getBanner(id: String): ByteArray? {
        bannerRequests += id
        return banners[id]
    }

    override suspend fun claim(id: String): ClaimResult {
        claimRequests += id
        claimGate?.await()
        return claimResult
    }
}
