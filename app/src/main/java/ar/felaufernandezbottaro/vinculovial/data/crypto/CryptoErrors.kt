package ar.felaufernandezbottaro.vinculovial.data.crypto

/**
 * La app está mal configurada (clave/PSK ausente o inválida). Es un error de build,
 * no del usuario: el repositorio lo mapea a un error genérico de la UI.
 */
class CryptoConfigurationException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** Falló una operación criptográfica en tiempo de ejecución. Nunca incluye el material sensible. */
class CryptoOperationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
