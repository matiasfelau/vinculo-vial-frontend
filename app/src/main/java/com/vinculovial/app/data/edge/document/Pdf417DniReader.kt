package com.vinculovial.app.data.edge.document

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Lee el número de documento del PDF417 del reverso del DNI. */
interface DniBarcodeReader {

    /** `null` si no hay un PDF417 legible o si no es un DNI argentino. */
    suspend fun readDniNumber(dniBackImage: Bitmap): String?
}

/**
 * Implementación con ML Kit Barcode Scanning, restringida a PDF417.
 *
 * Lectura determinista: o el código decodifica o no. No hay margen de interpretación
 * como en el OCR de texto impreso, que es justamente por lo que se eligió esta vía.
 */
@Singleton
class MlKitPdf417DniReader @Inject constructor() : DniBarcodeReader {

    /**
     * Limitar los formatos hace la detección más rápida y evita falsos positivos con
     * otros códigos que puedan estar en el documento.
     */
    private val scanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417)
                .build(),
        )
    }

    override suspend fun readDniNumber(dniBackImage: Bitmap): String? {
        val barcodes = scanner
            .process(InputImage.fromBitmap(dniBackImage, 0))
            .await()

        // Puede haber más de un código en el campo visual; sirve el primero que parsee
        // como DNI argentino.
        return barcodes
            .asSequence()
            .mapNotNull { it.rawValue }
            .mapNotNull(ArgentineDniParser::parse)
            .firstOrNull()
            ?.number
    }
}
