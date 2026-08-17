# CLAUDE.md — vinculo-vial-app (Android, US-01 Registro)

## Resumen del caso de uso
Un ciudadano se registra en la plataforma. La app escanea el código del DNI (PDF417 o QR) y ejecuta la prueba de vida localmente (Edge); descarta el documento de RAM inmediatamente (Zero Retention). La **verificación de identidad** (¿la selfie es la persona del documento?) ya no ocurre en el dispositivo — la hace el backend contra el padrón de RENAPER, pivote documentado en la sección "RENAPER-Ready" más abajo. La app cifra `{dni, tramite, sexo, email, password, selfie}` con HPKE contra la Public Key de Identity, adjunta un Mock App Attestation (JWT firmado con PSK local) y llama al backend. No recibe tokens de sesión en la respuesta (eso es US-02/Login).

## Fuera de alcance
- iOS.
- Recuperación o verificación de email.

## Onboarding (Edge)
El escaneo del documento y la prueba de vida corren en el dispositivo. Al backend llegan los campos leídos del documento, las credenciales, y la selfie (comprimida) para que el backend verifique la identidad contra RENAPER.

### Escaneo del documento: en vivo, sin foto (la arquitectura vigente)
El usuario apunta el teléfono al código del DNI y la app lo lee del stream. **No hay obturador, no hay que quedarse quieto y no se saca ninguna foto del documento.** `DocumentScanScreen` bindea sólo `Preview` + `ImageAnalysis`; `MlKitDocumentScanSession` decodifica y entrega `DocumentIdentity` (número, sexo, trámite) ya parseado.

Detecta **PDF417 (DNI tarjeta) y QR (DNI con chip) al mismo tiempo**, sin que el usuario elija qué versión tiene. La **detección** de ambos funciona; la **decodificación** del contenido del QR sigue sin poder hacerse porque RENAPER no publicó el formato — pero ahora eso se le informa al usuario (`DocumentScanStage.UNSUPPORTED_DOCUMENT`, "Todavía no leemos ese DNI") en vez de dejarlo escaneando para siempre sin explicación.

**Las dos piezas que lo hacen posible**, las dos de `BarcodeScannerOptions`:
- `enableAllPotentialBarcodes()` — reporta códigos detectados pero **no** decodificados. Sin esto el escáner es todo-o-nada y no habría forma de saber que el usuario ya está apuntando bien, ni de decírselo.
- `setZoomSuggestionOptions(...)` — **auto-zoom**. Cuando esos códigos están demasiado lejos para leerse, ML Kit calcula la relación de zoom que los haría legibles y la pide por `ZoomController`; la pantalla la aplica con `cameraControl.setZoomRatio()`. Disponible desde la unbundled 18.3.0 (el proyecto está en 18.3.1).

#### Por qué esto invalida la conclusión anterior de que "el stream no alcanza"
Durante varias entregas la conclusión fue que `ImageAnalysis` **nunca** podría decodificar un PDF417 —un decodificador necesita ~2.5 px/módulo, y a distancia cómoda el código ocupa ~13% del ancho del cuadro— y de ahí salió toda la arquitectura híbrida (analizar en vivo sólo geometría, disparar una foto de alta resolución al detectar quietud).

Ese razonamiento asumía un **campo de visión fijo**. Con auto-zoom la premisa se cae: el zoom acerca ópticamente, el código pasa a ocupar mucha más superficie del sensor y los píxeles por módulo suben sin que el usuario mueva la mano. Es resolución conseguida por óptica en vez de por conteo de píxeles del cuadro completo — la variable que faltaba considerar.

**El segundo motivo por el que se puede sacar la foto entera**: desde el pivote RENAPER-Ready, del documento **sólo se necesitan tres campos de texto** que están en la cadena del código. La foto impresa dejó de usarse cuando la comparación facial se movió al backend, así que la única razón que quedaba para sacar una foto de alta resolución era tener píxeles para decodificar. Resuelto eso, la foto no tiene ningún propósito.

#### Qué se eliminó con la arquitectura híbrida, y por qué no puede volver
Con `ImageCapture` fuera de esta pantalla se fue **todo lo que existía para sostenerlo**, junto con los problemas que cada pieza resolvía:
- la topología alternada de streams (`switchToCaptureMode`/`switchToAnalysisMode`) y su margen de asentamiento de sesión de Camera2 — existían por el conflicto de resolución entre streams concurrentes, que sin `ImageCapture` no puede darse;
- el techo de 1080×1440 que persistió pese a cuatro rondas de corrección (ver el historial más abajo) — era un límite de `ImageCapture`, y ya no hay `ImageCapture`;
- el obturador manual de respaldo a los 12s;
- el diagnóstico de `Hardware Level` / resolución del sensor (`logSensorCapabilities`);
- `DocumentFaceDetector` — la presencia de cara en el documento era un gate de calidad heredado de la era del embedder local; sin foto no hay nada sobre lo que evaluarlo, y la verificación real la hace RENAPER;
- `canReadDniFront()` / `DniFrontQuality` — validaban una foto fija que ya no existe;
- `MlKitDniBarcodeReader` y su `SCALE_LADDER` multiescala — el escaneo en vivo ve muchos cuadros a distintas distancias y enfoques, que aporta naturalmente la variación que la escalera simulaba sobre una imagen única.

**Zero Retention mejora**: del documento no se materializa ninguna imagen. No es que se borre bien — es que nunca existe. Los cuadros se analizan y se sueltan; lo único que sobrevive al escaneo son tres campos de texto.

#### La UX
- Marco de cuatro esquinas **sin velo ni rectángulo cerrado**: un marco que se "llena" comunica que el documento tiene que ocupar todo el recuadro, y eso es exactamente lo que empujaba al usuario a cruzar la distancia mínima de enfoque. Con auto-zoom, apuntar alcanza.
- Línea de barrido animada mientras busca, que se apaga en los estados resueltos.
- Estados con mensaje propio: buscando / enfocando / no es un DNI / DNI no soportado / falta el trámite / listo. Ninguno dice "acercate" (ver "El círculo vicioso del encuadre" más abajo).
- **Linterna**: la única acción manual que queda, y la que realmente sirve — el plástico del DNI refleja y con poca luz el código se pierde.
- Confirmación háptica al leer, y una pausa de 450ms antes de navegar para que el cambio de pantalla no se sienta un salto.
- El trámite faltante se detecta **durante el escaneo**, no al final: antes el usuario completaba toda la selfie para que recién ahí se rechazara el registro.

#### Historial: las cuatro rondas de pelea con la resolución de `ImageCapture`
> **Ya no aplica al escaneo del documento** — `ImageCapture` salió de esa pantalla con el escaneo en vivo. Se conserva por dos motivos: la **selfie** sigue usando `ImageCapture` (`LivenessCaptureScreen`), así que la mecánica descrita acá sigue siendo relevante si aparece un problema de resolución ahí; y documenta por qué la arquitectura híbrida terminó descartada en vez de "arreglada".

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

### Los dos pasos del onboarding
1. **Documento** → escaneo en vivo, sin foto (ver arriba). Entrega `DocumentIdentity`.
2. **Selfie** → prueba de vida activa sobre el preview (ML Kit Face Detection con clasificación); superada, se toma la foto sola con `ImageCapture`. Es el **único** lugar que todavía saca una foto, y tiene que hacerlo: la selfie es lo que viaja al backend para que RENAPER verifique la identidad.

### Bug de captura: rotación perdida en `ImageCapture`
`ImageCapture.OnImageCapturedCallback` entrega el JPEG **sin rotar** — el sensor suele estar montado apaisado, y lo que indica cuánto rotar para que la imagen quede vertical es `imageProxy.imageInfo.rotationDegrees`, no el contenido del archivo (no hay garantía de EXIF ahí, a diferencia de la variante que guarda a un `OutputFileOptions`). El código construía `InputImage.fromBitmap(bitmap, 0)` con la rotación hardcodeada en 0, así que ML Kit analizaba una imagen apaisada creyendo que estaba derecha: ni el PDF417 ni la cara del documento se detectaban de forma confiable, **incluso con una foto perfectamente nítida y bien encuadrada** — el síntoma que delata este bug específico frente a uno de calidad de imagen real.

Además, `InputImage.fromBitmap(bitmap, rotationDegrees)` con rotación no nula tiene reportes de comunidad de comportarse de forma inconsistente en detección de caras/texto según el modelo de ML Kit — no alcanza con pasarle el ángulo correcto a ese parámetro.

**Fix**: `CameraInternals.captureJpegInternal()` rota los píxeles físicamente (decode → `Matrix.postRotate()` → recodifica a JPEG) apenas se captura la foto, usando `imageInfo.rotationDegrees`. De ahí en más todo el pipeline recibe una imagen genuinamente vertical y nunca necesita pasarle una rotación distinta de 0 a ML Kit. Corregido en un único punto compartido por la captura del DNI y la de la selfie — la selfie tenía el mismo bug latente, afectando la calidad de la foto que termina viajando al backend.

### El círculo vicioso del encuadre (y por qué era de resolución)
Síntoma de campo: la app pedía acercar el DNI, y al acercarse el lente cruzaba su **distancia focal mínima** y perdía el foco; la foto salía borrosa, se rechazaba, y el mensaje volvía a mandar a acercarse.

Dos hallazgos al auditarlo:

1. **Nunca hubo una validación de encuadre.** Nada medía qué porcentaje de pantalla ocupaba el documento. La exigencia venía del **dibujo y de la copy**: un rectángulo cerrado con la proporción exacta de la tarjeta, con velo oscuro alrededor, comunica "llename", y los mensajes lo decían literalmente. El único límite de tamaño real era `DOCUMENT_MIN_FACE_SIZE`.
2. **Lo que realmente impedía leer a distancia cómoda era el techo de resolución.** A ~25cm la tarjeta ocupa ~27% del ancho del cuadro y el PDF417 ~13%; con ~205 módulos y un mínimo de ~2.5 px/módulo hacen falta ~500px sobre el código, o sea un cuadro de ~3800px. Con el techo en 2000px el código recibía la mitad de los píxeles necesarios y sólo era legible pegando la cámara al plástico — exactamente donde no enfoca.

Correcciones de entonces: subir los techos de resolución, bajar el umbral de tamaño de cara, y reemplazar el rectángulo cerrado por cuatro esquinas sin velo que marcan zona sin sugerir tamaño.

**La lección que sobrevive al cambio de arquitectura, y es la más importante de esta sección**: el problema no era técnico sino de *instrucción*. Nada validaba el encuadre — era el dibujo y la copy los que empujaban al usuario contra el límite físico del lente. El escaneo en vivo actual mantiene el marco de esquinas sin velo, y **ningún estado dice "acercate"** (el auto-zoom lo resuelve solo). Si alguna vez alguien reescribe estos textos, ese es el error a no repetir.

### Resolución: lo único que decide si el PDF417 se lee
> Esta sección describe la era de la foto fija. Sigue siendo la mejor explicación de **por qué** los píxeles por módulo importan tanto — el concepto es el mismo que el auto-zoom resuelve hoy por otra vía —, pero las constantes que menciona ya no existen en el escaneo del documento.

Un decodificador de código de barras necesita del orden de **2-3 píxeles por módulo** (la barra más angosta). En un encuadre típico el PDF417 ocupa cerca de la mitad del ancho de la tarjeta, así que con el lado mayor de la imagen en ~1000px el código queda justo en el límite de lo indecodificable — y ninguna nitidez, iluminación ni encuadre lo compensa, porque el dato ya no está en los píxeles.

Dos lugares recortaban resolución sin que se notara, y ambos están corregidos:

1. `SecureBitmap.decode()` submuestreaba con `inSampleSize` "hasta quedar por debajo" del objetivo. Como `inSampleSize` avanza por potencias de dos, eso se pasaba hasta a la mitad: una captura de 4000px con objetivo 1600 terminaba en **1000px**, un 37% por debajo de lo pedido. Ahora corta *antes* de cruzar el umbral (queda entre el objetivo y su doble), a costa de como mucho el doble de memoria. Fijado en `SecureBitmapSampleSizeTest`.
2. `rotatedJpeg()` hacía además un downscale duro a 1000px de lado, agregado para atacar el moiré. Era el instrumento equivocado (ver abajo) y consolidaba el techo anterior; hoy sólo acota memoria, con un piso alto (`MAX_CAPTURE_DIMENSION_PX = 2000`) y aplicado vía `inSampleSize` para no materializar un bitmap gigante.

**Moiré, y por qué el downscale fijo no era la solución**: las líneas de seguridad impresas sobre el plástico tienen una frecuencia espacial parecida a la de las barras y baten con ellas a resolución alta, tapando el patrón — de ahí la paradoja de campo de que una foto perfectamente nítida falle donde una apenas desenfocada funciona (el desenfoque actúa de filtro pasabajos). Eso convive con el modo de falla **opuesto** (poca resolución), así que sobre una imagen única no existe una resolución que sirva siempre: cualquier número fijo cubre un modo y agrava el otro. La solución de entonces fue una escalera de escalas al decodificar. **Con escaneo en vivo el problema se disuelve**: el stream entrega decenas de cuadros por segundo a distancias y enfoques ligeramente distintos, que es exactamente la variación que la escalera simulaba a mano — y basta con que un cuadro acierte.

**No hay detección de nitidez en el pipeline.** Nada mide enfoque, gradiente ni Laplaciano, y nunca lo hubo. Lo único que se evalúa es si el código decodifica. Un mensaje de error que hablara de nitidez volvería a inducir al error que ya costó caro.

**Autoenfoque: no se toca por Camera2Interop.** CameraX ya usa `CONTROL_AF_MODE_CONTINUOUS_PICTURE` por defecto en el preview, y `startFocusAndMetering()` (el tap-to-focus) pasa a `CONTROL_AF_MODE_AUTO` mientras dura el enfoque manual y vuelve a continuo al cancelar. Como Camera2Interop tiene **más** prioridad que CameraX, fijar el modo AF a mano sería redundante en el mejor caso y rompería el tap-to-focus en el peor — el propio equipo de CameraX desaconseja configurar AF/AE/AWB por interop. Hay un comentario en `DocumentScanScreen` para que la idea no vuelva.

### Pivote RENAPER-Ready: la biometría se movió al backend
Hasta la entrega anterior había un **patrón Strategy** completo para esto: `IdentityVerificationProvider` (puerto), `EdgeIdentityVerificationProvider` (implementación activa, TensorFlow Lite + coseno) y `RenaperIdentityVerificationProvider` (stub, pensado para el día que se conectara RENAPER). Todo eso se **retiró** en este pivote, junto con `FaceEmbedder`, `TfLiteFaceEmbedder`, el modelo `.tflite`, `FaceLocator`/`MlKitFaceLocator` (el recorte cuadrado con margen, `squareBoxWithin()`), `FACE_MATCH_THRESHOLD` y `FACE_EMBEDDER_MODEL_ASSET`. Con ellos se fue toda la historia de calibración del umbral (`0.62` → `0.45` → `0.35`) documentada en entregas previas — dejó de aplicar por completo, no sólo el valor.

**Por qué se retiró la abstracción entera, no sólo la implementación Edge**: el patrón Strategy anticipaba una llamada de verificación *aparte*, previa al signup — pedirle a un `IdentityVerificationProvider` un veredicto sobre dos JPEG antes de decidir si mandar el registro. La integración real que se pidió es otra: la selfie viaja **dentro** del mismo payload de `/signup`, y es el backend el que decide al procesar esa request — no hay una llamada de verificación separada que el frontend orqueste. La abstracción no encajaba con la forma real de la integración, así que mantenerla como cascarón vacío hubiera sido indirección sin propósito.

**Qué queda del lado del Edge**:
- Lectura del código del documento (hoy `MlKitDocumentScanSession` + `DniBarcodeInterpreter`) — sigue siendo la única fuente del `dni`/`sexo`/`trámite`.
- (El detector de cara en el documento, `DocumentFaceDetector`, existió brevemente como gate de calidad de la foto fija y se eliminó junto con ella al pasar a escaneo en vivo — sin foto no hay nada sobre lo que evaluarlo.)
- Prueba de vida — sin cambios, sigue siendo la única defensa local contra una foto-de-una-foto antes de gastar una llamada de red.
- `SelfieCompressor` (nuevo) — redimensiona la selfie a un máximo de 800×800 (nunca agranda) y la recomprime en JPEG, bajando la calidad en escalones desde ~80% hasta entrar en un presupuesto de ~150KB crudos (~200KB ya en Base64, que infla ~4/3). Es la única imagen que sale del dispositivo en todo el flujo.

**Zero Retention, con una única excepción deliberada**: el documento nunca sale — ni la imagen ni un embedding, sólo sus campos parseados. La selfie es la excepción: `EdgeIdentityVerificationService.verify()` sigue liberando todo en el `finally` de `verifyAndDiscard()` (documento, bitmaps intermedios, JPEG original de la selfie), pero deja sobrevivir el string Base64 ya comprimido — es el único dato pensado para viajar, y viaja dentro del mismo payload cifrado que el resto de los campos (`SignUpRepositoryImpl` lo serializa y cifra junto con `dni`/`email`/`password`, y lo pone en cero en el mismo `finally` que ya limpiaba la contraseña).

**`MISSING_PROCEDURE_NUMBER`**: `EdgeResult.Reason` nuevo. RENAPER necesita el trámite para identificar contra qué registro verificar; si el código leído no lo trae (`ArgentineDni.procedureNumber` es nullable — no todas las disposiciones lo incluyen), se rechaza localmente en vez de mandar un registro que el backend no va a poder resolver.

**Principio abierto/cerrado en el parser**: `DocumentDataParser` es la interfaz (`parse(rawData: String): ArgentineDni?`); `LegacyDniPdf417Parser` la implementa de verdad, `ElectronicDniQrParser` es un **stub deliberado** que lanza `NotImplementedError("A la espera de definiciones oficiales del Renaper")`. No hay heurística de JSON/URL/delimitado inventada — no hay documentación pública del formato del QR, y adivinar es peor que rechazar explícitamente. `DniBarcodeInterpreter` reconoce PDF417 y QR y despacha por formato; el día que RENAPER publique el formato, `ElectronicDniQrParser` es la única clase que hay que tocar. Que el stub **lance** en vez de devolver `null` es lo que permite distinguir "DNI electrónico, todavía no lo leemos" de "esto no es un DNI" y decírselo al usuario.

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

  > ⚠️ **Campos nuevos, cambio de contrato.** Identity tiene que: (1) aceptar `tramite`/`sexo`/`selfie` y dejar de esperar `biometric_verified`; (2) implementar la verificación contra RENAPER con la selfie recibida. El punto (3) de esta advertencia —qué código HTTP devuelve un rechazo de RENAPER— ya se resolvió, ver el contrato del 400 más abajo.
- **Respuestas**: `201 Created` / `400 Bad Request` / `403 Forbidden` / `409 Conflict`.
- **Cuerpo del 400** (confirmado con el equipo de backend — es el único código de estado con cuerpo documentado; 403/409 se manejan sólo por código de estado):
  ```json
  { "code": "datos_invalidos", "details": [ { "field": "sexo", "code": "formato_invalido", "message": "texto para mostrar" } ] }
  ```
  ```json
  { "code": "identidad_no_verificada" }
  ```
  - `code` de nivel superior identifica la categoría. `datos_invalidos` trae `details[]`; `identidad_no_verificada` (RENAPER no confirmó la selfie) no trae `details` — deliberadamente genérico, sin campo que señalar, para no distinguir "no es la misma persona" de "no se pudo verificar" (esa distinción es información que un atacante puede usar para calibrar intentos).
  - `details[].code` es el valor **estable** para matchear en código: `requerido` / `formato_invalido` / `longitud_invalida` / `password_debil` / `password_predecible`. `details[].field` es uno de `email`/`dni`/`tramite`/`sexo`/`selfie`/`password`. `details[].message` es texto para mostrar, pensado para cambiar de redacción sin previo aviso — **nunca matchear contra él**.
  - `details[]` puede traer más de un elemento en la misma respuesta: hay que iterarlo, no asumir un único error.
  - Del lado de la app, `SignUpRepositoryImpl.handleBadRequest()` parsea esto a `SignUpError.DocumentDataRejected` (algún campo no es del formulario — `dni`/`tramite`/`sexo`/`selfie` — manda a repetir la captura, no a editar el formulario), `SignUpError.PasswordRejected` (`password_debil`/`password_predecible`, la única categoría de `datos_invalidos` con mensaje específico: es una validación que sólo puede hacerse server-side, el usuario no tiene forma de adivinarlo solo) o `SignUpError.InvalidData` (genérico, para lo demás) — o `SignUpError.IdentityNotVerified` para `identidad_no_verificada`.
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
  /data/edge/document/     # MlKitDocumentScanSession (escaneo en vivo + auto-zoom),
                            # DniBarcodeInterpreter + DocumentDataParser (legacy real, electrónico stub)
  /data/edge/liveness/     # desafío de vida sobre el stream de cámara
  /di/                     # módulos Hilt
```
Regla dura: `domain` no importa Retrofit ni Compose. `presentation` solo llama a `domain`.

## Flujo de datos US-01
1. `presentation/signup`: formulario captura Email y Password. `presentation/camera`: pide el permiso `CAMERA` en runtime, **escanea el documento en vivo** (`DocumentScanSession` decodifica del stream y devuelve `DocumentIdentity` — nunca una imagen) y captura la selfie (automática, tras la prueba de vida) con CameraX, entregando los bytes **en memoria** (nunca a disco: por eso no se usa `ActivityResultContracts.TakePicture`, que exige URI de destino).
2. `data/edge`: `SecureImageBuffer` toma posesión de los bytes de la selfie; `EdgeIdentityVerificationService.verifyAndDiscard()` verifica la prueba de vida y comprime la selfie → `EdgeResult.Verified(document, selfieBase64)` o `EdgeResult.Rejected(reason)`. De la selfie sólo sobrevive la versión comprimida, que es la que tiene que viajar; del documento no hay imagen que liberar porque nunca existió — sin cache en disco (verificado con test).
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