package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.CameraFrame
import com.vinculovial.app.domain.signup.model.DocumentScanMode
import com.vinculovial.app.domain.signup.model.DocumentScanProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * Escaneo en vivo del documento: decide, mirando el stream de la cámara, si el DNI es el
 * formato actual (código en el frente) o el electrónico (código en el reverso), y cuándo
 * hay señal suficiente como para tomar la foto.
 *
 * Es una sesión con estado sobre *varios frames*, no el análisis de una foto sola: hace
 * falta que la lectura sea estable antes de decidir, para no cortar el escaneo por un
 * frame borroso o mal encuadrado.
 *
 * Los frames se descartan a medida que se procesan; la sesión no acumula imágenes.
 */
interface DocumentScanSession {

    val progress: StateFlow<DocumentScanProgress>

    /** Arranca (o reinicia) el escaneo en el modo indicado. */
    fun start(mode: DocumentScanMode)

    /**
     * Entrega un frame del preview. La implementación puede descartarlo si viene
     * demasiado seguido del anterior.
     */
    suspend fun submitFrame(frame: CameraFrame)

    /** Libera los recursos de los detectores. */
    fun close()
}
