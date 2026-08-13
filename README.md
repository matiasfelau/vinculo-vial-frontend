# vinculo-vial-frontend

App Android de **ViVi (Vínculo Vial)**. Esta entrega implementa **US-01 — Registro de usuario**:
captura de DNI y selfie, OCR + Liveness en el dispositivo (Edge), descarte inmediato de las
imágenes (Zero Retention), cifrado HPKE del payload contra Identity y publicación en el Gateway
con un mock de App Attestation.

El contrato compartido con `vinculo-vial-gateway` e `vinculo-vial-identity` está en
[CLAUDE.md](CLAUDE.md), que es la fuente única de verdad.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean layers · Hilt · Retrofit/OkHttp/Moshi ·
Google Tink (HPKE) · jjwt (mock de Play Integrity).

## Puesta en marcha

1. Copiar `local.properties.example` a `local.properties` y completar:
   - `sdk.dir` — ruta del Android SDK.
   - `IDENTITY_HPKE_PUBLIC_KEY` — Public Key X25519 (base64) de Identity. Debe ser el par de
     `IDENTITY_HPKE_PRIVATE_KEY` en el repo de Identity.
   - `ATTESTATION_MODE` — `mock` o `play_integrity` (ver abajo).
   - `API_BASE_URL` — `http://10.0.2.2:8080/` para el Gateway corriendo en la máquina host.
2. En CI, las mismas claves se pueden pasar como variables de entorno (ver `.env.example`).

`local.properties` y `.env` están gitignored: no se commitea ningún secreto.

### Modos de attestation

El header `X-App-Attestation` se genera de dos formas y **el Gateway tiene que estar
configurado en el mismo modo que la app**:

| | `mock` | `play_integrity` |
|---|---|---|
| Token | JWT HMAC-SHA256 firmado por la app | token opaco emitido por Google |
| Config en la app | `APP_ATTESTATION_PSK` (≥32 bytes) | `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` |
| Validación en Gateway | verifica firma con la misma PSK | llama a `decodeIntegrityToken` con cuenta de servicio |
| Requiere Play Store | no | sí |
| Uso | desarrollo, emuladores, CI | release |

En ambos modos el token se liga al cuerpo enviado con `base64url(SHA-256(body))`
(claim `requestHash` en el mock, `requestHash` nativo en Play Integrity), así un token
capturado no sirve para firmar otro registro. El Gateway debe recalcularlo y rechazar si
no coincide.

El número de proyecto de Cloud sale de Play Console → Configuración → Integridad de la app.
Play Integrity **sólo devuelve veredictos válidos para builds distribuidos por Play**
(internal testing en adelante); en un debug sideloaded devuelve `APP_NOT_INSTALLED`, que la
app traduce a "no pudimos validar tu dispositivo". Por eso el default de desarrollo es `mock`.

```bash
./gradlew assembleDebug        # compilar
./gradlew testDebugUnitTest    # tests unitarios
./gradlew lint                 # análisis estático
```

## Arquitectura

```
app/
  presentation/   Compose + ViewModels (estado en StateFlow). Sólo habla con domain.
    signup/       formulario, consentimiento, captura, éxito y estados de error
    splash/ theme/ components/ navigation/
  domain/         Modelos, reglas y puertos. Sin Retrofit ni Compose.
    signup/       RegisterUserUseCase, SignUpRepository, IdentityVerificationService,
                  SignUpResult/SignUpError, CredentialRules, EdgeResult
  data/
    edge/         SecureImageBuffer, cámara mock, OCR y Liveness mock
    crypto/       HpkeEncryptor (Tink), AttestationProvider (mock JWT | Play Integrity)
    remote/       AuthApiService (Retrofit), AttestationInterceptor
    signup/       SignUpRepositoryImpl, DTOs del contrato
  di/             módulos Hilt
```

### Flujo US-01

1. El usuario completa DNI, email y contraseña; la UI valida con `CredentialRules` (dominio).
2. Consentimiento explícito antes de pedir cámara.
3. Captura del frente del DNI y la selfie: los bytes nacen dentro de un `SecureImageBuffer`.
4. `RegisterUserUseCase` corre el Edge: OCR + Liveness y **descarte de las imágenes en `finally`**.
5. Si el DNI leído no coincide con el tipeado, o Liveness falla, **nada sale del dispositivo**.
6. `SignUpRepositoryImpl` cifra `{dni,email,password}` con HPKE y publica
   `POST /api/v1/auth/signup` con el header `X-App-Attestation`, ligado al cuerpo.
7. `201/400/403/409`, los fallos de red y los de attestation se traducen a `SignUpError`
   y a un mensaje genérico.

### Decisiones que conviene conocer

- **Zero Retention**: las imágenes sólo existen dentro de `SecureImageBuffer`, se prestan con
  `read {}` y al liberarse se sobreescriben con ceros. También se descartan al volver atrás y en
  `onCleared()` del ViewModel. Backup en la nube y device transfer están deshabilitados, y
  `FLAG_SECURE` bloquea capturas de pantalla y la miniatura de apps recientes.
  Límite conocido: la contraseña vive como `String` inmutable hasta que pase el GC —
  eliminarlo exigiría `CharArray` desde el `TextField` de Compose, que no expone esa API.
- **TLS obligatorio** en release; el cleartext sólo se habilita en debug y sólo contra el
  host local (ver `network_security_config.xml`).
- **El DNI que viaja es el que leyó el OCR**, no el que se tipeó; el tipeado sólo sirve para
  contrastar.
- **409 sin detalle**: nunca se informa si colisionó el DNI o el email (evita enumeración).
- **Logging HTTP en nivel BASIC**: jamás el cuerpo (ciphertext) ni los headers (attestation).
  Tampoco se loguean DNI, email, contraseña ni PSK.
- **Mocks aislados detrás de puertos** (`CameraSource`, `OcrEngine`, `LivenessDetector`,
  `AttestationProvider`): pasar a CameraX + ML Kit no toca `domain` ni `presentation`.
  Attestation ya tiene su implementación real: se cambia con `ATTESTATION_MODE`.

## Fuera de alcance de esta entrega

iOS, login/sesión (US-02) y recuperación o verificación de email.

## Pendiente en otros repos

Para activar `ATTESTATION_MODE=play_integrity` hace falta, en `vinculo-vial-gateway`:
decodificar el token contra la Play Integrity API con una cuenta de servicio del mismo
proyecto de Cloud, exigir `PLAY_RECOGNIZED` + `MEETS_DEVICE_INTEGRITY`, y validar el
`requestHash` contra el cuerpo recibido. Hasta entonces, ambos repos van en modo `mock`.
