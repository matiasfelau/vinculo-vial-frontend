package ar.felaufernandezbottaro.vinculovial

/**
 * Marca lo que se deja fuera del reporte de cobertura porque no se puede ejecutar en la JVM:
 * necesita hardware real (cámara, Keystore, biometría) o servicios de Google (ML Kit, Play
 * Integrity, FCM), o es puro cableado (`Application`, `MainActivity`, módulos de Hilt).
 *
 * [reason] es obligatorio a propósito: cada exclusión tiene que decir por qué, y el README las
 * lista una por una. La lógica con decisiones, mapeos o estados NO se excluye: se extrae a una
 * clase probada y lo marcado queda como un adaptador fino.
 *
 * Kover lo lee por `annotatedBy` (ver `app/build.gradle.kts`); la retención es de bytecode, no
 * hace falta reflexión.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FILE,
)
annotation class ExcludeFromCoverage(val reason: String)
