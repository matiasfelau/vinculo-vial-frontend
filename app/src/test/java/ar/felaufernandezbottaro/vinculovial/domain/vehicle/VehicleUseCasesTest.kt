package ar.felaufernandezbottaro.vinculovial.domain.vehicle

import ar.felaufernandezbottaro.vinculovial.domain.vehicle.model.VehicleCount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleUseCasesTest {

    private val repository = FakeVehicleRepository()

    @Test
    fun `vincular normaliza la patente antes de mandarla`() = runTest {
        val result = LinkVehicleUseCase(repository)(" ab-123 cd ")

        assertEquals(VehicleResult.Success(VehicleCount(count = 1, max = 3)), result)
        assertEquals(listOf("AB123CD"), repository.linked)
    }

    @Test
    fun `acepta los dos formatos`() = runTest {
        LinkVehicleUseCase(repository)("AB123CD")
        LinkVehicleUseCase(repository)("abc123")

        assertEquals(listOf("AB123CD", "ABC123"), repository.linked)
    }

    @Test
    fun `una patente invalida no sale del dispositivo, ni al vincular ni al desvincular`() = runTest {
        listOf("", "AB12CD", "ABCD123", "A1B23CD", "1234567", "AB123C", "AB123CDE").forEach { invalid ->
            assertEquals(
                "vincular $invalid",
                VehicleResult.Failed(VehicleError.InvalidPlate),
                LinkVehicleUseCase(repository)(invalid),
            )
            assertEquals(
                "desvincular $invalid",
                VehicleResult.Failed(VehicleError.InvalidPlate),
                UnlinkVehicleUseCase(repository)(invalid),
            )
        }
        assertTrue(repository.linked.isEmpty())
        assertTrue(repository.unlinked.isEmpty())
    }

    @Test
    fun `desvincular normaliza y manda la patente`() = runTest {
        LinkVehicleUseCase(repository)("AB123CD")

        val result = UnlinkVehicleUseCase(repository)("ab 123 cd")

        assertEquals(VehicleResult.Success(VehicleCount(count = 0, max = 3)), result)
        assertEquals(listOf("AB123CD"), repository.unlinked)
    }

    @Test
    fun `el error del backend se propaga tal cual`() = runTest {
        repository.linkFailure = VehicleError.Duplicate
        repository.unlinkFailure = VehicleError.NotFound

        assertEquals(VehicleResult.Failed(VehicleError.Duplicate), LinkVehicleUseCase(repository)("AB123CD"))
        assertEquals(VehicleResult.Failed(VehicleError.NotFound), UnlinkVehicleUseCase(repository)("AB123CD"))
    }

    @Test
    fun `desvincular todos y contar pasan directo al repositorio`() = runTest {
        assertEquals(VehicleResult.Success(Unit), UnlinkAllVehiclesUseCase(repository)())
        assertEquals(VehicleResult.Success(VehicleCount(0, 3)), GetVehicleCountUseCase(repository)())
        assertEquals(1, repository.unlinkAllCalls)
        assertEquals(1, repository.countCalls)
    }

    @Test
    fun `solo las fallas de red y de disponibilidad son transitorias`() {
        assertTrue(VehicleError.Network.isTransient)
        assertTrue(VehicleError.Unavailable.isTransient)
        listOf(
            VehicleError.InvalidPlate,
            VehicleError.NotFound,
            VehicleError.Duplicate,
            VehicleError.LimitReached,
            VehicleError.Configuration,
            VehicleError.Unexpected,
        ).forEach { assertFalse("$it", it.isTransient) }
    }
}
