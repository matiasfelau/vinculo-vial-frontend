package com.vinculovial.app.data.edge.document

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vinculovial.app.data.edge.SecureBitmap
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Lee el número de documento de cualquiera de los formatos de DNI que la app reconoce.
 *
 * Hoy sólo el actual (PDF417) tiene lectura real; el electrónico (QR) está reconocido
 * a nivel de formato pero su parser es un stub — ver [ElectronicDniQrParser].
 */
interface DniBarcodeReader {

    /** `null` si no hay un código legible en la imagen, o si no es el de un DNI argentino. */
    suspend fun readDni(image: Bitmap): ArgentineDni?
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

    /**
     * Intenta decodificar a varias escalas antes de darse por vencido.
     *
     * Una foto de un DNI puede fallar por dos motivos **opuestos**, y por eso no existe una
     * resolución única que sirva siempre:
     *
     * - **Muy poca resolución**: por debajo de ~2 píxeles por módulo el código es
     *   irrecuperable. Lo arregla mirar la imagen entera, a escala 1.
     * - **Demasiada**: las líneas de seguridad impresas sobre el plástico tienen una
     *   frecuencia espacial parecida a la de las barras, y a resolución alta baten con ellas
     *   generando moiré que tapa el patrón. Es la explicación de la paradoja de campo — una
     *   foto perfectamente nítida fallando donde una apenas desenfocada funciona: el
     *   desenfoque actúa de filtro pasabajos y borra ese batido.
     *
     * Reescalar hacia abajo es exactamente ese filtro pasabajos, pero aplicado a propósito y
     * de forma reversible: si la escala 1 falla se prueban escalas menores, y basta con que
     * una acierte. Es mucho más robusto que apostar a un número mágico, que por definición
     * sólo cubre uno de los dos modos de falla.
     */
    override suspend fun readDni(image: Bitmap): ArgentineDni? {
        for (scale in SCALE_LADDER) {
            val candidate = image.scaledBy(scale) ?: continue
            try {
                candidate.readFirstDni()?.let { return it }
            } finally {
                // Cada reescalado es otra copia de la foto impresa del titular: no sobrevive
                // al intento que lo necesitó.
                if (candidate !== image) SecureBitmap.wipe(candidate)
            }
        }
        return null
    }

    private suspend fun Bitmap.readFirstDni(): ArgentineDni? {
        val barcodes = scanner.process(InputImage.fromBitmap(this, 0)).await()

        // Puede haber más de un código en el campo visual; sirve el primero que
        // parsee como DNI argentino.
        return barcodes.asSequence().mapNotNull { it.toDni() }.firstOrNull()
    }

    /** `null` si la escala deja una imagen degenerada; escala 1 devuelve el original. */
    private fun Bitmap.scaledBy(scale: Float): Bitmap? {
        if (scale >= 1f) return this

        val targetWidth = (width * scale).roundToInt()
        val targetHeight = (height * scale).roundToInt()
        if (targetWidth < MIN_USABLE_SIDE || targetHeight < MIN_USABLE_SIDE) return null

        return runCatching { scale(targetWidth, targetHeight) }.getOrNull()
    }

    private fun Barcode.toDni(): ArgentineDni? {
        val raw = rawValue ?: return null
        return try {
            when (format) {
                Barcode.FORMAT_PDF417 -> legacyParser.parse(raw)
                Barcode.FORMAT_QR_CODE -> electronicParser.parse(raw)
                else -> null
            }
        } catch (e: NotImplementedError) {
            // El DNI electrónico todavía no tiene parser real (stub deliberado): se
            // trata como "código no reconocido", no como un fallo del registro.
            null
        }
    }

    private companion object {
        /**
         * De mayor a menor: primero la resolución completa (el caso de "poca resolución"),
         * después dos pasadas de suavizado creciente para el moiré. Tres intentos sobre una
         * imagen fija son imperceptibles para el usuario, que ya está esperando el resultado
         * de una foto que disparó a mano.
         */
        val SCALE_LADDER = listOf(1f, 0.6f, 0.4f)

        /** Por debajo de esto no queda nada que decodificar; evita reescalados absurdos. */
        const val MIN_USABLE_SIDE = 200
    }
}
