# vinculo-vial-frontend

App Android de **ViVi (Vínculo Vial)**. Esta entrega implementa **US-01 — Registro de usuario**:
captura real de DNI y selfie con la cámara, análisis en el dispositivo (Edge), descarte inmediato
de las imágenes (Zero Retention), cifrado HPKE del payload contra Identity y publicación en el
Gateway con App Attestation de Google Play Integrity.

El contrato compartido con `vinculo-vial-gateway` e `vinculo-vial-identity` está en
[CLAUDE.md](CLAUDE.md), que es la fuente única de verdad.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean layers · Hilt · CameraX ·
Retrofit/OkHttp/Moshi · Google Tink (HPKE) · Play Integrity.

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

En ambos modos el token se liga al cuerpo enviado con `base64url(SHA-256(body))`
(claim `requestHash` en el mock, `requestHash` nativo en Play Integrity), así un token
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
    camera/       CameraCaptureScreen (CameraX), CameraPermissionGate
    splash/ theme/ components/ navigation/
  domain/         Modelos, reglas y puertos. Sin Retrofit ni Compose.
    signup/       RegisterUserUseCase, SignUpRepository, IdentityVerificationService,
                  SignUpResult/SignUpError, CredentialRules, EdgeResult
  data/
    edge/         SecureImageBuffer, CaptureQuality/JpegImage, OCR y Liveness simulados
    crypto/       HpkeEncryptor (Tink), AttestationProvider (Play Integrity | mock JWT)
    remote/       AuthApiService (Retrofit), AttestationInterceptor
    signup/       SignUpRepositoryImpl, DTOs del contrato
  di/             módulos Hilt
```

### Flujo US-01

1. El usuario completa DNI, email y contraseña; la UI valida con `CredentialRules` (dominio).
2. Consentimiento explícito antes de pedir cámara; el permiso `CAMERA` se solicita en
   runtime recién al abrir la captura.
3. Captura del frente del DNI (cámara trasera) y la selfie (frontal) con CameraX. Los bytes
   salen del buffer de la cámara y entran directo a un `SecureImageBuffer`, sin tocar disco.
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

## Estado del Edge

La captura es real; el análisis todavía no. Conviene tenerlo claro antes de demostrarlo:

- **Real**: el rechazo de capturas inservibles. `CaptureQuality` valida que el JPEG esté
  íntegro y tenga resolución suficiente (≥0.9 MP para el documento, ≥0.3 MP para la selfie);
  una foto vacía, truncada o chica corta el registro con "no pudimos leer tu DNI".
- **Simulado**: la extracción y la comparación. `SimulatedOcrEngine` da por leído el DNI que
  tipeó el usuario en lugar de reconocer el texto, y `SimulatedLivenessDetector` da por válida
  la correspondencia entre las dos fotos. **Consecuencia concreta: `DniMismatch` no puede
  dispararse hoy.**
- Para cerrarlo: ML Kit para el texto — o mejor, el PDF417 del reverso del DNI argentino, que
  trae los datos estructurados — y face match + anti-spoofing on-device.

## Fuera de alcance de esta entrega

iOS, login/sesión (US-02), recuperación o verificación de email, y OCR/Liveness reales.

## Pendiente en otros repos

Para activar `ATTESTATION_MODE=play_integrity` hace falta, en `vinculo-vial-gateway`:
decodificar el token contra la Play Integrity API con una cuenta de servicio del mismo
proyecto de Cloud, exigir `PLAY_RECOGNIZED` + `MEETS_DEVICE_INTEGRITY`, y validar el
`requestHash` contra el cuerpo recibido. Hasta entonces, ambos repos van en modo `mock`.
