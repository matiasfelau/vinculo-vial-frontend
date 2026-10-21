package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoConfigurationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.CryptoOperationException
import ar.felaufernandezbottaro.vinculovial.data.crypto.HpkeEncryptor
import ar.felaufernandezbottaro.vinculovial.data.crypto.toCryptogram
import ar.felaufernandezbottaro.vinculovial.di.VehicleHpkePublicKey
import ar.felaufernandezbottaro.vinculovial.domain.scan.PlateEncryptor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra la patente contra la Public Key de **Vehicle** (nunca la de IA: esa es sólo para las
 * fotos), con `info` vacío. Sin envoltorio JSON: se cifra directamente el texto de la patente.
 */
@Singleton
class VehiclePlateEncryptor @Inject constructor(
    private val encryptor: HpkeEncryptor,
    @VehicleHpkePublicKey private val vehiclePublicKey: String,
) : PlateEncryptor {

    override fun encrypt(patente: String): String? {
        val plaintext = patente.toByteArray(Charsets.UTF_8)
        return try {
            encryptor.encrypt(plaintext, vehiclePublicKey).toCryptogram()
        } catch (e: CryptoConfigurationException) {
            null
        } catch (e: CryptoOperationException) {
            null
        } finally {
            plaintext.fill(0)
        }
    }
}
