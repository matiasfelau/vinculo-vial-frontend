package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.CameraFrame
import com.vinculovial.app.domain.signup.model.LivenessProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * Prueba de vida activa: observa el stream de la cámara y exige un gesto.
 *
 * Es deliberadamente una sesión sobre *varios frames* y no un análisis de una foto: una
 * selfie sonriente aislada la puede producir cualquiera con una foto impresa. Lo que
 * prueba presencia es la **transición** entre dos estados observados en vivo.
 *
 * Los frames se descartan a medida que se procesan; la sesión no acumula imágenes.
 */
interface LivenessSession {

    val progress: StateFlow<LivenessProgress>

    /** Arranca (o reinicia) el desafío, eligiendo un gesto al azar. */
    fun start()

    /**
     * Entrega un frame del preview. La implementación puede descartarlo si viene
     * demasiado seguido del anterior.
     */
    suspend fun submitFrame(frame: CameraFrame)

    /** Libera los recursos del detector. */
    fun close()
}
