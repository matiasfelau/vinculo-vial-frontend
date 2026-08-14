# CLAUDE.md — vinculo-vial-app (Android, US-01 Registro)

## Resumen del caso de uso
Un ciudadano se registra en la plataforma. La app captura DNI (foto) y selfie, ejecuta OCR y Liveness localmente (Edge), descarta las imágenes de RAM inmediatamente (Zero Retention), cifra DNI/email/password con HPKE contra la Public Key de Identity, adjunta un Mock App Attestation (JWT firmado con PSK local) y llama al backend. No recibe tokens de sesión en la respuesta (eso es US-02/Login).

## Fuera de alcance
- iOS.
- Recuperación o verificación de email.

## Onboarding biométrico (Edge)
Todo el procesamiento ocurre en el dispositivo. El backend recibe el número de documento y un booleano; nada más.

Tres capturas, en este orden:
1. **Reverso del DNI** → ML Kit Barcode Scanning lee el **PDF417** y de ahí sale el número. Es determinista: el código decodifica o no decodifica, sin el margen de interpretación del OCR de texto impreso.
2. **Frente del DNI** → ML Kit Face Detection recorta la foto impresa del titular; TFLite produce el embedding A.
3. **Selfie** → prueba de vida activa sobre el preview (ML Kit Face Detection con clasificación); superada, se toma la foto y se produce el embedding B.

Después: `coseno(A, B) ≥ FACE_MATCH_THRESHOLD` → `biometric_verified: true`.

**Prueba de vida**: se exige una *transición* observada en vivo (neutra→sonrisa, u ojos abiertos→cerrados), no un gesto aislado. Comprobar sólo "está sonriendo" lo pasaría una foto impresa de alguien sonriendo. El gesto se sortea en cada intento para que no alcance con grabar un video una vez.

**Zero Retention**: las imágenes viven dentro de `SecureImageBuffer`, y los bitmaps que necesitan ML Kit y TFLite se sobreescriben y reciclan (`SecureBitmap`). Los embeddings también se ponen en cero: un vector facial es un dato biométrico y tratarlo como inocuo sería tan grave como mandar la foto.

**Dependencia externa**: el modelo `.tflite` de reconocimiento facial **no está en el repositorio** (licencia + tamaño). Ver `app/src/main/assets/README.md`. Sin él, la verificación falla con `BiometricsUnavailable`; nunca se da por válida.

## Contrato compartido entre componentes (US-01 — fuente única de verdad, idéntico en los 3 repos)

- **Endpoint**: `POST /api/v1/auth/signup` (síncrono).
- **Payload externo (lo que viaja por HTTP, cifrado)**:
  ```json
  { "ephemeral_public_key": "<base64>", "ciphertext": "<base64>" }
  ```
  Encoding estándar de un mensaje HPKE modo BASE (KEM X25519/HKDF-SHA256, AEAD AES-256-GCM).
- **Payload interno (una vez desencriptado en Identity)**:
  ```json
  {
    "dni": "12345678",
    "email": "usuario@ejemplo.com",
    "password": "Password123!",
    "biometric_verified": true
  }
  ```
  `biometric_verified` es el **único** rastro del onboarding biométrico que llega al backend: ni las imágenes ni los embeddings faciales salen del dispositivo. Es `true` sólo si en el device se leyó el PDF417, se superó la prueba de vida y el match facial dio por encima del umbral; si algo falla, la request no se envía.

  > ⚠️ **Campo nuevo.** Identity tiene que aceptarlo. Decidir explícitamente si se persiste como atributo del usuario y si se rechaza un signup con `biometric_verified: false` — hoy la app nunca lo manda en `false`, pero el backend no debería confiar en eso.
- **Respuestas**: `201 Created` / `400 Bad Request` / `403 Forbidden` / `409 Conflict`.
- **Header `X-App-Attestation`**: token de vida corta que genera la app y valida el Gateway. Identity nunca lo revisa. Hay **dos modos**, elegidos en build con `ATTESTATION_MODE`; **el Gateway tiene que estar configurado en el mismo modo que la app**:
  - `mock` (desarrollo, default): JWT firmado HMAC-SHA256 con `APP_ATTESTATION_PSK`, simulando el veredicto de Play Integrity (claims equivalentes a `PLAY_RECOGNIZED`, `MEETS_DEVICE_INTEGRITY`). El Gateway verifica firma y expiración.
  - `play_integrity` (producción): token opaco emitido por Google (Standard Request). El Gateway **no puede validarlo con una PSK**: tiene que llamar a `playintegrity.googleapis.com/v1/{packageName}:decodeIntegrityToken` con una cuenta de servicio del mismo proyecto de Cloud, y exigir `appRecognitionVerdict = PLAY_RECOGNIZED` y `deviceRecognitionVerdict` conteniendo `MEETS_DEVICE_INTEGRITY`.
- **`requestHash`**: en ambos modos el token se liga al cuerpo enviado mediante `base64url(SHA-256(body))` — como claim en el mock, como `requestHash` nativo en Play Integrity. El Gateway debe recalcularlo sobre el cuerpo recibido y rechazar si no coincide: sin esto, un token capturado sirve para firmar otro registro.
- **Evento RabbitMQ publicado**: `user.created`, producido por `vinculo-vial-identity`.
  ```json
  { "eventId": "<uuid>", "eventType": "user.created", "timestamp": "<iso8601>", "producer": "vinculo-vial-identity", "actor": null, "payload": { "userId": "<uuid>" } }
  ```
  `actor: null` porque en signup todavía no hay sesión/usuario autenticado actuando.
- **Variables de entorno compartidas conceptualmente** (mismo secreto/par de claves en dos repos, nombre puede variar levemente por convención de cada lenguaje pero el valor debe coincidir):
  - `ATTESTATION_MODE` — `mock` o `play_integrity`. Debe coincidir entre Frontend y Gateway.
  - `APP_ATTESTATION_PSK` — sólo en modo `mock`. Compartida entre Frontend (firma el JWT) y Gateway (lo valida).
  - `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` — sólo en modo `play_integrity`. El Gateway necesita credenciales de servicio del **mismo** proyecto de Cloud para decodificar el token.
  - `IDENTITY_HPKE_PUBLIC_KEY` (Frontend) ↔ `IDENTITY_HPKE_PRIVATE_KEY` (Identity) — mismo par de claves X25519.

## Stack
- Kotlin + Jetpack Compose
- MVVM + Clean layers (`presentation` / `domain` / `data`)
- Hilt (DI)
- CameraX — captura en memoria, sin escribir la foto a disco
- ML Kit (unbundled, vía Play Services) — Barcode Scanning para el PDF417, Face Detection para recorte y liveness
- TensorFlow Lite — embeddings faciales (modelo provisto aparte, ver `app/src/main/assets/README.md`)
- Retrofit + OkHttp + Moshi
- Google Tink — HPKE modo BASE, KEM X25519/HKDF-SHA256, AEAD AES-256-GCM
- `SecureImageBuffer` propio para Zero Retention (buffer de imagen que se sobreescribe/libera explícitamente tras extraer datos)
- Attestation: `com.google.android.play:integrity` (Standard Request) en producción; JWT mock con `io.jsonwebtoken:jjwt` (HMAC-SHA256 con `APP_ATTESTATION_PSK`) en desarrollo

## Estructura de módulos
```
/app
  /presentation/signup/    # SignUpScreen, SignUpViewModel, UiState
  /presentation/camera/    # CameraCaptureScreen (CameraX), CameraPermissionGate
  /domain/signup/          # RegisterUserUseCase, modelos de dominio
  /data/signup/            # SignUpRepository, DTOs, mappers
  /data/remote/            # Retrofit ApiService, interceptors
  /data/crypto/            # HpkeEncryptor (Tink), AttestationProvider (mock JWT | Play Integrity)
  /data/edge/              # SecureImageBuffer/SecureBitmap
  /data/edge/document/     # PDF417 (ML Kit) + parser del DNI argentino
  /data/edge/face/         # recorte (ML Kit), embedder (TFLite), similitud coseno
  /data/edge/liveness/     # desafío de vida sobre el stream de cámara
  /di/                     # módulos Hilt
```
Regla dura: `domain` no importa Retrofit ni Compose. `presentation` solo llama a `domain`.

## Flujo de datos US-01
1. `presentation/signup`: formulario captura Email y Password. `presentation/camera`: pide el permiso `CAMERA` en runtime y toma las tres fotos con CameraX, entregándolas **en memoria** (nunca a disco: por eso no se usa `ActivityResultContracts.TakePicture`, que exige URI de destino).
2. `data/edge`: `SecureImageBuffer` toma posesión de los bytes, corre PDF417 + match facial + liveness → `EdgeResult.Verified(dni, faceSimilarity)` o `EdgeResult.Rejected(reason)`. Inmediatamente después libera/sobreescribe buffers, bitmaps y embeddings — sin cache en disco, sin referencias vivas post-uso (verificado con test).
3. `domain/signup`: `RegisterUserUseCase` arma `{dni, email, password, biometric_verified}`.
4. `data/crypto`: `HpkeEncryptor` (Tink) cifra el payload contra `IDENTITY_HPKE_PUBLIC_KEY` → produce `{ephemeral_public_key, ciphertext}` (base64).
5. `data/crypto`: `AttestationProvider` obtiene el veredicto de integridad ligado al hash del cuerpo — JWT firmado con la PSK en modo `mock`, token de Google en modo `play_integrity`.
6. `data/remote`: `POST /api/v1/auth/signup`, header `X-App-Attestation`, body = `{ephemeral_public_key, ciphertext}`.
7. `presentation/signup`: mapea `201/400/403/409`/error de red a `UiState`.

## Convenciones de código
- Un `ViewModel` por pantalla, estado en `StateFlow<UiState>`.
- Errores de red mapeados a sealed class de dominio, nunca excepción cruda en `presentation`.
- Nunca loguear DNI, password, PSK ni el ciphertext completo.
- Tests: `domain` con JUnit + mocks de repositorio. `data/crypto` y `data/edge` con tests unitarios puros.

## Flujo Git (iterativo por feature)
Rama base `dev`. Una rama por feature, `feature/<slug>`, PR + squash merge a `dev` al cerrar checklist y con tests en verde.

### Plan de branches
1. `feature/signup-ui` — formulario Compose, validación básica de UI.
2. `feature/edge-ocr-liveness-mock` — mocks de OCR/Liveness en `data/edge`, `EdgeResult`.
3. `feature/secure-image-buffer` — `SecureImageBuffer` con Zero Retention garantizado (test que confirma liberación de referencias).
4. `feature/hpke-encryption` — `HpkeEncryptor` (Tink, X25519/HKDF-SHA256/AES-256-GCM), carga de `IDENTITY_HPKE_PUBLIC_KEY`, tests round-trip.
5. `feature/mock-attestation` — `AttestationTokenGenerator`, tests de expiración/firma.
6. `feature/networking-signup` — `ApiService` Retrofit, interceptor `X-App-Attestation`, `SignUpRepository`.
7. `feature/signup-usecase-integration` — `RegisterUserUseCase` end-to-end, mapeo 201/400/403/409 → `UiState`.
8. `feature/signup-error-states` — UI de cada estado de error (mensajes genéricos, sin detalle de enumeración en 409).

## Configuración
`.env.example` / `local.properties.example` (gitignored, consumidos por Gradle → `BuildConfig`):
- `API_BASE_URL`
- `IDENTITY_HPKE_PUBLIC_KEY` — Public Key X25519 (base64) de Identity.
- `ATTESTATION_MODE` — `play_integrity` (default) o `mock`. Falla cerrado: cualquier valor que no sea exactamente `mock` usa la verificación real.
- `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` — sólo modo `play_integrity`. Número de proyecto de Cloud vinculado en Play Console.
- `APP_ATTESTATION_PSK` — sólo modo `mock`. PSK compartida con el Gateway, mínimo 32 bytes.
- `ATTESTATION_TOKEN_TTL_SECONDS` (default `60`) — sólo modo `mock`.
- `NETWORK_TIMEOUT_MS` (default `15000`)
- `FACE_MATCH_THRESHOLD` (default `0.62`) — umbral de similitud coseno. **Sin calibrar**.
- `FACE_EMBEDDER_MODEL_ASSET` (default `face_embedder.tflite`) — nombre del modelo en `assets/`.

## Comandos
```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```