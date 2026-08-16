# CLAUDE.md — vinculo-vial-app (Android, US-01 Registro)

## Resumen del caso de uso
Un ciudadano se registra en la plataforma. La app captura DNI (foto) y selfie, ejecuta OCR y Liveness localmente (Edge), descarta las imágenes de RAM inmediatamente (Zero Retention), cifra DNI/email/password con HPKE contra la Public Key de Identity, adjunta un Mock App Attestation (JWT firmado con PSK local) y llama al backend. No recibe tokens de sesión en la respuesta (eso es US-02/Login).

## Fuera de alcance
- iOS.
- Recuperación o verificación de email.

## Onboarding biométrico (Edge)
Todo el procesamiento ocurre en el dispositivo. El backend recibe el número de documento y un booleano; nada más.

### Un solo paso, sólo el DNI actual (MVP)
Por decisión explícita (YAGNI): mientras RENAPER no publique el formato del DNI electrónico (QR en el reverso), la app **sólo acepta el DNI actual** (PDF417 en el mismo frente). No hay paso de reverso.

1. **Frente del DNI** → **100% manual, por decisión de producto.** Hubo una versión con detección automática en vivo (`ImageAnalysis` + ML Kit sobre el preview), pero el autoenfoque, la resolución del preview y los reflejos sobre el plástico la hacían fallar de forma inconsistente en hardware real — fragmentación de Android que ninguna heurística visual cubre del todo. Se retiró esa detección en vivo entera (`DocumentScanSession`, `MlKitDocumentScanSession` y los modelos que la acompañaban ya no existen en el código). `DocumentScanScreen` sólo bindea `Preview` + `ImageCapture` — nada de `ImageAnalysis` para esta pantalla —, muestra el marco como guía visual **estática** y el usuario dispara la foto con el obturador. La única pieza interactiva que sí quedó es **tap-to-focus** sobre el preview (`FocusMeteringAction` vía `camera.cameraControl`, con un anillo puramente visual como feedback) — es control manual de foco, no una heurística de encuadre, así que no contradice la decisión anterior. La foto sí pasa por el pipeline real igual: `IdentityVerificationService.canReadDniFront()` corre PDF417 + detección de cara sobre la imagen ya tomada, así que una foto mala se rechaza con un Toast y la cámara queda lista para reintentar.
2. **Selfie** → prueba de vida activa sobre el preview (ML Kit Face Detection con clasificación); superada, se toma la foto sola. Este paso sigue siendo 100% automático por video — necesita seguir la transición de un gesto en el tiempo, no una foto sola, así que acá `ImageAnalysis` sigue corriendo.

La validación de `canReadDniFront()` y la lectura final y determinante en `verifyAndDiscard()` son pipelines independientes que decodifican por separado (mismo código real — PDF417 + ML Kit Face Detection —, corrido dos veces): la primera es sólo una consulta rápida para la UX ("¿sirve esta foto?"), la segunda es la única fuente de verdad para lo que efectivamente se cifra y viaja al backend. `canReadDniFront()` devuelve `DniFrontQuality` y no un booleano: la pantalla necesita distinguir "no se lee el código" de "no se ve tu foto en el DNI", que se corrigen de formas distintas. ML Kit Face Detection recorta la foto impresa del frente y la de la selfie; TFLite produce los dos embeddings. `coseno(A, B) ≥ FACE_MATCH_THRESHOLD` → `biometric_verified: true`.

### Bug de captura: rotación perdida en `ImageCapture`
`ImageCapture.OnImageCapturedCallback` entrega el JPEG **sin rotar** — el sensor suele estar montado apaisado, y lo que indica cuánto rotar para que la imagen quede vertical es `imageProxy.imageInfo.rotationDegrees`, no el contenido del archivo (no hay garantía de EXIF ahí, a diferencia de la variante que guarda a un `OutputFileOptions`). El código construía `InputImage.fromBitmap(bitmap, 0)` con la rotación hardcodeada en 0, así que ML Kit analizaba una imagen apaisada creyendo que estaba derecha: ni el PDF417 ni la cara del documento se detectaban de forma confiable, **incluso con una foto perfectamente nítida y bien encuadrada** — el síntoma que delata este bug específico frente a uno de calidad de imagen real.

Además, `InputImage.fromBitmap(bitmap, rotationDegrees)` con rotación no nula tiene reportes de comunidad de comportarse de forma inconsistente en detección de caras/texto según el modelo de ML Kit — no alcanza con pasarle el ángulo correcto a ese parámetro.

**Fix**: `CameraInternals.captureJpegInternal()` rota los píxeles físicamente (decode → `Matrix.postRotate()` → recodifica a JPEG) apenas se captura la foto, usando `imageInfo.rotationDegrees`. De ahí en más todo el pipeline recibe una imagen genuinamente vertical y nunca necesita pasarle una rotación distinta de 0 a ML Kit. Corregido en un único punto compartido por la captura del DNI y la de la selfie — la selfie tenía el mismo bug latente, afectando la calidad del embedding para el match.

### Resolución: lo único que decide si el PDF417 se lee
Un decodificador de código de barras necesita del orden de **2-3 píxeles por módulo** (la barra más angosta). En un encuadre típico el PDF417 ocupa cerca de la mitad del ancho de la tarjeta, así que con el lado mayor de la imagen en ~1000px el código queda justo en el límite de lo indecodificable — y ninguna nitidez, iluminación ni encuadre lo compensa, porque el dato ya no está en los píxeles. La cara es indiferente a esto (el embedder reduce a 112×112 igual), así que **el que dicta la resolución de todo el pipeline del documento es el código de barras**.

Dos lugares recortaban resolución sin que se notara, y ambos están corregidos:

1. `SecureBitmap.decode()` submuestreaba con `inSampleSize` "hasta quedar por debajo" del objetivo. Como `inSampleSize` avanza por potencias de dos, eso se pasaba hasta a la mitad: una captura de 4000px con objetivo 1600 terminaba en **1000px**, un 37% por debajo de lo pedido. Ahora corta *antes* de cruzar el umbral (queda entre el objetivo y su doble), a costa de como mucho el doble de memoria. Fijado en `SecureBitmapSampleSizeTest`.
2. `rotatedJpeg()` hacía además un downscale duro a 1000px de lado, agregado para atacar el moiré. Era el instrumento equivocado (ver abajo) y consolidaba el techo anterior; hoy sólo acota memoria, con un piso alto (`MAX_CAPTURE_DIMENSION_PX = 2000`) y aplicado vía `inSampleSize` para no materializar un bitmap gigante.

**Moiré, y por qué el downscale fijo no era la solución**: las líneas de seguridad impresas sobre el plástico tienen una frecuencia espacial parecida a la de las barras y baten con ellas a resolución alta, tapando el patrón — de ahí la paradoja de campo de que una foto perfectamente nítida falle donde una apenas desenfocada funciona (el desenfoque actúa de filtro pasabajos). Pero eso convive con el modo de falla **opuesto** (poca resolución), así que no existe una resolución única que sirva siempre: cualquier número fijo cubre un modo y agrava el otro. La solución real es probar varias escalas al decodificar — `MlKitDniBarcodeReader` recorre `SCALE_LADDER = [1f, 0.6f, 0.4f]` y le alcanza con que una acierte. Reescalar hacia abajo es el mismo filtro pasabajos, pero aplicado a propósito y sin perder el intento a resolución completa.

**No hay detección de nitidez en el pipeline.** Nada mide enfoque, gradiente ni Laplaciano. Lo único que se evalúa sobre la foto del documento es si el código decodifica y si se ve la foto del titular — el mensaje de error que hablaba de nitidez inducía a error y fue reemplazado por uno por modo de falla (`DniFrontQuality`).

**Tamaño de la cara en el documento**: `MlKitFaceLocator.MIN_FACE_SIZE` lo dimensiona el documento, no la selfie. La foto impresa del DNI mide ~19mm de ancho sobre una tarjeta de 85.6mm, así que con la tarjeta llenando el recuadro guía la cara queda apenas por encima de 0.12 — el valor anterior sólo funcionaba con un encuadre perfecto. Está en 0.07; en una selfie la cara es muchísimo más grande y no le afecta.

**Principio abierto/cerrado en el parser**: `DocumentDataParser` es la interfaz (`parse(rawData: String): ArgentineDni?`); `LegacyDniPdf417Parser` la implementa de verdad, `ElectronicDniQrParser` es un **stub deliberado** que lanza `NotImplementedError("A la espera de definiciones oficiales del Renaper")`. No hay heurística de JSON/URL/delimitado inventada — no hay documentación pública del formato del QR, y adivinar es peor que rechazar explícitamente. `MlKitDniBarcodeReader` ya reconoce PDF417 y QR y despacha por formato; el día que RENAPER publique el formato, `ElectronicDniQrParser` es la única clase que hay que tocar (y recién ahí tiene sentido reintroducir el paso del reverso en `DocumentScanSession`/`DocumentScanScreen`, hoy retirado a propósito).

**Prueba de vida**: se exige una *transición* observada en vivo (neutra→sonrisa, u ojos abiertos→cerrados), no un gesto aislado. Comprobar sólo "está sonriendo" lo pasaría una foto impresa de alguien sonriendo. El gesto se sortea en cada intento para que no alcance con grabar un video una vez.

**Zero Retention**: las imágenes viven dentro de `SecureImageBuffer`, y los bitmaps que necesitan ML Kit y TFLite se sobreescriben y reciclan (`SecureBitmap`). Los embeddings también se ponen en cero: un vector facial es un dato biométrico y tratarlo como inocuo sería tan grave como mandar la foto.

**Dependencia externa**: el modelo `.tflite` de reconocimiento facial (MobileFaceNet, 112×112 → 512-d) **no está en el repositorio** (licencia + tamaño, sigue en `.gitignore`). Ver `app/src/main/assets/README.md`. Sin él, o si el nombre del archivo no coincide con `FACE_EMBEDDER_MODEL_ASSET`, la verificación falla con `BiometricsUnavailable` — nunca se da por válida. `TfLiteFaceEmbedder` loguea la excepción real (tag `TfLiteFaceEmbedder`) apenas falla la carga, así que un nombre mal escrito o una forma de tensor inesperada son diagnosticables por logcat en vez de quedar como un booleano opaco. Un caso aparte, que sí requiere reemplazar el archivo (no hay arreglo posible del lado de la app): si el modelo tiene el tensor de entrada o salida en **float16**, `tensorflow-lite` (Java) lo rechaza con `"DataType error: DataType 10 is not recognized in Java"` — esa API nunca soportó float16, en ninguna versión, y `tensorflow-lite-support` no lo evita porque reutiliza el mismo `DataType`. Hay que re-exportar el modelo con la entrada/salida en float32 (los pesos sí pueden seguir cuantizados adentro). Detalle y snippet de conversión en `app/src/main/assets/README.md`.

### Gotcha de CameraX + Compose: pantalla negra
Dos causas posibles, y las dos se dieron acá:

1. **`DisposableEffect` keyeado con el estado que el propio efecto escribe.** Si `cameraProvider` es un `remember { mutableStateOf<ProcessCameraProvider?>(null) }` que se completa DENTRO de un `LaunchedEffect` (recién al terminar `bindToLifecycle`), y el `DisposableEffect` de limpieza se keyea con `cameraProvider`, el cambio de `null` → `provider` hace que Compose trate ese cambio como "la key cambió": dispone el efecto anterior — llamando a `onDispose`, que para entonces ya lee el provider recién bindeado — y lo desvincula ahí mismo, un instante después de bindearlo. La cámara queda unbindeada casi enseguida y nada la vuelve a bindear. Se ve como "la pantalla se pone negra justo después de un instante", porque el bind y el auto-unbind caen en la misma recomposición. **Fix**: `DisposableEffect(Unit)`, nunca keyeado con un estado que el propio setup escribe.
2. `PreviewView` en modo `PERFORMANCE` (default, `SurfaceView`) puede además quedar negro en hardware real cuando hay otro contenido de Compose dibujado encima, aunque el bind esté bien — pasa poco en emulador. `DocumentScanScreen` y `LivenessCaptureScreen` usan `ImplementationMode.COMPATIBLE` (`TextureView`) por las dudas; si se agrega una tercera pantalla con preview, aplicar lo mismo.

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
- **`requestHash`**: en ambos modos el token se liga al cuerpo enviado mediante `hex(SHA-256(body))` (minúscula, 64 caracteres) — como claim en el mock, como `requestHash` nativo en Play Integrity. **Hex, no base64**: es el formato que espera `vinculo-vial-gateway`. El Gateway debe recalcularlo sobre el cuerpo recibido y rechazar si no coincide: sin esto, un token capturado sirve para firmar otro registro.
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
  /data/edge/              # SecureImageBuffer/SecureBitmap, sesión de captura y provider local
  /data/edge/document/     # PDF417 (ML Kit) + DocumentDataParser (legacy real, electrónico stub)
  /data/edge/face/         # recorte (ML Kit), embedder (TFLite), similitud coseno
  /data/edge/liveness/     # desafío de vida sobre el stream de cámara
  /data/renaper/           # provider contra el padrón del Estado (stub deliberado)
  /di/                     # módulos Hilt
```
Regla dura: `domain` no importa Retrofit ni Compose. `presentation` solo llama a `domain`.

### Patrón Estrategia en la verificación de identidad
Hay dos responsabilidades separadas a propósito, y confundirlas es lo que haría frágil el Zero Retention:

- **`IdentityVerificationService`** (puerto en `domain`, implementado por `EdgeIdentityVerificationService`) es la **sesión de captura**: sostiene las imágenes, lee el código de barras, y garantiza que nada sobreviva a `verifyAndDiscard()`. Es común a todas las estrategias — si el borrado dependiera de cada implementación, cada estrategia nueva sería una oportunidad nueva de filtrar imágenes.
- **`IdentityVerificationProvider`** (puerto en `domain`) es la **estrategia de verificación**: dado un `DocumentIdentity` (número, sexo, trámite) y los dos JPEG, decide si es la misma persona. Recibe los bytes **prestados**: no debe conservarlos.

Implementaciones:
- `EdgeIdentityVerificationProvider` — la activa. ML Kit recorta, TFLite embebe, coseno decide. Nada sale del dispositivo.
- `RenaperIdentityVerificationProvider` — **stub deliberado** que lanza `NotImplementedError("Pendiente de credenciales API del Estado")`, misma convención que `ElectronicDniQrParser`. Un stub que devolviera `Match` daría por verificada una identidad que nadie verificó; uno que devolviera `Unavailable` se confundiría con una caída real. Fijado en `RenaperIdentityVerificationProviderTest`.

Cambiar de estrategia es una línea en `EdgeModule.provideIdentityVerificationProvider()`. **Pero no es una decisión sólo técnica**: conectar RENAPER implica que la selfie salga del dispositivo, lo que contradice frontalmente la garantía de que al backend sólo llegan el número de documento y un booleano. Cambia qué dato biométrico viaja, hacia dónde y con qué base legal — hay que decidirlo con producto y backend antes de implementar esa clase, no después.

## Flujo de datos US-01
1. `presentation/signup`: formulario captura Email y Password. `presentation/camera`: pide el permiso `CAMERA` en runtime y toma 2 fotos con CameraX (frente del DNI y selfie), entregándolas **en memoria** (nunca a disco: por eso no se usa `ActivityResultContracts.TakePicture`, que exige URI de destino).
2. `data/edge`: `SecureImageBuffer` toma posesión de los bytes, corre lectura de PDF417 + match facial + liveness → `EdgeResult.Verified(dni, faceSimilarity)` o `EdgeResult.Rejected(reason)`. Inmediatamente después libera/sobreescribe buffers, bitmaps y embeddings — sin cache en disco, sin referencias vivas post-uso (verificado con test).
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
- `FACE_EMBEDDER_MODEL_ASSET` (default `mobilefacenet.tflite`) — nombre del modelo en `assets/`. Tiene que coincidir exacto con el archivo.

## Comandos
```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```