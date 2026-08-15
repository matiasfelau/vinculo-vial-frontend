package com.vinculovial.app.data.edge.document

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lee el número de documento de cualquiera de los formatos de DNI que la app reconoce.
 *
 * Hoy sólo el actual (PDF417) tiene lectura real; el electrónico (QR) está reconocido
 * a nivel de formato pero su parser es un stub — ver [ElectronicDniQrParser].
 */
interface DniBarcodeReader {

    /** `null` si no hay un código legible en la imagen, o si no es el de un DNI argentino. */
    suspend fun readDniNumber(image: Bitmap): String?
}

/**
 * Implementación con ML Kit Barcode Scanning.
 *
 * Lectura determinista: el código decodifica o no decodifica, sin el margen de
 * interpretación del OCR de texto impreso. El formato detectado (`Barcode.getFormat()`)
 * decide qué [DocumentDataParser] aplicar; no se intenta adivinar por el contenido.
 */
@Singleton
class MlKitDniBarcodeReader @Inject constructor(
    private val legacyParser: LegacyDniPdf417Parser,
    private val electronicParser: ElectronicDniQrParser,
) : DniBarcodeReader {

    /**
     * Se reconocen los dos formatos aunque el electrónico todavía no tenga parser real:
     * así, cuando lo tenga, alcanza con completar [ElectronicDniQrParser] — este
     * despacho no cambia (principio abierto/cerrado).
     */
    private val scanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }

    override suspend fun readDniNumber(image: Bitmap): String? {
        val barcodes = scanner.process(InputImage.fromBitmap(image, 0)).await()

        // Puede haber más de un código en el campo visual; sirve el primero que
        // parsee como DNI argentino.
        return barcodes.asSequence().mapNotNull { it.toDniNumber() }.firstOrNull()
    }

    private fun Barcode.toDniNumber(): String? {
        val raw = rawValue ?: return null
        return try {
            when (format) {
                Barcode.FORMAT_PDF417 -> legacyParser.parse(raw)?.number
                Barcode.FORMAT_QR_CODE -> electronicParser.parse(raw).number
                else -> null
            }
        } catch (e: NotImplementedError) {
            // El DNI electrónico todavía no tiene parser real (stub deliberado): se
            // trata como "código no reconocido", no como un fallo del registro.
            null
        }
    }
}
