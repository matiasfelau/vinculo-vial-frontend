package ar.felaufernandezbottaro.vinculovial.data.session

/**
 * Estados de la sesión en la app (US-12).
 *
 * - [NONE]: no hay sesión. Bienvenida o Login.
 * - [LOCKED]: sólo existe el refresh token envuelto con la clave biométrica; no hay nada en
 *   memoria. Desbloqueo.
 * - [UNLOCKED]: access y refresh token sólo en memoria. Home y el resto de la app.
 */
enum class SessionState { NONE, LOCKED, UNLOCKED }
