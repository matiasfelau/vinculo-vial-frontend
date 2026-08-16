# CLAUDE.md — vinculo-vial-app (Android, US-01 Registro)

## Resumen del caso de uso
Un ciudadano se registra en la plataforma. La app escanea el código del DNI (PDF417 o QR) y ejecuta la prueba de vida localmente (Edge); descarta el documento de RAM inmediatamente (Zero Retention). La **verificación de identidad** (¿la selfie es la persona del documento?) ya no ocurre en el dispositivo — la hace el backend contra el padrón de RENAPER, pivote documentado en la sección "RENAPER-Ready" más abajo. La app cifra `{dni, tramite, sexo, email, password, selfie}` con HPKE contra la Public Key de Identity, adjunta un Mock App Attestation (JWT firmado con PSK local) y llama al backend. No recibe tokens de sesión en la respuesta (eso es US-02/Login).

## Fuera de alcance
- iOS.
- Recuperación o verificación de email.

## Onboarding (Edge)
El escaneo del documento y la prueba de vida corren en el dispositivo. Al backend llegan los campos leídos del documento, las credenciales, y la selfie (comprimida) para que el backend verifique la identidad contra RENAPER.

### Escaneo del documento: dos formatos, detección simultánea
La app detecta **PDF417 (DNI tarjeta) y QR (DNI con chip) al mismo tiempo**, sin que el usuario elija qué versión tiene. Esto reemplaza la decisión anterior de "sólo el DNI actual, un solo paso" — sigue siendo YAGNI, pero acotado a lo que sí cambió: **la detección** de ambos formatos ya funciona (`MlKitDocumentPresenceSession` y `MlKitDniBarcodeReader` reconocen los dos desde hace varias entregas); **la decodificación** del contenido del QR sigue sin poder hacerse, porque RENAPER no publicó ese formato — ver "Principio abierto/cerrado en el parser" más abajo. Un QR detectado pero no decodificable se trata igual que cualquier código ilegible.

1. **Documento** → **auto-disparo híbrido** (ver la sección siguiente). El texto que sigue describe la etapa manual anterior y se conserva porque explica por qué el auto-disparo está armado como está.

#### Arquitectura híbrida de captura (la vigente)
Dos requisitos que ningún caso de uso de CameraX satisface solo:
- **Resolución**: decodificar un PDF417 exige ~2.5 px/módulo; a distancia cómoda el código ocupa ~13% del ancho, así que hacen falta varios megapíxeles. `ImageAnalysis` no llega ni cerca.
- **Momento**: si el usuario dispara a mano, mueve el teléfono justo al presionar.

Se usa cada caso de uso para lo que sirve, corriendo los tres en paralelo:
- `ImageAnalysis` (~1280×960) **no lee nada**: `MlKitDocumentPresenceSession` sólo mira si hay la caja de un código de barras y una cara, y si dejaron de moverse durante 3 frames. La pieza que lo hace posible es `BarcodeScannerOptions.enableAllPotentialBarcodes()`, que devuelve la caja de un código **aunque no lo pueda decodificar** — sin eso el escáner es todo-o-nada y a esta resolución no decodificaría nunca, así que no habría señal.
- Al detectar quietud se dispara `takePicture()` programáticamente. `ImageCapture` lleva `ResolutionSelector` con `HIGHEST_AVAILABLE_STRATEGY` + 4:3, y `takePicture()` corre su secuencia de pre-captura (incluido el disparo de autoenfoque) justo antes de exponer — que resultó ser la forma más confiable de conseguir foco en este hardware.
- Esa foto fija de máxima resolución es la única que se decodifica.

**Por qué esto no repite el fracaso de la detección en vivo**: aquella intentaba *leer* desde el preview. Ésta no decodifica nada; la responsabilidad es estrictamente más chica y es lo que la vuelve viable. El motivo real del fracaso anterior fue siempre la resolución del stream, no la heurística.

**El obturador manual sigue existiendo pero oculto**: reaparece a los 12s si el auto-disparo no logró nada. La detección en vivo ya falló dos veces en hardware real acá, y quedarse sin salida sería peor que la fricción que se vino a eliminar. En el caso feliz nadie lo ve.

##### El bug que motivó todo esto: `ImageCapture` a 1080×1440
Sin `ResolutionSelector`, CameraX entregaba ~1.5MP (la resolución de la pantalla, no la del sensor). Con el documento a distancia cómoda eso dejaba el recorte de la cara impresa en **135×135 px** — ruido para el embedder, que hundía la similitud coseno a **0.33** contra una selfie de 613×613 — y el PDF417 muy por debajo de los px/módulo necesarios. Ningún cálculo aguas abajo recupera píxeles que la captura no entregó: por eso todos los ajustes de `MAX_CAPTURE_DIMENSION_PX` anteriores no movieron el amperímetro, estaban acotando una imagen que ya venía chica.

##### Segunda vuelta: `ResolutionSelector` solo no alcanzó
Con `HIGHEST_AVAILABLE_STRATEGY` puesto, una prueba real siguió midiendo 1080×1440 (recorte de 145×145, score 0.479). La causa no era de configuración: en hardware con `Hardware Level` LIMITED o LEGACY (Camera2), el ISP negocia la resolución de **todos** los streams concurrentes como un conjunto — con `Preview` + `ImageAnalysis` + `ImageCapture` bindeados a la vez, el hardware puede forzar a los tres a la resolución más baja que garantiza para esa combinación de tres streams, sin importar qué `ResolutionSelector` se le pida a uno solo. Es una restricción real y documentada del propio Camera2 (tabla de configuraciones garantizadas por `Hardware Level`), no un bug de configuración.

**Fix**: `imageAnalysis` e `imageCapture` nunca están bindeados a la vez (patrón recomendado por el propio equipo de CameraX). En reposo sólo está bindeado `imageAnalysis` junto con `preview` (que nunca se desbindea). Al momento de disparar, `DocumentScanScreen.switchToCaptureMode()` desbindea `imageAnalysis` y bindea `imageCapture` solo — con sólo dos streams, CameraX recalcula sin la restricción del tercero —, y al terminar `switchToAnalysisMode()` hace el camino inverso para que la detección de presencia retome. El pestañeo de reconfiguración cae en el momento en que ya se muestra "Tomando la foto…", así que no se nota. `CameraInternals.captureJpegInternal()` loguea (`Log.d`, tag `CameraCapture`, sólo debug) la resolución real entregada por cada captura, para diagnosticar sin acceso físico al dispositivo si esto vuelve a pasar en otro hardware.

##### Tercera vuelta: una carrera, no un límite de hardware
Con el fix anterior, una segunda prueba real siguió midiendo la misma resolución baja (1080×1440). La explicación de "el HAL ignora la reconfiguración" no es coherente con cómo funciona Camera2: una `CaptureSession` no puede "ignorar" un cambio en su conjunto de `Surface` porque literalmente no existe sin él — cambiar los streams implica destruir la sesión vieja y crear una nueva, no hay un tercer estado intermedio donde el HAL "elija" quedarse con la vieja.

La explicación consistente con la mecánica real: `bindToLifecycle()` vuelve **antes** de que la sesión de Camera2 termine de reconfigurarse en el hardware — es asíncrono, y CameraX no expone ninguna señal pública de "ya terminó". Encadenar `switchToCaptureMode()` → `takePicture()` sin ningún margen corre el riesgo de que la foto se dispare todavía contra la sesión vieja (con `imageAnalysis` en el grupo de streams), lo que en el log es **indistinguible** de un límite real de hardware — mismo número, causa distinta. `capture()` ahora espera `CAPTURE_SESSION_SETTLE_MS` (400ms, empírico) entre `switchToCaptureMode()` y el disparo. No es una garantía — CameraX no ofrece una API para esperar la confirmación real —, así que si el log de `CameraCapture` sigue mostrando resolución baja después de este cambio, recién ahí un límite real de hardware pasa a ser la explicación más probable.

##### Cuarta vuelta: techo confirmado en campo, mecanismo exacto sin aislar
Con las tres correcciones anteriores aplicadas (`ResolutionSelector`, separar la topología de streams, margen de asentamiento), una prueba de campo siguió midiendo 1080×1440. A esta altura, seguir cambiando el binding a ciegas sin un dato directo del hardware dejó de ser una estrategia razonable.

**Lo que se sabe, con evidencia**: `ImageCapture` entrega 1080×1440 en este dispositivo pese a tres intentos de recuperación distintos, cada uno atacando una causa mecánicamente diferente. **Lo que no se aisló**: el mecanismo exacto — si es un límite real del sensor o alguna otra restricción de la topología de streams específica de este hardware. Se barajó la hipótesis de que "CameraX fuerza `ImageCapture` a igualar la resolución de `Preview` para garantizar el framerate", pero no está verificada — no es un mecanismo documentado de la negociación de resolución de CameraX, que trata cada `UseCase` de forma independiente; se deja registrada como hipótesis, no como hallazgo confirmado.

Para no dejar la duda sin herramienta, `DocumentScanScreen.logSensorCapabilities()` loguea una vez al bindear (`Log.d`, tag `CameraCapture`, sólo debug, vía `Camera2CameraInfo`) el `Hardware Level` real del dispositivo y la resolución JPEG máxima que el sensor anuncia en `SCALER_STREAM_CONFIGURATION_MAP` — la fuente de verdad de Camera2, independiente de cualquier negociación de CameraX. Si esa cifra es bastante mayor a 1080×1440, el techo no es del sensor. Es puramente informativo: no bloquea nada ni cambia comportamiento.

**Decisión de producto**: para los fines de este entregable se acepta el techo de resolución en el hardware de prueba disponible, documentando la limitación en vez de seguir iterando sobre binding sin poder confirmar la causa contra el hardware real. (En la entrega en que se tomó esta decisión, el techo también motivó ajustar `FACE_MATCH_THRESHOLD` — esa variable ya no existe, ver "Pivote RENAPER-Ready" más abajo.)

---

Descripción de la etapa manual anterior, que sigue vigente en todo lo demás:

1. **Frente del DNI** → **100% manual, por decisión de producto.** Hubo una versión con detección automática en vivo (`ImageAnalysis` + ML Kit sobre el preview), pero el autoenfoque, la resolución del preview y los reflejos sobre el plástico la hacían fallar de forma inconsistente en hardware real — fragmentación de Android que ninguna heurística visual cubre del todo. Se retiró esa detección en vivo entera (`DocumentScanSession`, `MlKitDocumentScanSession` y los modelos que la acompañaban ya no existen en el código). `DocumentScanScreen` sólo bindea `Preview` + `ImageCapture` — nada de `ImageAnalysis` para esta pantalla —, muestra el marco como guía visual **estática** y el usuario dispara la foto con el obturador. La única pieza interactiva que sí quedó es **tap-to-focus** sobre el preview (`FocusMeteringAction` vía `camera.cameraControl`, con un anillo puramente visual como feedback) — es control manual de foco, no una heurística de encuadre, así que no contradice la decisión anterior. La foto sí pasa por el pipeline real igual: `IdentityVerificationService.canReadDniFront()` corre PDF417 + detección de cara sobre la imagen ya tomada, así que una foto mala se rechaza con un Toast y la cámara queda lista para reintentar.
2. **Selfie** → prueba de vida activa sobre el preview (ML Kit Face Detection con clasificación); superada, se toma la foto sola. Este paso sigue siendo 100% automático por video — necesita seguir la transición de un gesto en el tiempo, no una foto sola, así que acá `ImageAnalysis` sigue corriendo.

La validación de `canReadDniFront()` y la lectura final y determinante en `verifyAndDiscard()` son pipelines independientes que decodifican por separado (mismo código real, corrido dos veces): la primera es sólo una consulta rápida para la UX ("¿sirve esta foto?"), la segunda es la única fuente de verdad para lo que efectivamente se cifra y viaja al backend. `canReadDniFront()` devuelve `DniFrontQuality` y no un booleano: la pantalla necesita distinguir "no se lee el código" de "no se ve tu foto en el DNI", que se corrigen de formas distintas. `DocumentFaceDetector` sólo confirma presencia (¿hay una cara en la foto impresa?, sin recorte ni margen) — es un gate de calidad del documento, no alimenta ninguna comparación: la comparación de caras ya no ocurre en el dispositivo (ver "RENAPER-Ready" más abajo).

### Bug de captura: rotación perdida en `ImageCapture`
`ImageCapture.OnImageCapturedCallback` entrega el JPEG **sin rotar** — el sensor suele estar montado apaisado, y lo que indica cuánto rotar para que la imagen quede vertical es `imageProxy.imageInfo.rotationDegrees`, no el contenido del archivo (no hay garantía de EXIF ahí, a diferencia de la variante que guarda a un `OutputFileOptions`). El código construía `InputImage.fromBitmap(bitmap, 0)` con la rotación hardcodeada en 0, así que ML Kit analizaba una imagen apaisada creyendo que estaba derecha: ni el PDF417 ni la cara del documento se detectaban de forma confiable, **incluso con una foto perfectamente nítida y bien encuadrada** — el síntoma que delata este bug específico frente a uno de calidad de imagen real.

Además, `InputImage.fromBitmap(bitmap, rotationDegrees)` con rotación no nula tiene reportes de comunidad de comportarse de forma inconsistente en detección de caras/texto según el modelo de ML Kit — no alcanza con pasarle el ángulo correcto a ese parámetro.

**Fix**: `CameraInternals.captureJpegInternal()` rota los píxeles físicamente (decode → `Matrix.postRotate()` → recodifica a JPEG) apenas se captura la foto, usando `imageInfo.rotationDegrees`. De ahí en más todo el pipeline recibe una imagen genuinamente vertical y nunca necesita pasarle una rotación distinta de 0 a ML Kit. Corregido en un único punto compartido por la captura del DNI y la de la selfie — la selfie tenía el mismo bug latente, afectando la calidad de la foto que termina viajando al backend.

### El círculo vicioso del encuadre (y por qué era de resolución)
Síntoma de campo: la app pedía acercar el DNI, y al acercarse el lente cruzaba su **distancia focal mínima** y perdía el foco; la foto salía borrosa, se rechazaba, y el mensaje volvía a mandar a acercarse.

Dos hallazgos al auditarlo:

1. **Nunca hubo una validación de encuadre.** Nada medía qué porcentaje de pantalla ocupaba el documento. La exigencia venía del **dibujo y de la copy**: un rectángulo cerrado con la proporción exacta de la tarjeta, con velo oscuro alrededor, comunica "llename", y los mensajes lo decían literalmente. El único límite de tamaño real era `DOCUMENT_MIN_FACE_SIZE`.
2. **Lo que realmente impedía leer a distancia cómoda era el techo de resolución.** A ~25cm la tarjeta ocupa ~27% del ancho del cuadro y el PDF417 ~13%; con ~205 módulos y un mínimo de ~2.5 px/módulo hacen falta ~500px sobre el código, o sea un cuadro de ~3800px. Con el techo en 2000px el código recibía la mitad de los píxeles necesarios y sólo era legible pegando la cámara al plástico — exactamente donde no enfoca.

Correcciones: `MAX_CAPTURE_DIMENSION_PX` y `DOCUMENT_DECODE_DIMENSION` a 3000 (un sensor de 12MP pasa entero sin submuestrear); el `MIN_FACE_SIZE` del detector de presencia del documento a 0.03 (la cara impresa es ~4% del ancho a 25cm; esa constante vivía en `MlKitFaceLocator.DOCUMENT_MIN_FACE_SIZE`, hoy vive en `MlKitDocumentFaceDetector.MIN_FACE_SIZE` — ver "Pivote RENAPER-Ready"); el rectángulo cerrado reemplazado por cuatro esquinas sin velo, que marcan zona sin sugerir tamaño; y toda la copy reescrita. `DocumentCaptureMessagesTest` prohíbe que "acercá"/"llená" vuelvan a aparecer en un mensaje de captura.

**El warning de ML Kit sobre `Bitmap` no es la causa de nada.** Es un aviso de *rendimiento* dirigido a pipelines de video en vivo, donde conviene `InputImage.fromMediaImage()` sobre el `media.Image` del `ImageProxy` en vez de convertir cada frame a `Bitmap`. Acá se procesa **una sola imagen fija** que llega como JPEG desde `ImageCapture`: no hay `media.Image` que pasar, y las alternativas (`fromByteBuffer` con NV21/YV12) exigirían convertir JPEG→YUV a mano, que es más trabajo y más riesgo, no menos. `fromBitmap` es la entrada correcta para este caso.

### Resolución: lo único que decide si el PDF417 se lee
Un decodificador de código de barras necesita del orden de **2-3 píxeles por módulo** (la barra más angosta). En un encuadre típico el PDF417 ocupa cerca de la mitad del ancho de la tarjeta, así que con el lado mayor de la imagen en ~1000px el código queda justo en el límite de lo indecodificable — y ninguna nitidez, iluminación ni encuadre lo compensa, porque el dato ya no está en los píxeles. La cara es indiferente a esto (`DocumentFaceDetector` sólo necesita confirmar presencia, no una geometría precisa), así que **el que dicta la resolución de todo el pipeline del documento es el código de barras**.

Dos lugares recortaban resolución sin que se notara, y ambos están corregidos:

1. `SecureBitmap.decode()` submuestreaba con `inSampleSize` "hasta quedar por debajo" del objetivo. Como `inSampleSize` avanza por potencias de dos, eso se pasaba hasta a la mitad: una captura de 4000px con objetivo 1600 terminaba en **1000px**, un 37% por debajo de lo pedido. Ahora corta *antes* de cruzar el umbral (queda entre el objetivo y su doble), a costa de como mucho el doble de memoria. Fijado en `SecureBitmapSampleSizeTest`.
2. `rotatedJpeg()` hacía además un downscale duro a 1000px de lado, agregado para atacar el moiré. Era el instrumento equivocado (ver abajo) y consolidaba el techo anterior; hoy sólo acota memoria, con un piso alto (`MAX_CAPTURE_DIMENSION_PX = 2000`) y aplicado vía `inSampleSize` para no materializar un bitmap gigante.

**Moiré, y por qué el downscale fijo no era la solución**: las líneas de seguridad impresas sobre el plástico tienen una frecuencia espacial parecida a la de las barras y baten con ellas a resolución alta, tapando el patrón — de ahí la paradoja de campo de que una foto perfectamente nítida falle donde una apenas desenfocada funciona (el desenfoque actúa de filtro pasabajos). Pero eso convive con el modo de falla **opuesto** (poca resolución), así que no existe una resolución única que sirva siempre: cualquier número fijo cubre un modo y agrava el otro. La solución real es probar varias escalas al decodificar — `MlKitDniBarcodeReader` recorre `SCALE_LADDER = [1f, 0.6f, 0.4f]` y le alcanza con que una acierte. Reescalar hacia abajo es el mismo filtro pasabajos, pero aplicado a propósito y sin perder el intento a resolución completa.

**No hay detección de nitidez en el pipeline.** Nada mide enfoque, gradiente ni Laplaciano. Lo único que se evalúa sobre la foto del documento es si el código decodifica y si se ve la foto del titular — el mensaje de error que hablaba de nitidez inducía a error y fue reemplazado por uno por modo de falla (`DniFrontQuality`).

**Tamaño de la cara en el documento**: `MlKitDocumentFaceDetector.MIN_FACE_SIZE` está en 0.03, deliberadamente permisivo — la foto impresa del DNI mide ~19mm sobre una tarjeta de 85.6mm, y a la distancia donde el lente enfoca sin pelear (~25cm) esa cara puede ser apenas un ~3-4% del ancho del cuadro. Con un valor más alto (0.12, el que había antes de esta corrección) sólo pasaba un encuadre casi perfecto. Antes existía un umbral distinto para la selfie (`SELFIE_MIN_FACE_SIZE = 0.15`, en `MlKitFaceLocator`): ya no hace falta — con la biometría movida al backend, la selfie no pasa por ningún gate local de tamaño de cara (ver "Pivote RENAPER-Ready").

**Autoenfoque: no se toca por Camera2Interop.** CameraX ya usa `CONTROL_AF_MODE_CONTINUOUS_PICTURE` por defecto en el preview, y `startFocusAndMetering()` (el tap-to-focus) pasa a `CONTROL_AF_MODE_AUTO` mientras dura el enfoque manual y vuelve a continuo al cancelar. Como Camera2Interop tiene **más** prioridad que CameraX, fijar el modo AF a mano sería redundante en el mejor caso y rompería el tap-to-focus en el peor — el propio equipo de CameraX desaconseja configurar AF/AE/AWB por interop. Hay un comentario en `DocumentScanScreen` para que la idea no vuelva.

### Pivote RENAPER-Ready: la biometría se movió al backend
Hasta la entrega anterior había un **patrón Strategy** completo para esto: `IdentityVerificationProvider` (puerto), `EdgeIdentityVerificationProvider` (implementación activa, TensorFlow Lite + coseno) y `RenaperIdentityVerificationProvider` (stub, pensado para el día que se conectara RENAPER). Todo eso se **retiró** en este pivote, junto con `FaceEmbedder`, `TfLiteFaceEmbedder`, el modelo `.tflite`, `FaceLocator`/`MlKitFaceLocator` (el recorte cuadrado con margen, `squareBoxWithin()`), `FACE_MATCH_THRESHOLD` y `FACE_EMBEDDER_MODEL_ASSET`. Con ellos se fue toda la historia de calibración del umbral (`0.62` → `0.45` → `0.35`) documentada en entregas previas — dejó de aplicar por completo, no sólo el valor.

**Por qué se retiró la abstracción entera, no sólo la implementación Edge**: el patrón Strategy anticipaba una llamada de verificación *aparte*, previa al signup — pedirle a un `IdentityVerificationProvider` un veredicto sobre dos JPEG antes de decidir si mandar el registro. La integración real que se pidió es otra: la selfie viaja **dentro** del mismo payload de `/signup`, y es el backend el que decide al procesar esa request — no hay una llamada de verificación separada que el frontend orqueste. La abstracción no encajaba con la forma real de la integración, así que mantenerla como cascarón vacío hubiera sido indirección sin propósito.

**Qué queda del lado del Edge**:
- Lectura del código de barras (`DniBarcodeReader`) — sigue siendo la única fuente del `dni`/`sexo`/`trámite`.
- `DocumentFaceDetector` (antes `FaceLocator`) — se achicó a sólo presencia (`hasFace(): Boolean`), sin recorte ni margen: esa geometría existía únicamente para alimentar al embedder, que ya no está. Sigue siendo el gate de calidad de `DniFrontQuality.PHOTO_NOT_FOUND`.
- Prueba de vida — sin cambios, sigue siendo la única defensa local contra una foto-de-una-foto antes de gastar una llamada de red.
- `SelfieCompressor` (nuevo) — redimensiona la selfie a un máximo de 800×800 (nunca agranda) y la recomprime en JPEG, bajando la calidad en escalones desde ~80% hasta entrar en un presupuesto de ~150KB crudos (~200KB ya en Base64, que infla ~4/3). Es la única imagen que sale del dispositivo en todo el flujo.

**Zero Retention, con una única excepción deliberada**: el documento nunca sale — ni la imagen ni un embedding, sólo sus campos parseados. La selfie es la excepción: `EdgeIdentityVerificationService.verify()` sigue liberando todo en el `finally` de `verifyAndDiscard()` (documento, bitmaps intermedios, JPEG original de la selfie), pero deja sobrevivir el string Base64 ya comprimido — es el único dato pensado para viajar, y viaja dentro del mismo payload cifrado que el resto de los campos (`SignUpRepositoryImpl` lo serializa y cifra junto con `dni`/`email`/`password`, y lo pone en cero en el mismo `finally` que ya limpiaba la contraseña).

**`MISSING_PROCEDURE_NUMBER`**: `EdgeResult.Reason` nuevo. RENAPER necesita el trámite para identificar contra qué registro verificar; si el código leído no lo trae (`ArgentineDni.procedureNumber` es nullable — no todas las disposiciones lo incluyen), se rechaza localmente en vez de mandar un registro que el backend no va a poder resolver.

**Principio abierto/cerrado en el parser**: `DocumentDataParser` es la interfaz (`parse(rawData: String): ArgentineDni?`); `LegacyDniPdf417Parser` la implementa de verdad, `ElectronicDniQrParser` es un **stub deliberado** que lanza `NotImplementedError("A la espera de definiciones oficiales del Renaper")`. No hay heurística de JSON/URL/delimitado inventada — no hay documentación pública del formato del QR, y adivinar es peor que rechazar explícitamente. `MlKitDniBarcodeReader` ya reconoce PDF417 y QR y despacha por formato; el día que RENAPER publique el formato, `ElectronicDniQrParser` es la única clase que hay que tocar.

**Prueba de vida**: se exige una *transición* observada en vivo (neutra→sonrisa, u ojos abiertos→cerrados), no un gesto aislado. Comprobar sólo "está sonriendo" lo pasaría una foto impresa de alguien sonriendo. El gesto se sortea en cada intento para que no alcance con grabar un video una vez.

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
    "tramite": "00307513148",
    "sexo": "M",
    "email": "usuario@ejemplo.com",
    "password": "Password123!",
    "selfie": "<base64, JPEG ≤800x800, ~80% calidad, ≤~200KB>"
  }
  ```
  Pivote RENAPER-Ready: reemplaza al `biometric_verified: true` de entregas anteriores. `dni`/`tramite`/`sexo` se leyeron del código de barras del documento (no lo que tipeó el usuario); `selfie` es la única imagen que sale del dispositivo en todo el flujo — la comparación contra el documento ya no la hace el device, la hace Identity/RENAPER. Si el código no se lee, si le falta el trámite o si la prueba de vida falla, la request no se envía.

  > ⚠️ **Campos nuevos, cambio de contrato.** Identity tiene que: (1) aceptar `tramite`/`sexo`/`selfie` y dejar de esperar `biometric_verified`; (2) implementar la verificación contra RENAPER con la selfie recibida; (3) definir y documentar qué código HTTP devuelve un rechazo de esa verificación — hoy la app no tiene un `SignUpError` dedicado para eso y un 400 genérico (`InvalidData`) es lo único que lo cubriría hasta que se especifique algo más preciso.
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
- ML Kit (unbundled, vía Play Services) — Barcode Scanning para PDF417/QR, Face Detection para presencia y liveness. Sin TensorFlow Lite: la verificación de identidad la hace el backend contra RENAPER.
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
  /data/edge/              # SecureImageBuffer/SecureBitmap, SelfieCompressor, sesión de captura
  /data/edge/document/     # PDF417/QR (ML Kit) + DocumentDataParser (legacy real, electrónico stub),
                            # DocumentFaceDetector (sólo presencia), MlKitDocumentPresenceSession
  /data/edge/liveness/     # desafío de vida sobre el stream de cámara
  /di/                     # módulos Hilt
```
Regla dura: `domain` no importa Retrofit ni Compose. `presentation` solo llama a `domain`.

## Flujo de datos US-01
1. `presentation/signup`: formulario captura Email y Password. `presentation/camera`: pide el permiso `CAMERA` en runtime, escanea el documento (auto-disparo híbrido) y captura la selfie (automática, tras la prueba de vida) con CameraX, entregando los bytes **en memoria** (nunca a disco: por eso no se usa `ActivityResultContracts.TakePicture`, que exige URI de destino).
2. `data/edge`: `SecureImageBuffer` toma posesión de los bytes; `EdgeIdentityVerificationService.verifyAndDiscard()` corre lectura de PDF417/QR + prueba de vida + compresión de la selfie → `EdgeResult.Verified(document, selfieBase64)` o `EdgeResult.Rejected(reason)`. Libera/sobreescribe el documento y los bitmaps intermedios siempre; de la selfie sólo sobrevive la versión ya comprimida, que es la que tiene que viajar — sin cache en disco (verificado con test).
3. `domain/signup`: `RegisterUserUseCase` arma `{dni, tramite, sexo, email, password, selfie}`.
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

`FACE_MATCH_THRESHOLD` y `FACE_EMBEDDER_MODEL_ASSET` existieron mientras la verificación de identidad corría on-device con TensorFlow Lite; se retiraron junto con esa lógica en el pivote RENAPER-Ready (ver "Pivote RENAPER-Ready" más arriba). Si aparecen en un `local.properties` viejo no rompen nada — `BuildConfig` simplemente ya no define esos campos.

## Comandos
```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```