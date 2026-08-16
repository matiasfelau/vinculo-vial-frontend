package com.vinculovial.app.data.renaper

import com.vinculovial.app.domain.signup.IdentityVerificationProvider
import com.vinculovial.app.domain.signup.model.IdentityVerificationOutcome
import com.vinculovial.app.domain.signup.model.IdentityVerificationRequest

/**
 * Verificación contra el padrón del RENAPER. **Stub deliberado: todavía no hay nada acá.**
 *
 * Existe para dejar declarado el punto de extensión —el día que haya credenciales, esta es
 * la única clase que hay que completar, y el resto del flujo no se entera— y para que la
 * decisión de conectarlo sea explícita y no un descubrimiento a mitad de camino.
 *
 * Sigue la misma convención que `ElectronicDniQrParser`: falla ruidosamente en vez de
 * devolver algo inventado. Un stub que devolviera [IdentityVerificationOutcome.Match] sería
 * catastrófico —daría por verificada una identidad que nadie verificó— y uno que devolviera
 * `Unavailable` se confundiría con una caída real del servicio.
 *
 * ## Lo que hay que resolver antes de implementarlo
 *
 * 1. **Credenciales y convenio.** El acceso al padrón no es público; requiere convenio con
 *    el organismo y credenciales de servicio. De ahí el mensaje de la excepción.
 * 2. **Contrato real.** Qué campos exige (el trámite es el más probable de faltar: hoy se
 *    parsea del PDF417 y puede venir `null`), qué formato de imagen acepta y qué devuelve.
 * 3. **Privacidad — la decisión de fondo.** Conectar esto significa que **la selfie sale del
 *    dispositivo**, lo que contradice la garantía de Zero Retention que hoy es el centro del
 *    diseño (`CLAUDE.md`: al backend sólo llegan el número de documento y un booleano; ni
 *    las imágenes ni los embeddings salen del teléfono). No es un detalle de implementación:
 *    cambia qué dato biométrico viaja, hacia dónde, con qué base legal y con qué
 *    consentimiento del usuario. Hay que decidirlo con producto y con el equipo del backend
 *    antes de escribir la primera línea de esta clase, no después.
 */
class RenaperIdentityVerificationProvider : IdentityVerificationProvider {

    override suspend fun verify(request: IdentityVerificationRequest): IdentityVerificationOutcome =
        throw NotImplementedError("Pendiente de credenciales API del Estado")
}
