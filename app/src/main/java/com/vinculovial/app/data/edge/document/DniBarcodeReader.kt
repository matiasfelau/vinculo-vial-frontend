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
 * Lee el número de documento de cualquiera de los dos formatos de DNI en circulación:
 * el PDF417 del frente (DNI actual) o el QR del reverso (DNI electrónico).
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
 * decide qué parser aplicar; no se intenta adivinar por el contenido.
 */
@Singleton
class MlKitDniBarcodeReader @Inject constructor() : DniBarcodeReader {

    /**
     * Limitar los formatos hace la detección más rápida y evita falsos positivos con
     * otros códigos que puedan estar en el documento o alrededor.
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
        return when (format) {
            Barcode.FORMAT_PDF417 -> ArgentineDniParser.parse(raw)?.number
            Barcode.FORMAT_QR_CODE -> ArgentineDniQrParser.parseDniNumber(raw)
            else -> null
        }
    }
}
