package com.vinculovial.app.presentation.camera

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
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
import com.vinculovial.app.domain.signup.model.DniFrontQuality
import com.vinculovial.app.presentation.components.VvCircleIconButton
import com.vinculovial.app.presentation.theme.VvNavy
import com.vinculovial.app.presentation.theme.VvOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * Captura del frente del documento — **100% manual, por decisión de producto**.
 *
 * Hubo una versión anterior con detección automática en vivo (`ImageAnalysis` +
 * detección de cara/código sobre el preview), pero el autoenfoque, la resolución del
 * preview y los reflejos sobre el plástico de la tarjeta la hacían fallar de forma
 * inconsistente en hardware real — fragmentación de Android que ninguna heurística
 * visual cubre del todo. Se retiró esa detección en vivo entera (no sólo el auto-disparo)
 * y quedó sólo el marco como guía visual estática: el usuario encuadra y toca el botón.
 *
 * La única pieza interactiva que sí quedó es el tap-to-focus sobre el preview: no es una
 * heurística de encuadre, es control manual de foco (`FocusMeteringAction`), para el caso en
 * que el autoenfoque no converja solo sobre el plástico del documento. No reintroduce nada
 * de lo que se sacó — nadie decide por el usuario si la foto está lista.
 *
 * La foto sí pasa por el pipeline real de todos modos — [onManualCapture] corre PDF417 +
 * detección de cara sobre la imagen ya tomada, así que una foto mala (borrosa, sin cara,
 * sin código) se rechaza igual, sólo que después de una acción explícita del usuario en
 * vez de una heurística en tiempo real.
 *
 * La selfie (`LivenessCaptureScreen`) es la contracara: sigue siendo 100% automática por
 * video, porque ahí sí hace falta seguir un gesto en el tiempo, no una foto sola.
 *
 * @param onManualCapture valida la foto contra el pipeline real. No la acepta: eso lo
 * hace [onScanned] si el resultado da bien.
 * @param onScanned entrega la foto del frente. Toma posesión de los bytes.
 */
@Composable
fun DocumentScanScreen(
    onManualCapture: suspend (image: ByteArray) -> DniFrontQuality,
    onScanned: (frontImage: ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var capturing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
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
            .build()
        // NO se fija CONTROL_AF_MODE_CONTINUOUS_PICTURE por Camera2Interop acá, aunque sea
        // tentador: CameraX ya usa ese modo por defecto en el preview, y Camera2Interop tiene
        // MÁS prioridad que CameraX, así que fijarlo a mano rompería el tap-to-focus —
        // startFocusAndMetering() necesita pasar a CONTROL_AF_MODE_AUTO mientras dura el
        // enfoque manual y volver a continuo al cancelar. Es decir: sería redundante en el
        // mejor caso y regresivo en el peor. (El equipo de CameraX desaconseja explícitamente
        // configurar AF/AE/AWB por interop.) Además, los parámetros camera2 puestos en
        // ImageCapture terminan aplicándose a toda la repeating request, no sólo al disparo.
    }

    // Sin ImageAnalysis: acá no corre ninguna detección en tiempo real sobre el preview,
    // sólo Preview (para que el usuario vea lo que encuadra) e ImageCapture (para la
    // foto que dispara el botón).
    LaunchedEffect(Unit) {
        failure = try {
            val provider = context.awaitCameraProviderInternal()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
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
        onDispose { cameraProvider?.unbindAll() }
    }

    // El anillo que confirma dónde se pidió enfocar se apaga solo, sin que nadie lo dispare
    // a mano en cada punto donde podría cambiar `focusPoint`.
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(700)
            focusPoint = null
        }
    }

    fun focusAt(offset: Offset) {
        // Mientras hay una captura en curso no tiene sentido re-enfocar: la foto ya se tomó
        // o está por tomarse.
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

    fun captureManually() {
        if (capturing) return
        capturing = true
        imageCapture.captureJpegInternal(
            context = context,
            onJpeg = { bytes ->
                coroutineScope.launch {
                    val quality = try {
                        onManualCapture(bytes)
                    } catch (e: Exception) {
                        DniFrontQuality.BARCODE_UNREADABLE
                    }
                    if (quality.isReadable) {
                        onScanned(bytes)
                    } else {
                        capturing = false
                        Toast.makeText(context, quality.failureMessage(), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onFailure = {
                capturing = false
                failure = "No pudimos tomar la foto. Intentá de nuevo."
            },
        )
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

        DocumentGuideOverlay()

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
                text = failure
                    ?: "Poné el frente de tu DNI a la distancia que te resulte cómoda, " +
                    "donde se vea nítido. Tocá la pantalla para enfocar.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (failure != null) VvOrange else Color.White,
                textAlign = TextAlign.Center,
            )

            // Se reemplaza por el spinner de arriba ni bien hay una captura en curso,
            // en vez de quedar deshabilitado pero visible.
            if (!capturing) {
                ManualCaptureShutterButton(onClick = ::captureManually)
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

/** Botón obturador, único disparador de la captura del frente. */
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
            text = "Capturar DNI",
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
 * Cuatro esquinas que marcan la zona de lectura. Guía visual estática — no reacciona a
 * ninguna detección en tiempo real, ya no hay ninguna corriendo sobre el preview.
 *
 * **Antes era un rectángulo cerrado, con la proporción exacta de la tarjeta y un velo oscuro
 * alrededor, y eso era un problema de UX real aunque no validara nada.** Un marco de ese tipo
 * comunica "llename", y el usuario obediente se acercaba hasta que el documento lo llenaba —
 * cruzando en el camino la distancia focal mínima del lente y llegando a una foto borrosa. La
 * app rechazaba, el usuario se acercaba todavía más, y el círculo se cerraba. Ninguna
 * validación medía el encuadre: era el dibujo el que daba la instrucción equivocada.
 *
 * Las esquinas sueltas dicen "que entre acá adentro" sin sugerir ningún tamaño, y sin velo no
 * hay un "afuera" que se sienta inválido. Es todo lo que hace falta: la única condición real
 * es que el código de barras y la foto entren en el cuadro y estén enfocados.
 */
@Composable
private fun DocumentGuideOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Proporción de tarjeta ID-1 (85.6 x 54 mm) sólo para orientar la forma; el área es
        // generosa a propósito, para que sostener el DNI lejos siga cayendo adentro.
        val width = size.width * 0.92f
        val height = width * (54f / 85.6f)
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val arm = width * 0.09f
        val stroke = Stroke(width = 5f, cap = StrokeCap.Round)

        listOf(
            Offset(left, top) to Pair(arm, arm),
            Offset(left + width, top) to Pair(-arm, arm),
            Offset(left, top + height) to Pair(arm, -arm),
            Offset(left + width, top + height) to Pair(-arm, -arm),
        ).forEach { (corner, arms) ->
            val (horizontal, vertical) = arms
            drawLine(Color.White, corner, corner.copy(x = corner.x + horizontal), stroke.width, stroke.cap)
            drawLine(Color.White, corner, corner.copy(y = corner.y + vertical), stroke.width, stroke.cap)
        }
    }
}
