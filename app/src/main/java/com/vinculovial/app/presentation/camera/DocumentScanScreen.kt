package com.vinculovial.app.presentation.camera

import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinculovial.app.domain.signup.DocumentPresenceSession
import com.vinculovial.app.domain.signup.model.DniFrontQuality
import com.vinculovial.app.domain.signup.model.DocumentPresenceStage
import com.vinculovial.app.presentation.components.VvCircleIconButton
import com.vinculovial.app.presentation.theme.VvGreen
import com.vinculovial.app.presentation.theme.VvNavy
import com.vinculovial.app.presentation.theme.VvOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Un mensaje por modo de falla, porque se corrigen de formas distintas.
 *
 * Antes había uno solo ("no pudimos leer el DNI, asegurate de que esté nítido") para los dos
 * casos, y era engañoso en las dos direcciones: mandaba a corregir la nitidez cuando el
 * problema podía ser el encuadre, y sugería que existía una medición de nitidez que nunca
 * existió — nada en el pipeline calcula enfoque. Lo único que se evalúa es si el código
 * decodifica y si se ve la foto del titular.
 */
internal fun DniFrontQuality.failureMessage(): String = when (this) {
    DniFrontQuality.READABLE -> ""

    // Ninguno de los dos mensajes pide acercarse. Pedirlo era contraproducente: el lente deja
    // de enfocar antes de que el documento llene el cuadro, así que "acercate más" empujaba
    // justo hacia la foto borrosa. Lo que sí ayuda de verdad es que esté quieto, plano y sin
    // un reflejo encima.
    DniFrontQuality.BARCODE_UNREADABLE ->
        "No pudimos leer el código de barras. Apoyá el DNI en una superficie plana, " +
            "esperá a que se vea nítido y evitá los reflejos."

    DniFrontQuality.PHOTO_NOT_FOUND ->
        "No pudimos ver tu foto en el DNI. Que se vea el documento completo y derecho, " +
            "sin sombras encima."
}

/**
 * Captura del frente del documento: **auto-disparo híbrido**.
 *
 * ## La arquitectura, y por qué es híbrida
 *
 * Hay dos requisitos que ningún caso de uso de CameraX satisface solo:
 *
 * - **Resolución.** Decodificar un PDF417 exige del orden de 2.5 píxeles por módulo. A una
 *   distancia donde el lente enfoque, el código ocupa ~13% del ancho del cuadro, así que hace
 *   falta una imagen de varios megapíxeles. `ImageAnalysis` no llega ni cerca — ese fue
 *   siempre el motivo real por el que la detección en vivo fracasó dos veces en este
 *   proyecto, y no la heurística que se le echaba la culpa.
 * - **Momento.** Pedirle al usuario que dispare a mano no funciona: en el momento en que
 *   presiona, mueve el teléfono.
 *
 * La solución estándar es usar cada caso de uso para lo que sirve. `ImageAnalysis` corre en
 * paralelo pero **no lee nada**: sólo mira si hay la caja de un código de barras y una cara, y
 * si dejaron de moverse (ver [DocumentPresenceSession]). Cuando eso pasa, se dispara
 * programáticamente `ImageCapture` a **máxima resolución del sensor**, y esa foto fija es la
 * que se decodifica. De yapa, `takePicture()` corre su secuencia de pre-captura —incluido el
 * disparo de autoenfoque— justo antes de exponer, que es la forma más confiable de conseguir
 * foco en este hardware.
 *
 * ## `ImageAnalysis` e `ImageCapture` nunca están bindeados a la vez
 *
 * En hardware con `Hardware Level` LIMITED o LEGACY (Camera2), el ISP negocia la resolución
 * de **todos** los streams concurrentes como un conjunto: agregar un tercer stream (el de
 * análisis) a la sesión de Preview + Captura puede forzar a los tres a la resolución más baja
 * que el hardware garantiza para esa combinación — en un dispositivo real, eso midió 1080x1440
 * (1.5MP, la del preview) donde la sola combinación Preview + Captura habría dado varios
 * megapíxeles más. Es una restricción real del hardware, documentada en el propio Camera2
 * (tabla de configuraciones garantizadas), no un bug de configuración.
 *
 * La salida, y el patrón que recomienda el propio equipo de CameraX: `imageAnalysis` e
 * `imageCapture` **nunca conviven en el mismo `bindToLifecycle()`**. En reposo sólo está
 * bindeado `imageAnalysis` (junto con `preview`, que nunca se toca). Al momento de disparar,
 * [switchToCaptureMode] lo desbindea y bindea `imageCapture` solo — ahí CameraX recalcula la
 * resolución para el par Preview + Captura, sin la restricción del tercer stream — y al
 * terminar, [switchToAnalysisMode] hace el camino inverso para que la detección de presencia
 * siga corriendo. El pestañeo de reconfiguración que esto produce cae exactamente en el
 * momento en que ya se muestra "Tomando la foto…", así que no se nota.
 *
 * ## Sobre el obturador manual
 *
 * No aparece en el flujo normal: el usuario sostiene el teléfono y la app hace el resto. Pero
 * reaparece si el auto-disparo no logró nada en [MANUAL_FALLBACK_DELAY_MS]. La detección en
 * vivo ya falló dos veces en hardware real en este proyecto, y quedarse sin ninguna salida
 * sería peor que la fricción que vinimos a eliminar. Es una red de seguridad que en el caso
 * feliz nadie ve.
 *
 * La selfie (`LivenessCaptureScreen`) usa la misma idea desde siempre: analiza video, dispara
 * sola.
 *
 * @param onCaptureTaken valida la foto contra el pipeline real. No la acepta: eso lo hace
 * [onScanned] si el resultado da bien.
 * @param onScanned entrega la foto del frente. Toma posesión de los bytes.
 */
@Composable
fun DocumentScanScreen(
    presenceSession: DocumentPresenceSession,
    onCaptureTaken: suspend (image: ByteArray) -> DniFrontQuality,
    onScanned: (frontImage: ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val presence by presenceSession.presence.collectAsStateWithLifecycle()

    var capturing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var rejection by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var manualShutterAvailable by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    // Siempre intercepta (no se deshabilita el handler): mientras se puede cancelar,
    // pide confirmación; una vez en curso la captura, directamente se ignora — no cae al
    // back por defecto de la navegación, que saldría de la pantalla sin avisar.
    BackHandler {
        if (!capturing) showCancelDialog = true
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // SurfaceView (el modo por defecto) puede quedar negro cuando hay otro
            // contenido de Compose dibujado encima, en hardware real.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            // Un documento mal comprimido no se puede leer, ni el código ni la cara.
            // MAXIMIZE_QUALITY además corre la secuencia de pre-captura (disparo de
            // autoenfoque y pre-exposición) antes de sacar la foto.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            // SIN esto CameraX elegía ~1080x1440 (1.5MP): la resolución de la pantalla, no la
            // del sensor. Con el documento a distancia cómoda eso dejaba la foto impresa del
            // titular en un recorte de 135x135px — ruido para el embedder, que hundía la
            // similitud coseno a 0.33 — y el PDF417 muy por debajo de los píxeles por módulo
            // que necesita. Ningún cálculo aguas abajo puede recuperar píxeles que la captura
            // no entregó, así que este selector es la base de todo el pipeline.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    // 4:3 es la relación nativa del sensor: es el modo que aprovecha toda su
                    // superficie, mientras que 16:9 recorta arriba y abajo.
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            .build()
        // NO se fija CONTROL_AF_MODE_CONTINUOUS_PICTURE por Camera2Interop acá, aunque sea
        // tentador: CameraX ya usa ese modo por defecto en el preview, y Camera2Interop tiene
        // MÁS prioridad que CameraX, así que fijarlo a mano rompería tanto el tap-to-focus
        // como el disparo de autoenfoque que corre takePicture() antes de exponer.
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            // Interesa el frame más reciente; acumular retrasaría la detección de quietud.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // El default (640x480) deja la cara impresa del DNI en ~26px a distancia cómoda,
            // por debajo de lo que ML Kit detecta. Acá no hace falta resolución para *leer*,
            // pero sí la suficiente para *ver* las dos geometrías.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 960),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .build()
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        presenceSession.start()

        failure = try {
            val provider = context.awaitCameraProviderInternal()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                // Cerrar ANTES de analizar libera el slot de la cámara enseguida; el frame ya
                // está copiado a NV21. Sin el try/finally, CameraX se queda sin buffers a los
                // pocos frames y el preview también se traba.
                val frame = try {
                    imageProxy.toCameraFrame()
                } finally {
                    imageProxy.close()
                }
                if (frame != null) {
                    // El analyzer corre en su propio hilo y CameraX espera que sea bloqueante.
                    kotlinx.coroutines.runBlocking { presenceSession.submitFrame(frame) }
                }
            }

            // `imageCapture` NO se bindea acá: ver el KDoc de la función. Bindearlo junto con
            // `imageAnalysis` es exactamente lo que degradaba la resolución de la foto.
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
            cameraProvider = provider
            null
        } catch (e: Exception) {
            "No pudimos abrir la cámara. Cerrá otras apps que la estén usando e intentá de nuevo."
        }
    }

    // La key es Unit a propósito, NO `cameraProvider`: ver el comentario equivalente en
    // LivenessCaptureScreen — keyear con un estado que el propio efecto escribe hace que
    // el bind se desvincule solo, un instante después de bindear.
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            presenceSession.close()
        }
    }

    // El anillo que confirma dónde se pidió enfocar se apaga solo.
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(FOCUS_RING_MS)
            focusPoint = null
        }
    }

    // Red de seguridad: si el auto-disparo no logró nada, aparece el obturador manual.
    LaunchedEffect(Unit) {
        delay(MANUAL_FALLBACK_DELAY_MS)
        manualShutterAvailable = true
    }

    fun focusAt(offset: Offset) {
        val activeCamera = camera ?: return
        if (capturing) return

        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
        val action = FocusMeteringAction
            .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        activeCamera.cameraControl.startFocusAndMetering(action)
        focusPoint = offset
    }

    /**
     * Desbindea `imageAnalysis` y bindea `imageCapture` solo (con `preview`, que sigue
     * bindeado sin tocarse). Es el momento en que CameraX recalcula la resolución de captura
     * sin la restricción del tercer stream concurrente.
     */
    fun switchToCaptureMode() {
        val provider = cameraProvider ?: return
        provider.unbind(imageAnalysis)
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)
    }

    /** El camino inverso, para que la detección de presencia retome tras la captura. */
    fun switchToAnalysisMode() {
        val provider = cameraProvider ?: return
        provider.unbind(imageCapture)
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis)
    }

    fun capture() {
        if (capturing) return
        capturing = true
        rejection = null
        // Sin esto la sesión seguiría reportando STEADY y dispararía encima de esta captura.
        presenceSession.pause()
        switchToCaptureMode()

        imageCapture.captureJpegInternal(
            context = context,
            onJpeg = { bytes ->
                coroutineScope.launch {
                    // Se recupera la capacidad de análisis apenas se tiene la foto: la
                    // validación que sigue trabaja sobre los bytes ya capturados, no sobre la
                    // cámara, así que no hay motivo para tenerla desbindeada más tiempo del
                    // necesario.
                    switchToAnalysisMode()

                    val quality = try {
                        onCaptureTaken(bytes)
                    } catch (e: Exception) {
                        DniFrontQuality.BARCODE_UNREADABLE
                    }

                    if (quality.isReadable) {
                        onScanned(bytes)
                    } else {
                        // No se le pide nada al usuario: se le cuenta qué pasó y la app sigue
                        // intentando sola. La espera evita encadenar disparos sobre la misma
                        // escena mala mientras el usuario reacciona.
                        rejection = quality.failureMessage()
                        delay(RETRY_COOLDOWN_MS)
                        capturing = false
                        presenceSession.start()
                    }
                }
            },
            onFailure = {
                switchToAnalysisMode()
                capturing = false
                failure = "No pudimos tomar la foto. Intentá de nuevo."
                presenceSession.start()
            },
        )
    }

    // El disparo automático: la sesión avisa que el documento está quieto y encuadrado.
    LaunchedEffect(presence.isReadyToCapture) {
        if (presence.isReadyToCapture) capture()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { offset -> focusAt(offset) } },
        )

        DocumentGuideOverlay(locked = presence.stage != DocumentPresenceStage.SEARCHING)

        focusPoint?.let { FocusRing(center = it) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancelar",
                onClick = { showCancelDialog = true },
                enabled = !capturing,
            )
            Text(
                text = "Frente del DNI",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (capturing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            }

            Text(
                text = failure ?: rejection ?: presence.stage.instruction(capturing),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    failure != null || rejection != null -> VvOrange
                    presence.stage == DocumentPresenceStage.STEADY -> VvGreen
                    else -> Color.White
                },
                textAlign = TextAlign.Center,
            )

            // Sólo si el auto-disparo no llegó a ninguna parte. En el caso feliz no aparece.
            if (manualShutterAvailable && !capturing) {
                ManualCaptureShutterButton(onClick = ::capture)
            }
        }

        if (showCancelDialog) {
            CancelRegistrationDialog(
                onConfirm = {
                    showCancelDialog = false
                    onCancel()
                },
                onDismiss = { showCancelDialog = false },
            )
        }
    }
}

/** Qué se le dice al usuario según lo que ve la cámara. Nunca "acercate": ver [failureMessage]. */
private fun DocumentPresenceStage.instruction(capturing: Boolean): String = when {
    capturing -> "Tomando la foto, no muevas el documento"
    this == DocumentPresenceStage.SEARCHING ->
        "Mostrá el frente de tu DNI a la distancia que te resulte cómoda, donde se vea nítido"
    this == DocumentPresenceStage.DETECTED -> "Sostené firme un segundo…"
    else -> "¡Listo! Tomando la foto"
}

/** Obturador de respaldo: aparece sólo si el auto-disparo no logró nada. */
@Composable
private fun ManualCaptureShutterButton(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "Capturar DNI",
                tint = VvNavy,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
            )
        }
        Text(
            text = "Capturar ahora",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

/** Anillo que confirma dónde se pidió enfocar. Puramente informativo: se apaga solo. */
@Composable
private fun FocusRing(center: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = Color.White,
            radius = 45f,
            center = center,
            style = Stroke(width = 3f),
        )
    }
}

/**
 * Cuatro esquinas que marcan la zona de lectura, en verde cuando la cámara ya reconoció el
 * documento.
 *
 * **Antes era un rectángulo cerrado, con la proporción exacta de la tarjeta y un velo oscuro
 * alrededor, y eso era un problema de UX real aunque no validara nada.** Un marco de ese tipo
 * comunica "llename", y el usuario obediente se acercaba hasta que el documento lo llenaba —
 * cruzando en el camino la distancia focal mínima del lente y llegando a una foto borrosa. La
 * app rechazaba, el usuario se acercaba todavía más, y el círculo se cerraba. Ninguna
 * validación medía el encuadre: era el dibujo el que daba la instrucción equivocada.
 *
 * Las esquinas sueltas dicen "que entre acá adentro" sin sugerir ningún tamaño, y sin velo no
 * hay un "afuera" que se sienta inválido.
 */
@Composable
private fun DocumentGuideOverlay(locked: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Proporción de tarjeta ID-1 (85.6 x 54 mm) sólo para orientar la forma; el área es
        // generosa a propósito, para que sostener el DNI lejos siga cayendo adentro.
        val width = size.width * 0.92f
        val height = width * (54f / 85.6f)
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val arm = width * 0.09f
        val color = if (locked) VvGreen else Color.White
        val strokeWidth = 5f

        listOf(
            Offset(left, top) to Pair(arm, arm),
            Offset(left + width, top) to Pair(-arm, arm),
            Offset(left, top + height) to Pair(arm, -arm),
            Offset(left + width, top + height) to Pair(-arm, -arm),
        ).forEach { (corner, arms) ->
            val (horizontal, vertical) = arms
            drawLine(color, corner, corner.copy(x = corner.x + horizontal), strokeWidth, StrokeCap.Round)
            drawLine(color, corner, corner.copy(y = corner.y + vertical), strokeWidth, StrokeCap.Round)
        }
    }
}

/** Cuánto se espera antes de ofrecer el obturador manual como red de seguridad. */
private const val MANUAL_FALLBACK_DELAY_MS = 12_000L

/** Pausa tras una foto rechazada, para no encadenar disparos sobre la misma escena mala. */
private const val RETRY_COOLDOWN_MS = 2_500L

private const val FOCUS_RING_MS = 700L
