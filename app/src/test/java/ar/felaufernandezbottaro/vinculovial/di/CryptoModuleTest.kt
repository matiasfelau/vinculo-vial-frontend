package ar.felaufernandezbottaro.vinculovial.di

import ar.felaufernandezbottaro.vinculovial.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Cada destinatario HPKE tiene su propio provider y su propio campo de `BuildConfig`: ninguna
 * clave se deriva de otra ni hay una "por defecto" global.
 */
class CryptoModuleTest {

    @Test
    fun `la clave de IA sale de IA_HPKE_PUBLIC_KEY`() {
        assertEquals(BuildConfig.IA_HPKE_PUBLIC_KEY, CryptoModule.provideIaHpkePublicKey())
    }

    @Test
    fun `cada provider lee su propio campo de BuildConfig`() {
        assertEquals(BuildConfig.IDENTITY_HPKE_PUBLIC_KEY, CryptoModule.provideIdentityHpkePublicKey())
        assertEquals(BuildConfig.VEHICLE_HPKE_PUBLIC_KEY, CryptoModule.provideVehicleHpkePublicKey())
        assertEquals(BuildConfig.IA_HPKE_PUBLIC_KEY, CryptoModule.provideIaHpkePublicKey())
    }

    @Test
    fun `el qualifier de IA es distinto del de Vehicle y del de Identity`() {
        assertNotEquals(IaHpkePublicKey::class, VehicleHpkePublicKey::class)
        assertNotEquals(IaHpkePublicKey::class, IdentityHpkePublicKey::class)
    }
}
