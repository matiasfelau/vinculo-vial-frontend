package ar.felaufernandezbottaro.vinculovial.domain.scan.model

/**
 * Una línea de texto que devolvió el reconocimiento, con la confianza que le asignó el motor
 * (0..1). No expone tipos de ML Kit: la abstracción de `PlateRecognitionStrategy` tiene que
 * poder sustituirse por una estrategia remota sin tocar a quien consume el resultado.
 */
data class RecognizedText(val text: String, val confidence: Float)
