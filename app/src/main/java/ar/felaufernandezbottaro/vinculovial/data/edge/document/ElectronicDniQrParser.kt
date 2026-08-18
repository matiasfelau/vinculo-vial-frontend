package ar.felaufernandezbottaro.vinculovial.data.edge.document

import javax.inject.Inject

/**
 * Parser del QR del DNI electrónico (tarjeta con chip, en emisión desde 2026).
 *
 * **Stub deliberado.** RENAPER no publicó el formato del contenido del QR, así que no
 * hay nada confiable que implementar todavía — inventar una heurística sería adivinar,
 * y un DNI mal leído es peor que uno rechazado explícitamente. Cuando RENAPER publique
 * el formato (o se consiga documentarlo contra tarjetas reales), esta es la única clase
 * que hay que tocar: [MlKitDniBarcodeReader] ya está armado para despachar acá sin
 * cambios (principio abierto/cerrado vía [DocumentDataParser]).
 *
 * Por ahora el DNI electrónico está fuera de alcance del MVP: la app sólo acepta el DNI
 * actual (PDF417 en el frente). Ver `DocumentScanScreen` para el mensaje que ve el
 * usuario si su documento no tiene ese código.
 */
class ElectronicDniQrParser @Inject constructor() : DocumentDataParser {

    override fun parse(rawData: String): ArgentineDni =
        throw NotImplementedError("A la espera de definiciones oficiales del Renaper")
}
