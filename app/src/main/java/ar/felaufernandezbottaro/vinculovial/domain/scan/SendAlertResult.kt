package ar.felaufernandezbottaro.vinculovial.domain.scan

/**
 * Resultado de `POST /alerts` visto desde el dominio. `presentation` nunca ve un código HTTP ni
 * una excepción cruda: sólo estos casos.
 */
sealed interface SendAlertResult {

    /**
     * 202: la alerta quedó pendiente de validación. La app NO debe mostrar a cuántos dueños
     * llegó ni si llegó -- eso rompería el anonimato de extremo a extremo del contrato.
     */
    data object Sent : SendAlertResult

    data class Failed(val error: SendAlertError) : SendAlertResult
}

sealed interface SendAlertError {

    /** 400 `INVALID_EVIDENCE`: faltan o sobran fotos, o alguna no corresponde al tipo. */
    data object InvalidEvidence : SendAlertError

    /** 400 `ALERT_TYPE_DISABLED`: el tipo todavía no tiene validación. */
    data object AlertTypeDisabled : SendAlertError

    /** 400 `INVALID_ALERT_TYPE`: tipo fuera del catálogo. */
    data object InvalidAlertType : SendAlertError

    /** Cualquier otro 400. */
    data object InvalidData : SendAlertError

    /** 413 (`EVIDENCE_TOO_LARGE`, o el límite de body de Kong): las fotos pesan demasiado. */
    data object EvidenceTooLarge : SendAlertError

    /** 403 `ISSUER_UNDER_REVIEW`: la cuenta del emisor está en revisión y no puede crear alertas. */
    data object UnderReview : SendAlertError

    /** 404 `ATTEMPT_NOT_FOUND`: el intento no existe o es de otro usuario. Hay que reiniciar el flujo. */
    data object AttemptNotFound : SendAlertError

    /**
     * 404 `ATTEMPT_NOT_FOUND` en un reintento tras una falla de red al enviar: el primer envío
     * pudo haber llegado y haber consumido el intento. No se reinicia el flujo en silencio ni se
     * reenvía (podría duplicar la alerta): se avisa que pudo haberse enviado.
     */
    data object PossiblySent : SendAlertError

    /** 410 `ATTEMPT_EXPIRED` que persiste tras renovar el intento una vez. */
    data object AttemptExpired : SendAlertError

    /** 429: límite de Kong alcanzado. No reintentar automático. */
    data object RateLimited : SendAlertError

    /** 502/503/504 o circuito abierto: el Gateway o una dependencia suya no está disponible. Transitorio, reintentable. */
    data object Unavailable : SendAlertError

    /**
     * Sin conexión, timeout o servidor inalcanzable. Si el corte ocurrió después de empezar a
     * subir las fotos, el envío pudo haber llegado: ver [PossiblySent].
     */
    data object Network : SendAlertError

    /** Cualquier otra cosa, incluido 500, un 401 que TokenAuthenticator no pudo resolver y la configuración de build. */
    data object Unexpected : SendAlertError
}
