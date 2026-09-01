package ar.felaufernandezbottaro.vinculovial.domain.scan

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckVehicleUseCaseTest {

    private val patente = "GUQ856"

    @Test
    fun `pasa la patente reconocida al repositorio tal cual`() = runTest {
        val repository = FakeVehicleCheckRepository()
        val checkVehicle = CheckVehicleUseCase(repository)

        checkVehicle(patente)

        assertEquals(listOf(patente), repository.received)
    }

    @Test
    fun `devuelve el resultado del repositorio sin transformarlo`() = runTest {
        val repository = FakeVehicleCheckRepository(result = CheckVehicleResult.Failed(CheckVehicleError.RateLimited))
        val checkVehicle = CheckVehicleUseCase(repository)

        val result = checkVehicle(patente)

        assertEquals(CheckVehicleResult.Failed(CheckVehicleError.RateLimited), result)
    }
}
