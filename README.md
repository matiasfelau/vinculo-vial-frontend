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
      document/   PDF417 + QR con ML Kit, parsers del DNI actual y electrónico, escaneo en vivo
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
3. Captura del documento con CameraX (cámara trasera): el frente siempre, el reverso sólo si
   el escaneo en vivo no encontró el código ahí (ver "Onboarding biométrico" abajo). Selfie
   con la cámara frontal. Los bytes salen del buffer de la cámara y entran directo a un
   `SecureImageBuffer`, sin tocar disco.
4. `RegisterUserUseCase` corre el Edge: lectura de código, prueba de vida, match facial y
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

### Dos formatos de DNI, un flujo que se adapta solo

En Argentina conviven el DNI actual (PDF417 junto a la foto, en el frente) y el DNI
electrónico (tarjeta con chip, en emisión desde 2026: sólo la foto en el frente, código QR
en el reverso). La app no pregunta cuál tiene el usuario — lo decide escaneando el frente
en vivo, con lectura estable durante varios frames antes de actuar:

```
Frente ──┬─ cara + código juntos ──────────► listo, va directo a la selfie   (DNI actual)
         └─ cara sin código (confirmado) ──► pide el reverso ──► código ──► selfie   (DNI electrónico)
```

| Paso | Qué busca | Motor | Resultado |
|---|---|---|---|
| 1. Frente | cara **y** código (PDF417 o QR) a la vez | ML Kit Face Detection + Barcode | decide el formato; foto para el embedding A |
| 2. Reverso (condicional) | sólo el código | ML Kit Barcode | número de documento, si no salió del frente |
| 3. Selfie | prueba de vida + cara | ML Kit Face Detection + TFLite | embedding B |

La decisión de qué formato es (`DocumentScanSession`, en vivo, guía la pantalla) y la
lectura final del número (`EdgeIdentityVerificationService`, al terminar, prueba frente y
luego reverso) son cosas separadas a propósito: lo que decide qué le viaja al backend es
siempre la segunda, nunca la heurística de la pantalla. Verificado si
`coseno(A, B) ≥ FACE_MATCH_THRESHOLD`.

**Por qué códigos de barra y no OCR de texto**: decodifican o no decodifican. No hay margen
de interpretación ni dependencia de la tipografía o el desgaste del plástico.

> ⚠️ **El contenido exacto del QR del DNI electrónico no está confirmado contra una tarjeta
> real** — no hay documentación pública con el formato byte a byte. `ArgentineDniQrParser`
> prueba, en orden: el mismo delimitado por `@` del PDF417 (por si RENAPER lo reutilizó),
> JSON, y una URL de verificación con el número como query param. Si ninguno aplica,
> devuelve `null` en vez de inventar un número. **Hay que validar esto contra una tarjeta
> física** apenas se consiga una; si el formato real resulta ser otro (por ejemplo un token
> opaco que sólo tenga sentido para un servicio de verificación de RENAPER, en cuyo caso la
> extracción ya no podría ser 100% on-device), esa clase es el único lugar que hay que tocar.

**Prueba de vida**: se exige una *transición* observada en vivo — de cara neutra a sonrisa, o
de ojos abiertos a cerrados — y no un gesto aislado. Comprobar sólo "está sonriendo" lo pasaría
una foto impresa de alguien sonriendo. El gesto se sortea en cada intento, así no alcanza con
grabar un video una vez. La foto se dispara sola al completar el gesto, para que corresponda al
momento en que se probó la presencia.

**Qué se borra**: los JPEG (`SecureImageBuffer`), los bitmaps que necesitan ML Kit y TFLite
(`SecureBitmap`) y los embeddings. Un vector facial es un dato biométrico: tratarlo como
inocuo sería tan grave como mandar la foto.

### Pantalla negra en la preview de la cámara (CameraX + Compose)

`PreviewView` en modo `PERFORMANCE` (el default) usa `SurfaceView`, que compone en una
superficie aparte de la ventana. Cuando hay otro contenido de Compose dibujado encima —
acá, los overlays de encuadre — esa superficie puede terminar detrás y quedar negra en
hardware real, aunque en el emulador no se note (el overlay sí se ve porque es Compose
normal, compuesto en la jerarquía de la ventana). La solución es
`previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE`
(TextureView, que sí se integra en el dibujado normal). Las dos pantallas de cámara ya lo
tienen; si se agrega una tercera con preview, aplicar lo mismo.

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
