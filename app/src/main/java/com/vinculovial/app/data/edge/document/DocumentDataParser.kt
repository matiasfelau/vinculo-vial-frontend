package com.vinculovial.app.data.edge.document

/**
 * Interpreta el contenido crudo de un código de barras del DNI.
 *
 * Principio abierto/cerrado: cada formato de documento tiene su propia implementación
 * ([LegacyDniPdf417Parser], [ElectronicDniQrParser]); agregar un formato nuevo el día de
 * mañana es sumar una clase que implemente esta interfaz, no tocar las que ya funcionan.
 */
interface DocumentDataParser {

    /** `null` si el contenido no corresponde a un DNI argentino de este formato. */
    fun parse(rawData: String): ArgentineDni?
}
