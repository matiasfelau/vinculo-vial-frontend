package ar.felaufernandezbottaro.vinculovial.domain.scan

import android.graphics.Bitmap
import ar.felaufernandezbottaro.vinculovial.domain.scan.model.RecognizedText

/** Estrategia falsa: nunca se ejercita en los tests del ViewModel (no hay `Bitmap` real en JVM). */
class FakePlateRecognitionStrategy : PlateRecognitionStrategy {
    override suspend fun recognize(bitmap: Bitmap): List<RecognizedText> = emptyList()
}
