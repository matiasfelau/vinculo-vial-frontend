package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.CedulaData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkVehicleUseCaseTest {

    private val cedula = CedulaData(dni = "30123456", patente = "AB123CD")

    @Test
    fun `pasa la cedula escaneada al repositorio tal cual`() = runTest {
        val repository = FakeVehicleRepository()
        val linkVehicle = LinkVehicleUseCase(repository)

        linkVehicle(cedula)

        assertEquals(listOf(cedula), repository.received)
    }

    @Test
    fun `devuelve el resultado del repositorio sin transformarlo`() = runTest {
        val repository = FakeVehicleRepository(result = LinkVehicleResult.Failed(LinkVehicleError.OwnerMismatch))
        val linkVehicle = LinkVehicleUseCase(repository)

        val result = linkVehicle(cedula)

        assertEquals(LinkVehicleResult.Failed(LinkVehicleError.OwnerMismatch), result)
    }
}
