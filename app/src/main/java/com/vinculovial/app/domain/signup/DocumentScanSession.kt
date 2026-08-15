package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.CameraFrame
import com.vinculovial.app.domain.signup.model.DocumentScanProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * Escaneo en vivo del frente del documento: busca cara y código de barras a la vez, y
 * decide cuándo hay señal suficiente como para tomar la foto (o para avisar que el
 * documento no tiene el código que la app sabe leer).
 *
 * Es una sesión con estado sobre *varios frames*, no el análisis de una foto sola: hace
 * falta que la lectura sea estable antes de decidir, para no cortar el escaneo por un
 * frame borroso o mal encuadrado.
 *
 * Los frames se descartan a medida que se procesan; la sesión no acumula imágenes.
 */
interface DocumentScanSession {

    val progress: StateFlow<DocumentScanProgress>

    /** Arranca (o reinicia) el escaneo. */
    fun start()

    /**
     * Entrega un frame del preview. La implementación puede descartarlo si viene
     * demasiado seguido del anterior.
     */
    suspend fun submitFrame(frame: CameraFrame)

    /** Libera los recursos de los detectores. */
    fun close()
}
