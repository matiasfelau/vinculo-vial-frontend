package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.DniFrontQuality
import com.vinculovial.app.domain.signup.model.EdgeResult

/**
 * Puerto hacia el pipeline Edge. Se define acá para que `domain` no dependa de `data`.
 *
 * El onboarding biométrico necesita dos capturas:
 * 1. frente del DNI, de donde se lee el número por PDF417 y se extrae la foto impresa
 *    del titular;
 * 2. selfie, tomada recién después de superar la prueba de vida.
 *
 * (El DNI electrónico, con el código en el reverso, queda fuera de alcance del MVP —
 * ver `ElectronicDniQrParser`.)
 *
 * El servicio **toma posesión** de los bytes que recibe: quien los pasa no debe conservar
 * la referencia. Ninguna implementación puede exponer las imágenes hacia afuera, y
 * [verifyAndDiscard] debe liberarlas incluso si falla.
 */
interface IdentityVerificationService {

    /** @param image JPEG del frente del documento: código PDF417 y foto del titular. */
    suspend fun acceptDniFront(image: ByteArray)

    /**
     * Revisa una foto ya tomada, sin aceptarla: ¿tiene un código PDF417 legible y se ve la
     * foto impresa del titular? Es la verificación inmediata de la captura manual, para que
     * el usuario sepa en el momento si la foto sirve en vez de enterarse al final del flujo.
     *
     * Devuelve el motivo y no un booleano para que la pantalla pueda decir **qué** corregir:
     * "no se lee el código" y "no se ve la foto del documento" se arreglan de formas
     * distintas.
     *
     * No tiene efecto sobre lo que ya esté aceptado: si da [DniFrontQuality.READABLE], el
     * llamador todavía tiene que pasar la misma imagen a [acceptDniFront] para que cuente en
     * el registro.
     */
    suspend fun canReadDniFront(image: ByteArray): DniFrontQuality

    /**
     * @param image JPEG de la selfie.
     * @param livenessPassed si la prueba de vida se superó en el preview. Se pasa
     * explícitamente para que el resultado del desafío no se pierda entre pantallas.
     */
    suspend fun acceptSelfie(image: ByteArray, livenessPassed: Boolean)

    /**
     * Corre lectura de PDF417 y comparación facial, y descarta las imágenes pase lo
     * que pase.
     */
    suspend fun verifyAndDiscard(): EdgeResult

    /** Descarta cualquier captura pendiente (cancelación, back, error). */
    fun discardCaptures()
}
