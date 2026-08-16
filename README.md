# vinculo-vial-frontend

App Android de **ViVi (Vínculo Vial)**. Esta entrega implementa **US-01 — Registro de usuario**,
**RENAPER-Ready**: el escaneo del documento (PDF417 o QR, detectados en simultáneo) y la
prueba de vida corren en el dispositivo, pero la verificación de identidad — comparar la
selfie contra el padrón del Estado — la hace el backend. La selfie (redimensionada y
comprimida) es la única imagen que sale del dispositivo; el DNI nunca sale, sólo sus campos
ya leídos. Todo viaja cifrado con HPKE contra Identity, publicado en el Gateway con App
Attestation de Google Play Integrity.

El contrato compartido con `vinculo-vial-gateway` e `vinculo-vial-identity` está en
[CLAUDE.md](CLAUDE.md), que es la fuente única de verdad.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean layers · Hilt · CameraX ·
ML Kit (Barcode + Face Detection) · Retrofit/OkHttp/Moshi · Google Tink (HPKE) · Play Integrity.

## Puesta en marcha

1. Copiar `local.properties.example` a `local.properties` y completar:
   - `sdk.dir` — ruta del Android SDK.
   - `IDENTITY_HPKE_PUBLIC_KEY` — Public Key X25519 (base64) de Identity. Debe ser el par de
     `IDENTITY_HPKE_PRIVATE_KEY` en el repo de Identity.
   - `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` — número de proyecto de Cloud de Play Console.
   - `API_BASE_URL` — `http://10.0.2.2:8080/` para el Gateway corriendo en la máquina host.
     Desde un dispositivo físico usá la IP de tu máquina en la red local, no `10.0.2.2`
     (esa dirección sólo existe dentro del emulador).
2. En CI, las mismas claves se pueden pasar como variables de entorno (ver `.env.example`).

`local.properties` y `.env` están gitignored: no se commitea ningún secreto.

### Modos de attestation

El header `X-App-Attestation` se genera de dos formas y **el Gateway tiene que estar
configurado en el mismo modo que la app**:

| | `play_integrity` (default) | `mock` |
|---|---|---|
| Token | token opaco emitido por Google | JWT HMAC-SHA256 firmado por la app |
| Config en la app | `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` | `APP_ATTESTATION_PSK` (≥32 bytes) |
| Validación en Gateway | llama a `decodeIntegrityToken` con cuenta de servicio | verifica firma con la misma PSK |
| Requiere Play Store | sí | no |
| Uso | dispositivos reales, release | emuladores y CI |

Cualquier valor de `ATTESTATION_MODE` que no sea exactamente `mock` se interpreta como
`play_integrity`: un typo no debe degradar la seguridad en silencio.

En ambos modos el token se liga al cuerpo enviado con `hex(SHA-256(body))` — hexadecimal
minúscula, 64 caracteres, no base64: es el formato que espera el Gateway (claim
`requestHash` en el mock, `requestHash` nativo en Play Integrity), así un token
capturado no sirve para firmar otro registro. El Gateway debe recalcularlo y rechazar si
no coincide.

El número de proyecto de Cloud sale de Play Console → Configuración → Integridad de la app.
Play Integrity reconoce la app por la **firma del APK**: para que un build local devuelva un
veredicto positivo, la clave de firma tiene que estar registrada en Play Console (lo más
simple es subir el build a un track de prueba interna y usar Play App Signing). Un APK
firmado con la clave de debug por defecto devuelve `APP_NOT_INSTALLED`, que la app traduce a
"no pudimos validar tu dispositivo". Para trabajar sin eso, `ATTESTATION_MODE=mock`.

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
    camera/       DocumentScanScreen y LivenessCaptureScreen (CameraX), CameraPermissionGate
    splash/ theme/ components/ navigation/
  domain/         Modelos, reglas y puertos. Sin Retrofit ni Compose.
    signup/       RegisterUserUseCase, SignUpRepository, IdentityVerificationService,
                  SignUpResult/SignUpError, CredentialRules, EdgeResult, DocumentIdentity
  data/
    edge/         SecureImageBuffer, SecureBitmap, SelfieCompressor
      document/   PDF417/QR con ML Kit, DocumentDataParser (legacy real, electrónico stub),
                  DocumentFaceDetector (sólo presencia, gate de calidad del documento),
                  MlKitDocumentPresenceSession (auto-disparo del escaneo)
      liveness/   desafío de vida sobre el stream de cámara
    crypto/       HpkeEncryptor (Tink), AttestationProvider (Play Integrity | mock JWT)
    remote/       AuthApiService (Retrofit), AttestationInterceptor
    signup/       SignUpRepositoryImpl, DTOs del contrato
  di/             módulos Hilt
```

### Flujo US-01

1. El usuario completa DNI, email y contraseña; la UI valida con `CredentialRules` (dominio).
2. Consentimiento explícito antes de pedir cámara; el permiso `CAMERA` se solicita en
   runtime recién al abrir la captura.
3. Escaneo del documento con CameraX (cámara trasera) y captura de la selfie (cámara
   frontal). Los bytes salen del buffer de la cámara y entran directo a un
   `SecureImageBuffer`, sin tocar disco.
4. `RegisterUserUseCase` corre el Edge: lectura de PDF417/QR, prueba de vida, y
   redimensionado + compresión de la selfie (`SelfieCompressor`) — **descarte del documento,
   bitmaps intermedios y JPEG original de la selfie en `finally`**; sólo sobrevive la selfie
   ya comprimida, que es la que tiene que viajar.
5. Si el documento no se lee, le falta el número de trámite, la prueba de vida falla o el
   DNI leído no coincide con el tipeado, **nada sale del dispositivo**.
6. `SignUpRepositoryImpl` cifra `{dni,tramite,sexo,email,password,selfie}` con HPKE y
   publica `POST /api/v1/auth/signup` con el header `X-App-Attestation`, ligado al cuerpo.
   La verificación de identidad —¿la selfie es la persona del documento?— la hace el
   backend contra RENAPER; la app no recibe ese veredicto en la respuesta de signup.
7. `201/400/403/409`, los fallos de red y los de attestation se traducen a `SignUpError`
   y a un mensaje genérico.

### Decisiones que conviene conocer

- **Zero Retention, con una excepción deliberada**: las imágenes sólo existen dentro de
  `SecureImageBuffer`, se prestan con `read {}` y al liberarse se sobreescriben con ceros.
  El documento nunca sale del dispositivo — sólo sus campos parseados. La **selfie es la
  excepción**: `SelfieCompressor` la redimensiona/recomprime, y esa versión (nunca la
  original) es la única imagen que viaja, porque la verificación de identidad ahora la hace
  el backend. Todo se descarta también al volver atrás y en `onCleared()` del ViewModel.
  Backup en la nube y device transfer están deshabilitados, y `FLAG_SECURE` bloquea capturas
  de pantalla y la miniatura de apps recientes.
- **La foto nunca toca el disco**: se usa `ImageCapture.takePicture(executor, OnImageCapturedCallback)`
  y no `ActivityResultContracts.TakePicture`, porque ese contrato exige una URI de destino —
  es decir, dejaría la foto escrita en el almacenamiento y visible para la app de cámara.
  Límite conocido: la contraseña vive como `String` inmutable hasta que pase el GC —
  eliminarlo exigiría `CharArray` desde el `TextField` de Compose, que no expone esa API.
- **TLS obligatorio** en release; el cleartext sólo se habilita en debug y sólo contra el
  host local (ver `network_security_config.xml`).
- **El DNI que viaja es el que leyó el OCR**, no el que se tipeó; el tipeado sólo sirve para
  contrastar.
- **409 sin detalle**: nunca se informa si colisionó el DNI o el email (evita enumeración).
- **Logging HTTP en nivel BASIC**: jamás el cuerpo (ciphertext) ni los headers (attestation).
  Tampoco se loguean DNI, email, contraseña ni PSK.
- **Simulaciones aisladas detrás de puertos** (`OcrEngine`, `LivenessDetector`): reemplazarlas
  no toca `domain` ni `presentation`. Cámara y attestation ya son reales.

## Onboarding

El escaneo del documento y la prueba de vida corren en el dispositivo. La verificación de
identidad (¿la selfie es la persona del documento?) la hace el backend contra RENAPER — es
el único paso que ya no es local. Ver el pivote completo en `CLAUDE.md`.

| Paso | Cómo se dispara | Motor | Resultado |
|---|---|---|---|
| 1. Documento | **auto-disparo híbrido** — `ImageAnalysis` detecta PDF417 o QR en vivo (sin elegir formato) y, al verlo quieto, dispara sola una foto de alta resolución | ML Kit Barcode + Face Detection | número, sexo y trámite del documento |
| 2. Selfie | automático — prueba de vida sobre el preview en vivo | ML Kit Face Detection | JPEG redimensionado/comprimido, listo para el payload |

**El escaneo detecta PDF417 (DNI tarjeta) y QR (DNI con chip) al mismo tiempo**, sin que el
usuario elija qué versión de documento tiene. La *detección* de los dos ya funciona; la
*decodificación* del contenido del QR todavía no — RENAPER no publicó ese formato (ver
`ElectronicDniQrParser`, más abajo). Detalle completo de la arquitectura híbrida
(por qué `ImageAnalysis` sola no alcanza para decodificar, y cómo se dispara la foto de
alta resolución sin que el usuario toque un botón) en `CLAUDE.md`.

Sea cual sea el resultado, `IdentityVerificationService.canReadDniFront()` corre el
pipeline real (barcode + presencia de la foto del titular) sobre la imagen recién tomada
antes de aceptarla. Si no sirve, la app sigue intentando sola — no hay botón que tocar en
el camino feliz, y el mensaje nunca pide "acercarse más" (ver por qué en `CLAUDE.md`).

### Bug de captura: rotación perdida en `ImageCapture`

Si el DNI está perfectamente enfocado, iluminado y sin reflejos y aun así
`canReadDniFront()` falla siempre, la causa casi segura es esta, no la calidad de la foto:

`ImageCapture.OnImageCapturedCallback` entrega el JPEG **sin rotar** — el sensor suele
estar montado apaisado, y lo que indica cuánto rotar para que la imagen quede vertical es
`imageProxy.imageInfo.rotationDegrees`, no el contenido del archivo (a diferencia de la
variante que guarda a un `OutputFileOptions`, acá no hay garantía de que el JPEG traiga
EXIF de orientación). El código construía `InputImage.fromBitmap(bitmap, 0)` con la
rotación hardcodeada en 0: ML Kit analizaba una imagen apaisada creyendo que estaba
derecha, y ni el código de barras ni la cara del documento se detectaban de forma
confiable — con cualquier foto, sin importar lo nítida que fuera.

Tampoco alcanza con pasarle el ángulo correcto a `InputImage.fromBitmap(bitmap,
rotationDegrees)`: ese parámetro tiene reportes de comunidad de comportarse de forma
inconsistente en detección de caras/texto según el modelo de ML Kit.

**Fix**: `CameraInternals.captureJpegInternal()` rota los píxeles físicamente (decode →
`Matrix.postRotate()` → recodifica a JPEG) apenas se captura la foto, usando
`imageInfo.rotationDegrees`. De ahí en más todo el pipeline recibe una imagen
genuinamente vertical y siempre pasa rotación 0 a ML Kit. Corregido en el único punto
compartido por la captura del DNI y la de la selfie — la selfie tenía el mismo bug
latente, afectando en silencio la calidad de la foto que termina viajando al backend.

**Por qué PDF417 y no OCR de texto**: decodifica o no decodifica. No hay margen de
interpretación ni dependencia de la tipografía o el desgaste del plástico.

### Principio abierto/cerrado en el parser

`DocumentDataParser` es la interfaz (`parse(rawData: String): ArgentineDni?`):

- `LegacyDniPdf417Parser` — implementación real, la que efectivamente se usa hoy.
- `ElectronicDniQrParser` — **stub deliberado**. RENAPER no publicó el formato del
  contenido del QR del DNI electrónico; no hay documentación pública del formato byte a
  byte, así que no hay heurística confiable que escribir. Lanza
  `NotImplementedError("A la espera de definiciones oficiales del Renaper")` en vez de
  adivinar con JSON/URL/delimitado.

`MlKitDniBarcodeReader` ya reconoce los dos formatos (PDF417 y QR) y despacha por
`Barcode.getFormat()`; si detecta un QR, lo manda al stub y captura el
`NotImplementedError` como "código no reconocido" (no deja que tumbe el registro). El día
que RENAPER publique el formato, `ElectronicDniQrParser` es la única clase que hay que
tocar — el despacho no cambia. Recién ahí tiene sentido reintroducir el paso del reverso
en `DocumentScanSession`/`DocumentScanScreen`, hoy retirado a propósito.

**Prueba de vida**: se exige una *transición* observada en vivo — de cara neutra a sonrisa, o
de ojos abiertos a cerrados — y no un gesto aislado. Comprobar sólo "está sonriendo" lo pasaría
una foto impresa de alguien sonriendo. El gesto se sortea en cada intento, así no alcanza con
grabar un video una vez. La foto se dispara sola al completar el gesto, para que corresponda al
momento en que se probó la presencia.

**Qué se borra**: los JPEG (`SecureImageBuffer`) y los bitmaps que necesita ML Kit
(`SecureBitmap`) — del documento siempre, de la selfie apenas se obtiene la versión
comprimida que va a viajar. Esa versión comprimida es la única excepción deliberada al
Zero Retention (ver más abajo).

### Pantalla negra en la preview de la cámara (CameraX + Compose)

Dos causas posibles — las dos se dieron en este proyecto, en momentos distintos:

**1. `DisposableEffect` keyeado con el estado que el propio efecto escribe** (la causa
real de "video negro justo después de un instante"). Patrón roto:

```kotlin
var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

LaunchedEffect(Unit) {
    val provider = /* ...bind... */
    cameraProvider = provider   // escribe el mismo estado que la key de abajo
}

DisposableEffect(cameraProvider) {   // 🔴 mal: la key cambia por el propio efecto
    onDispose { cameraProvider?.unbindAll() }
}
```

Cuando `cameraProvider` pasa de `null` a `provider`, Compose ve que la *key* del
`DisposableEffect` cambió y trata eso como "hay que reiniciar el efecto": dispone el
anterior — corriendo `onDispose`, que para ese momento ya lee el provider recién
bindeado — y lo desvincula ahí mismo, un instante después de bindearlo. Nada lo vuelve a
bindear. El bind y el auto-unbind caen en la misma recomposición, así que se percibe como
"la preview se pone negra justo cuando recompone la pantalla" (el overlay, que también
recompone en ese momento, no tiene nada que ver). **Fix**: `DisposableEffect(Unit)`, nunca
keyeado con un estado que el propio setup del efecto escribe.

**2. `PreviewView` en modo `PERFORMANCE`** (el default) usa `SurfaceView`, que compone en
una superficie aparte de la ventana. Con otro contenido de Compose dibujado encima —los
overlays de encuadre— esa superficie puede terminar detrás y quedar negra en hardware
real, aunque en el emulador no se note. `previewView.implementationMode =
PreviewView.ImplementationMode.COMPATIBLE` (TextureView) lo evita. Las dos pantallas de
cámara ya lo tienen, por las dudas, aunque la causa raíz haya sido la (1); si se agrega
una tercera pantalla con preview, aplicar lo mismo.

### Pivote RENAPER-Ready: la selfie viaja, el reconocimiento facial local no

Hasta esta entrega la comparación de caras corría en el dispositivo, con TensorFlow Lite
(`TfLiteFaceEmbedder`, similitud coseno contra un umbral). Ese pipeline entero se retiró:
`FaceEmbedder`, el modelo `.tflite`, el recorte facial con margen y la clase de dominio
`IdentityVerificationProvider` (el patrón Strategy pensado para este momento) ya no están.
La verificación de identidad la hace ahora el backend, contra el padrón de RENAPER.

Eso mueve una línea que hasta acá era absoluta: **la selfie sale del dispositivo.**
`SelfieCompressor` la redimensiona a un máximo de 800×800 y la recomprime en JPEG bajando
la calidad en escalones (arranca en ~80%) hasta entrar en un presupuesto de ~150KB
crudos — lo que da un string Base64 de hasta ~200KB —, y ese string es el único que entra
al payload cifrado. El JPEG original y el bitmap intermedio se descartan igual que
siempre; sólo la versión comprimida sobrevive lo justo para viajar. El documento sigue sin
salir nunca: sólo sus campos ya leídos (`dni`, `tramite`, `sexo`).

**Pendiente cross-repo, igual que en su momento `biometric_verified`**: Identity necesita
poder recibir y usar estos campos nuevos, y el Gateway/Identity tienen que definir qué
código HTTP devuelve un rechazo de RENAPER (hoy cae genéricamente en `400`, mapeado a
`SignUpError.InvalidData`, hasta que se documente algo más específico). Detalle completo
del contrato en `CLAUDE.md`.

## Fuera de alcance de esta entrega

iOS, login/sesión (US-02) y recuperación o verificación de email.

## Pendiente en otros repos

- Para activar `ATTESTATION_MODE=play_integrity` hace falta, en `vinculo-vial-gateway`:
  decodificar el token contra la Play Integrity API con una cuenta de servicio del mismo
  proyecto de Cloud, exigir `PLAY_RECOGNIZED` + `MEETS_DEVICE_INTEGRITY`, y validar el
  `requestHash` contra el cuerpo recibido. Hasta entonces, ambos repos van en modo `mock`.
- Para el pivote RENAPER-Ready: Identity tiene que aceptar `tramite`, `sexo` y `selfie` en
  el payload interno (reemplazan a `biometric_verified`), y decidir/documentar el código
  HTTP de un rechazo de RENAPER. Ver `CLAUDE.md`.
