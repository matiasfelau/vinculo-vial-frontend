package com.vinculovial.app.domain.signup

import com.vinculovial.app.domain.signup.model.IdentityVerificationOutcome
import com.vinculovial.app.domain.signup.model.IdentityVerificationRequest

/**
 * Estrategia de verificación de identidad: dado un documento y dos imágenes, decide si la
 * persona de la selfie es la titular del documento.
 *
 * Es el punto de variación del onboarding biométrico. Hoy la única implementación real corre
 * entera en el dispositivo (`EdgeIdentityVerificationProvider`); mañana puede ser el padrón
 * del Estado (`RenaperIdentityVerificationProvider`). Quien orquesta la captura no sabe cuál
 * de las dos está atrás, y no debería: sólo depende de esta abstracción.
 *
 * ## Qué queda deliberadamente afuera
 *
 * Leer el código de barras, sostener las imágenes y garantizar el Zero Retention **no** son
 * responsabilidad del provider. Eso vive en [IdentityVerificationService], que es común a
 * todas las estrategias: si el borrado de las imágenes dependiera de cada implementación,
 * cada nueva estrategia sería una oportunidad nueva de filtrarlas.
 *
 * ## Advertencia de privacidad para implementaciones remotas
 *
 * Una implementación que consulte un servicio externo **rompe la garantía central del
 * proyecto** de que ninguna imagen sale del dispositivo (ver `CLAUDE.md`: hoy el backend sólo
 * recibe el número de documento y un booleano). Esa no es una decisión técnica que se pueda
 * tomar al implementar la interfaz: cambia qué datos biométricos salen del teléfono, hacia
 * dónde y bajo qué base legal. Esta abstracción deja el enchufe listo; conectar algo remoto
 * requiere decidirlo explícitamente.
 */
interface IdentityVerificationProvider {

    /**
     * @param request los bytes vienen **prestados**: no hay que conservar la referencia más
     * allá de esta llamada. Quien la hizo los borra al volver.
     */
    suspend fun verify(request: IdentityVerificationRequest): IdentityVerificationOutcome
}
