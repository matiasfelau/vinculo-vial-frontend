package ar.felaufernandezbottaro.vinculovial.presentation.camera

import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.felaufernandezbottaro.vinculovial.domain.signup.DocumentScanSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.ZoomController
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentScanStage
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvCircleIconButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvGreen
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvOrange
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Escaneo del DNI: **en vivo, sin obturador y sin foto**.
 *
 * ## Qué reemplaza, y por qué
 *
 * La versión anterior era híbrida: `ImageAnalysis` miraba si había un código y una cara y,
 * cuando detectaba que el usuario se había quedado quieto, disparaba una foto de alta
 * resolución con `ImageCapture` que era la que efectivamente se decodificaba. Funcionaba,
 * pero le pedía al usuario algo que no debería tener que pensar —congelar el pulso— y el
 * momento del disparo se sentía arbitrario.
 *
 * Dos cosas permiten sacarla entera:
 *
 * 1. **Del documento ya no se necesita una imagen.** Sólo `dni`/`sexo`/`tramite`, que están
 *    en la cadena del código. La foto impresa dejó de usarse cuando la comparación facial se
 *    movió al backend (pivote RENAPER-Ready), así que la única razón para sacar una foto era
 *    tener suficientes píxeles para decodificar.
 * 2. **El auto-zoom de ML Kit resuelve esos píxeles ópticamente.** Ver
 *    [DocumentScanSession]: cuando el escáner ve un código que no puede leer por lejanía,
 *    pide el zoom exacto que lo haría legible y la cámara se acerca sola.
 *
 * Resultado: sólo `Preview` + `ImageAnalysis` bindeados. Sin `ImageCapture`, se va también
 * todo lo que existía para sostenerlo — la topología de streams alternada, el margen de
 * asentamiento de la sesión de Camera2, el obturador manual de respaldo y el diagnóstico de
 * resolución del sensor. Ninguno de esos problemas puede volver a ocurrir porque el caso de
 * uso que los causaba ya no está.
 *
 * ## Zero Retention
 *
 * Del documento no se guarda ni se transmite ninguna imagen. Los cuadros se analizan y se
 * sueltan; lo único que sobrevive al escaneo son tres campos de texto.
 */
@Composable
fun DocumentScanScreen(
    scanSession: DocumentScanSession,
    onScanned: (document: DocumentIdentity) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scanState by scanSession.state.collectAsStateWithLifecycle()

    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    SecureScreen()
    BackHandler(onBack = onCancel)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // SurfaceView (el modo por defecto) puede quedar negro cuando hay otro
            // contenido de Compose dibujado encima, en hardware real.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            // Interesa el cuadro más reciente: acumular sólo agregaría latencia al escaneo.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // Más resolución que el default de 640x480 le da al decodificador más píxeles
            // por módulo de arranque, así el auto-zoom tiene que trabajar menos (y en
            // muchos casos no tiene que intervenir en absoluto).
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
        scanSession.start()

        failure = try {
            val provider = context.awaitCameraProviderInternal()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                // Cerrar ANTES de analizar libera el slot de la cámara enseguida; el cuadro
                // ya está copiado a NV21. Sin el try/finally, CameraX se queda sin buffers a
                // los pocos cuadros y el preview también se traba.
                val frame = try {
                    imageProxy.toCameraFrame()
                } finally {
                    imageProxy.close()
                }
                if (frame != null) {
                    // El analyzer corre en su propio hilo y CameraX espera que sea bloqueante.
                    kotlinx.coroutines.runBlocking { scanSession.submitFrame(frame) }
                }
            }

            provider.unbindAll()
            val bound = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
            camera = bound
            cameraProvider = provider

            // ML Kit maneja el zoom por su cuenta a partir de acá. Se acota al máximo real
            // del dispositivo: pedir más de lo que el hardware soporta lo dejaría sin
            // aplicar y el escáner esperaría un acercamiento que nunca llega.
            val maxZoom = bound.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
            scanSession.attachZoomController(
                ZoomController { ratio ->
                    if (ratio > maxZoom) {
                        false
                    } else {
                        bound.cameraControl.setZoomRatio(ratio)
                        true
                    }
                },
            )
            null
        } catch (e: Exception) {
            "No pudimos abrir la cámara. Cerrá otras apps que la estén usando e intentá de nuevo."
        }
    }

    // La key es Unit a propósito, NO `cameraProvider`: keyear con un estado que el propio
    // efecto escribe hace que el bind se desvincule solo, un instante después de bindear.
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            scanSession.close()
        }
    }

    LaunchedEffect(torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    // Confirmación háptica en el instante del reconocimiento, y una pausa corta antes de
    // navegar: sin ella el cambio de pantalla se siente como un salto sin causa.
    LaunchedEffect(scanState.isDone) {
        val document = scanState.document
        if (scanState.isDone && document != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(SUCCESS_HOLD_MS)
            onScanned(document)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        ScannerViewfinder(stage = scanState.stage)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            VvCircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancelar",
                onClick = onCancel,
            )
            Text(
                text = "Escaneá el código de tu DNI",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
            )
            // La linterna es la única acción manual que queda, y la que de verdad sirve:
            // el plástico del DNI refleja, y con poca luz el código se pierde.
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                VvCircleIconButton(
                    icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (torchOn) "Apagar la luz" else "Encender la luz",
                    onClick = { torchOn = !torchOn },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = failure ?: scanState.stage.headline(),
                style = MaterialTheme.typography.headlineSmall,
                color = when {
                    failure != null -> VvOrange
                    scanState.stage == DocumentScanStage.DECODED -> VvGreen
                    else -> Color.White
                },
                textAlign = TextAlign.Center,
            )

            // El detalle sólo aparece cuando hay algo accionable que decir, para que el
            // estado normal quede limpio en vez de saturado de instrucciones.
            AnimatedVisibility(
                visible = failure == null && scanState.stage.detail() != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = scanState.stage.detail().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Nunca "acercate": el auto-zoom lo resuelve solo, y pedirlo empujaría al usuario a cruzar
 * la distancia mínima de enfoque del lente — el círculo vicioso que ya costó caro antes.
 *
 * Los estados en los que el cuadro leído no resultó válido (no es un DNI, un formato que
 * todavía no se lee, o falta el número de trámite) se muestran igual que "buscando": son
 * lecturas transitorias del stream en vivo, no un veredicto sobre lo que el usuario está
 * mostrando, y anunciarlas como error generaba falsos positivos que confundían más de lo
 * que ayudaban.
 */
private fun DocumentScanStage.headline(): String = when (this) {
    DocumentScanStage.SEARCHING,
    DocumentScanStage.NOT_A_DNI,
    DocumentScanStage.UNSUPPORTED_DOCUMENT,
    DocumentScanStage.MISSING_PROCEDURE_NUMBER,
    -> "Apuntá al código de tu DNI"
    DocumentScanStage.CODE_DETECTED -> "Enfocando…"
    DocumentScanStage.DECODED -> "¡Listo!"
}

private fun DocumentScanStage.detail(): String? = when (this) {
    DocumentScanStage.SEARCHING,
    DocumentScanStage.NOT_A_DNI,
    DocumentScanStage.UNSUPPORTED_DOCUMENT,
    DocumentScanStage.MISSING_PROCEDURE_NUMBER,
    -> "Sostenelo a una distancia cómoda, donde se vea nítido"
    DocumentScanStage.CODE_DETECTED, DocumentScanStage.DECODED -> null
}

/**
 * Marco de escaneo: esquinas que reaccionan al estado, y una línea de barrido mientras
 * busca.
 *
 * Sin velo oscuro ni rectángulo cerrado, a propósito: un marco que se "llena" comunica que
 * el documento tiene que ocupar todo el recuadro, y eso hacía que el usuario se acercara
 * hasta perder el foco. Acá el encuadre no exige tamaño — con auto-zoom, apuntar alcanza.
 */
@Composable
private fun ScannerViewfinder(stage: DocumentScanStage) {
    val transition = rememberInfiniteTransition(label = "scanner")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    val target = if (stage == DocumentScanStage.DECODED) VvGreen else Color.White
    val cornerColor by androidx.compose.animation.animateColorAsState(
        targetValue = target,
        animationSpec = tween(220),
        label = "corners",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Proporción de tarjeta ID-1 (85.6 x 54 mm) sólo para orientar la forma; el área es
        // generosa para que apuntar de lejos siga cayendo adentro.
        val width = size.width * 0.86f
        val height = width * (54f / 85.6f)
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val arm = width * 0.09f
        val strokeWidth = 5f

        listOf(
            Offset(left, top) to Pair(arm, arm),
            Offset(left + width, top) to Pair(-arm, arm),
            Offset(left, top + height) to Pair(arm, -arm),
            Offset(left + width, top + height) to Pair(-arm, -arm),
        ).forEach { (corner, arms) ->
            val (horizontal, vertical) = arms
            drawLine(cornerColor, corner, corner.copy(x = corner.x + horizontal), strokeWidth, StrokeCap.Round)
            drawLine(cornerColor, corner, corner.copy(y = corner.y + vertical), strokeWidth, StrokeCap.Round)
        }

        // La línea de barrido sólo mientras busca: dejarla en los estados resueltos haría
        // parecer que la app sigue trabajando cuando ya terminó.
        if (stage == DocumentScanStage.SEARCHING || stage == DocumentScanStage.CODE_DETECTED) {
            val y = top + height * sweep
            drawLine(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to cornerColor.copy(alpha = 0.85f),
                    1f to Color.Transparent,
                    startX = left,
                    endX = left + width,
                ),
                start = Offset(left, y),
                end = Offset(left + width, y),
                strokeWidth = 3f,
            )
        }
    }
}

/** Cuánto se sostiene el estado de éxito antes de navegar, para que no se sienta un salto. */
private const val SUCCESS_HOLD_MS = 450L
