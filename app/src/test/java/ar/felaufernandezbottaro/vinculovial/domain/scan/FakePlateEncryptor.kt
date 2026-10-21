package ar.felaufernandezbottaro.vinculovial.domain.scan

/** Cifrador falso: registra las patentes recibidas y devuelve [result] (`null` simula una falla de cifrado). */
class FakePlateEncryptor(
    var result: String? = "pat-enc-de-prueba",
) : PlateEncryptor {

    val received = mutableListOf<String>()

    override fun encrypt(patente: String): String? {
        received += patente
        return result
    }
}
