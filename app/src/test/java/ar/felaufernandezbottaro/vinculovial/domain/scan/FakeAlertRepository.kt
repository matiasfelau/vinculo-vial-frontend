package ar.felaufernandezbottaro.vinculovial.domain.scan

/** Repositorio falso: registra los pares (alertType, patEnc) recibidos y devuelve lo programado. */
class FakeAlertRepository(
    var result: SendAlertResult = SendAlertResult.Sent,
) : AlertRepository {

    val received = mutableListOf<Pair<AlertType, String>>()

    override suspend fun sendAlert(alertType: AlertType, patEnc: String): SendAlertResult {
        received += alertType to patEnc
        return result
    }
}
