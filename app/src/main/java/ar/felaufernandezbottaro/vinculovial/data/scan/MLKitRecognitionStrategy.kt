package ar.felaufernandezbottaro.vinculovial.data.scan

import android.graphics.Bitmap
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage
import ar.felaufernandezbottaro.vinculovial.domain.scan.PlateRecognitionStrategy
import ar.felaufernandezbottaro.vinculovial.domain.scan.model.RecognizedText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR con ML Kit Text Recognition, sobre el bitmap que ya entregó `FrameCropper` recortado al
 * overlay. Devuelve cada línea detectada como candidato crudo, con la confianza de ML Kit;
 * `PlateRegexFilter` decide cuál (si alguna) tiene forma de patente -- acá no se valida formato,
 * sólo se lee texto.
 */
@ExcludeFromCoverage(reason = "Cliente de ML Kit Text Recognition: necesita los servicios de Google; el filtro de patentes vive en PlateRegexFilter")
@Singleton
class MLKitRecognitionStrategy @Inject constructor() : PlateRecognitionStrategy {

    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): List<RecognizedText> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = try {
            recognizer.process(image).await()
        } catch (e: Exception) {
            // Un frame que falla no invalida el intento: se ignora y sigue el siguiente.
            return emptyList()
        }
        return result.textBlocks.flatMap { block -> block.lines.map { RecognizedText(it.text, it.confidence) } }
    }
}
