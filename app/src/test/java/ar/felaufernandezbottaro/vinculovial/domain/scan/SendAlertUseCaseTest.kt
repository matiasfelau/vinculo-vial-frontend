package ar.felaufernandezbottaro.vinculovial.domain.scan

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SendAlertUseCaseTest {

    private val patEnc = "pat-enc-ya-generado-por-el-check"

    @Test
    fun `pasa el alertType y el pat_enc al repositorio tal cual`() = runTest {
        val repository = FakeAlertRepository()
        val sendAlert = SendAlertUseCase(repository)

        sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(listOf(AlertType.CRASHED to patEnc), repository.received)
    }

    @Test
    fun `devuelve el resultado del repositorio sin transformarlo`() = runTest {
        val repository = FakeAlertRepository(result = SendAlertResult.Failed(SendAlertError.RateLimited))
        val sendAlert = SendAlertUseCase(repository)

        val result = sendAlert(AlertType.CRASHED, patEnc)

        assertEquals(SendAlertResult.Failed(SendAlertError.RateLimited), result)
    }

    /**
     * El requerimiento central del flujo de dos pasos de US-05: el `pat_enc` que
     * `CheckVehicleUseCase` generó al escanear se reusa tal cual en `/alerts`, sin volver a
     * escanear ni a cifrar. `SendAlertUseCase` no tiene `HpkeEncryptor` entre sus
     * dependencias -- estructuralmente no podría generar un cifrado nuevo aunque quisiera --
     * así que esta prueba encadena ambos casos de uso con el mismo repositorio falso de
     * check y confirma que el `pat_enc` que efectivamente viaja a `/alerts` es exactamente
     * el mismo objeto que devolvió el check, no uno nuevo.
     */
    @Test
    fun `reusa el pat_enc que devolvio el check, no genera uno nuevo`() = runTest {
        val checkRepository = FakeVehicleCheckRepository(result = CheckVehicleResult.Found(patEnc = patEnc))
        val alertRepository = FakeAlertRepository()
        val checkVehicle = CheckVehicleUseCase(checkRepository)
        val sendAlert = SendAlertUseCase(alertRepository)

        val checkResult = checkVehicle("GUQ856") as CheckVehicleResult.Found
        sendAlert(AlertType.CRASHED, checkResult.patEnc)

        assertEquals(patEnc, alertRepository.received.single().second)
    }
}
