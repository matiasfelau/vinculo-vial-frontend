package ar.felaufernandezbottaro.vinculovial.data.edge.document

import com.google.mlkit.vision.barcode.common.Barcode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traduce un código ya decodificado por ML Kit a datos de DNI, despachando por formato.
 *
 * Principio abierto/cerrado, igual que antes: el formato detectado (`Barcode.getFormat()`)
 * elige el [DocumentDataParser]; no se adivina por el contenido. El día que RENAPER publique
 * el formato del QR, [ElectronicDniQrParser] es la única clase que hay que completar.
 *
 * Lo que sí cambió respecto de la versión anterior: el resultado distingue **por qué** un
 * código no sirvió. Antes todo caía en `null` y el usuario veía la pantalla buscando para
 * siempre, sin saber si el problema era el encuadre o su propio documento; ahora un DNI
 * electrónico se puede señalar como tal.
 */
@Singleton
class DniBarcodeInterpreter @Inject constructor(
    private val legacyParser: LegacyDniPdf417Parser,
    private val electronicParser: ElectronicDniQrParser,
) {

    sealed interface Reading {

        /** Un DNI argentino leído y parseado. */
        data class Dni(val value: ArgentineDni) : Reading

        /**
         * El código se leyó y corresponde a un formato de documento que la app reconoce
         * pero todavía no sabe interpretar — hoy, el QR del DNI electrónico.
         */
        data object UnsupportedDocument : Reading

        /** Un código cualquiera que no es de un DNI argentino. */
        data object NotADni : Reading
    }

    fun interpret(barcode: Barcode): Reading {
        val raw = barcode.rawValue ?: return Reading.NotADni

        return when (barcode.format) {
            Barcode.FORMAT_PDF417 ->
                legacyParser.parse(raw)?.let(Reading::Dni) ?: Reading.NotADni

            Barcode.FORMAT_QR_CODE -> try {
                electronicParser.parse(raw)?.let(Reading::Dni) ?: Reading.NotADni
            } catch (e: NotImplementedError) {
                // Stub deliberado a la espera de definiciones de RENAPER: el documento es
                // válido, la app es la que no sabe leerlo. Esa diferencia le importa al
                // usuario, así que no se colapsa con NotADni.
                Reading.UnsupportedDocument
            }

            else -> Reading.NotADni
        }
    }
}
