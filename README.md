# vinculo-vial-frontend

App Android de **ViVi (Vínculo Vial)**. Esta entrega implementa **US-01 — Registro de usuario**
con onboarding biométrico 100 % local: lectura del PDF417 del DNI, prueba de vida activa y
comparación facial en el dispositivo, descarte inmediato de las imágenes (Zero Retention),
cifrado HPKE del payload contra Identity y publicación en el Gateway con App Attestation de
Google Play Integrity.

El contrato compartido con `vinculo-vial-gateway` e `vinculo-vial-identity` está en
[CLAUDE.md](CLAUDE.md), que es la fuente única de verdad.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean layers · Hilt · CameraX ·
ML Kit (Barcode + Face Detection) · TensorFlow Lite · Retrofit/OkHttp/Moshi ·
Google Tink (HPKE) · Play Integrity.

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
                  SignUpResult/SignUpError, CredentialRules, EdgeResult
  data/
    edge/         SecureImageBuffer, SecureBitmap
      document/   PDF417 con ML Kit, DocumentDataParser (legacy real, electrónico stub), escaneo en vivo
      face/       recorte con ML Kit, embedder TFLite, similitud coseno
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
3. Captura del frente del DNI con CameraX (cámara trasera) y de la selfie (cámara
   frontal). Los bytes salen del buffer de la cámara y entran directo a un
   `SecureImageBuffer`, sin tocar disco.
4. `RegisterUserUseCase` corre el Edge: lectura de PDF417, prueba de vida, match facial y
   **descarte de imágenes, bitmaps y embeddings en `finally`**.
5. Si el documento no se lee, la prueba de vida falla, las caras no coinciden o el DNI leído no
   coincide con el tipeado, **nada sale del dispositivo**.
6. `SignUpRepositoryImpl` cifra `{dni,email,password,biometric_verified}` con HPKE y publica
   `POST /api/v1/auth/signup` con el header `X-App-Attestation`, ligado al cuerpo.
7. `201/400/403/409`, los fallos de red y los de attestation se traducen a `SignUpError`
   y a un mensaje genérico.

### Decisiones que conviene conocer

- **Zero Retention**: las imágenes sólo existen dentro de `SecureImageBuffer`, se prestan con
  `read {}` y al liberarse se sobreescriben con ceros. También se descartan al volver atrás y en
  `onCleared()` del ViewModel. Backup en la nube y device transfer están deshabilitados, y
  `FLAG_SECURE` bloquea capturas de pantalla y la miniatura de apps recientes.
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

## Onboarding biométrico

Corre entero en el dispositivo. Al backend sólo llegan el número de documento y un booleano.

### Un solo paso, sólo el DNI actual (MVP)

Por decisión explícita (YAGNI): mientras RENAPER no publique el formato del DNI
electrónico (código QR en el reverso), la app **sólo acepta el DNI actual** (PDF417 en el
mismo frente). No hay paso de reverso — inventar un parser sin documentación oficial sería
adivinar, y un DNI mal leído es peor que uno rechazado explícitamente.

| Paso | Qué busca | Motor | Resultado |
|---|---|---|---|
| 1. Frente | cara **y** PDF417 a la vez | ML Kit Face Detection + Barcode | número de documento; foto para el embedding A |
| 2. Selfie | prueba de vida + cara | ML Kit Face Detection + TFLite | embedding B |

Si el frente tiene cara pero nunca aparece el código (unos segundos de lectura estable sin
encontrarlo), se avisa con un Toast — *"Para esta fase de pruebas, por favor utiliza un DNI
tarjeta tradicional (con código de barras)"* — y se reinicia el escaneo. No hay una
pantalla de reverso a la que pasar.

**Captura híbrida en el frente**: el autoenfoque, la resolución del preview o un reflejo
sobre el plástico pueden hacer que el escaneo automático nunca se dispare en cierto
hardware — fragmentación de Android que ninguna heurística cubre al 100%. Por eso el
obturador manual está siempre visible (no aparece recién tras el timeout): toma una foto
de resolución completa con `ImageCapture` y la valida ahí mismo contra el mismo pipeline
real (`IdentityVerificationService.canReadDniFront()` — PDF417 + detección de cara), no
contra la heurística del preview. Si no lee, un Toast — *"No pudimos leer el DNI. Evitá
los reflejos y asegurate de que esté nítido"* — y la cámara vuelve a estar disponible; si
lee, sigue exactamente por el mismo camino que el automático. El escaneo en vivo sigue
corriendo en paralelo todo el tiempo; ambos caminos comparten un único lock para que
nunca disparen dos capturas a la vez.

Verificado si `coseno(A, B) ≥ FACE_MATCH_THRESHOLD`.

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

**Qué se borra**: los JPEG (`SecureImageBuffer`), los bitmaps que necesitan ML Kit y TFLite
(`SecureBitmap`) y los embeddings. Un vector facial es un dato biométrico: tratarlo como
inocuo sería tan grave como mandar la foto.

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

### Falta el modelo de reconocimiento facial

ML Kit hace *detección* de caras, no *reconocimiento*: no existe un modelo de embeddings
faciales distribuible por Maven. Hay que colocar un `.tflite` tipo MobileFaceNet en
`app/src/main/assets/` — ver [assets/README.md](app/src/main/assets/README.md) para la forma
esperada y las consideraciones de licencia.

Sin ese archivo la app compila y corre, pero el registro falla con "no podemos validar tu
identidad". **Nunca se da por válido un match que no se pudo calcular.**

El umbral `FACE_MATCH_THRESHOLD` (hoy `0.62`) es un punto de partida sin calibrar. Comparar una
foto impresa contra una selfie es más ruidoso que comparar dos selfies.

## Fuera de alcance de esta entrega

iOS, login/sesión (US-02) y recuperación o verificación de email.

## Pendiente en otros repos

Para activar `ATTESTATION_MODE=play_integrity` hace falta, en `vinculo-vial-gateway`:
decodificar el token contra la Play Integrity API con una cuenta de servicio del mismo
proyecto de Cloud, exigir `PLAY_RECOGNIZED` + `MEETS_DEVICE_INTEGRITY`, y validar el
`requestHash` contra el cuerpo recibido. Hasta entonces, ambos repos van en modo `mock`.
