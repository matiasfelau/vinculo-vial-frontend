package ar.felaufernandezbottaro.vinculovial.domain.benefits

sealed interface GetBenefitsResult {
    data class Success(val benefits: List<Benefit>) : GetBenefitsResult

    /** Cualquier falla: para la UI es lo mismo, el carrusel simplemente no se muestra. */
    data object Failed : GetBenefitsResult
}

sealed interface ClaimResult {

    /** El código en claro. Es un secreto: [toString] no lo imprime. */
    data class Success(val code: String) : ClaimResult {
        override fun toString(): String = "Success(code=***)"
    }

    data class Failed(val error: BenefitError) : ClaimResult
}

/** Los errores de `POST /benefits/{id}/claim`, mapeados por el `code` del backend. */
sealed interface BenefitError {

    /** 409 `NO_CODES_LEFT`: el pool de la campaña se agotó. */
    data object NoCodesLeft : BenefitError

    /** 404 `BENEFIT_NOT_FOUND`: la promoción no existe o ya no está vigente. */
    data object NotFound : BenefitError

    /** 429: límite de Kong alcanzado. */
    data object RateLimited : BenefitError

    /** 502/503/504 o circuito abierto: transitorio. */
    data object Unavailable : BenefitError

    /** Sin conexión, timeout o servidor inalcanzable. */
    data object Network : BenefitError

    /** Cualquier otra cosa, incluido un 401 que TokenAuthenticator no pudo resolver. */
    data object Unexpected : BenefitError
}
