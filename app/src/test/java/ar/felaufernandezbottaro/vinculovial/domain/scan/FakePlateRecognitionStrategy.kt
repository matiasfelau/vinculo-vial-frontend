package ar.felaufernandezbottaro.vinculovial.domain.scan

import android.graphics.Bitmap

/** Estrategia falsa: nunca se ejercita en los tests del ViewModel (no hay `Bitmap` real en JVM). */
class FakePlateRecognitionStrategy : PlateRecognitionStrategy {
    override suspend fun recognize(bitmap: Bitmap): List<String> = emptyList()
}
