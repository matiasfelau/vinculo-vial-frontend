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
   - `APP_ATTESTATION_PSK` — la misma PSK que valida el Gateway (mínimo 32 bytes).
   - `API_BASE_URL` — `http://10.0.2.2:8080/` para el Gateway corriendo en la máquina host.
2. En CI, las mismas claves se pueden pasar como variables de entorno (ver `.env.example`).

`local.properties` y `.env` están gitignored: no se commitea ningún secreto.

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
    crypto/       HpkeEncryptor (Tink), AttestationTokenGenerator (JWT mock)
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
   `POST /api/v1/auth/signup` con el header `X-App-Attestation`.
7. `201/400/403/409` y los fallos de red se traducen a `SignUpError` y a un mensaje genérico.

### Decisiones que conviene conocer

- **Zero Retention**: las imágenes sólo existen dentro de `SecureImageBuffer`, se prestan con
  `read {}` y al liberarse se sobreescriben con ceros. También se descartan al volver atrás y en
  `onCleared()` del ViewModel. Backup en la nube y device transfer están deshabilitados.
- **El DNI que viaja es el que leyó el OCR**, no el que se tipeó; el tipeado sólo sirve para
  contrastar.
- **409 sin detalle**: nunca se informa si colisionó el DNI o el email (evita enumeración).
- **Logging HTTP en nivel BASIC**: jamás el cuerpo (ciphertext) ni los headers (attestation).
  Tampoco se loguean DNI, email, contraseña ni PSK.
- **Mocks aislados detrás de puertos** (`CameraSource`, `OcrEngine`, `LivenessDetector`): pasar a
  CameraX + ML Kit + Play Integrity real no toca `domain` ni `presentation`.

## Fuera de alcance de esta entrega

Play Integrity real, iOS, login/sesión (US-02) y recuperación o verificación de email.
