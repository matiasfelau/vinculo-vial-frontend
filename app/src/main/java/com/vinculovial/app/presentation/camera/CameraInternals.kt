package com.vinculovial.app.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Piezas de CameraX compartidas por la captura de documento y la de selfie con liveness.
 *
 * Ambas entregan la foto **en memoria**: se usa
 * `takePicture(executor, OnImageCapturedCallback)` y no
 * `ActivityResultContracts.TakePicture`, porque ese contrato exige una URI de destino y
 * dejaría la imagen escrita en el almacenamiento, rompiendo Zero Retention.
 */
internal fun ImageCapture.captureJpegInternal(
    context: Context,
    onJpeg: (ByteArray) -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {

            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    image.use { proxy ->
                        if (proxy.format != ImageFormat.JPEG) {
                            onFailure(IllegalStateException("Formato inesperado: ${proxy.format}"))
                        } else {
                            // OnImageCapturedCallback entrega el JPEG SIN rotar: el
                            // sensor suele estar montado apaisado, y lo que dice cuánto
                            // rotar para que quede vertical es imageInfo.rotationDegrees,
                            // no el contenido del archivo (acá no hay garantía de EXIF,
                            // a diferencia de la variante que guarda a un OutputFileOptions).
                            // Se corrige acá, una única vez: de acá en más todo el
                            // pipeline (código de barras, cara) trabaja sobre una imagen
                            // ya vertical, sin que nadie más tenga que pensarlo.
                            val upright = proxy.readJpegBytesInternal()
                                .rotatedJpeg(proxy.imageInfo.rotationDegrees)
                            onJpeg(upright)
                        }
                    }
                } catch (e: Exception) {
                    onFailure(e)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onFailure(exception)
            }
        },
    )
}

/** Para un JPEG, el primer plano contiene la imagen completa ya comprimida. */
private fun ImageProxy.readJpegBytesInternal(): ByteArray {
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also(buffer::get)
}

/**
 * Deja el JPEG genuinamente vertical, acotando de paso cuánta memoria ocupa.
 *
 * Se rotan los píxeles en vez de pasarle el ángulo a `InputImage.fromBitmap(bitmap, deg)`
 * a propósito: ese parámetro tiene reportes conocidos de comportarse de forma inconsistente
 * en detección de caras/texto según el modelo de ML Kit. Rotar acá deja el bitmap
 * genuinamente vertical, así el resto del pipeline siempre pasa rotación 0 y no depende
 * de esa API.
 *
 * **Cuánta resolución se conserva es lo que decide si el PDF417 se lee o no.** Un decodificador
 * de códigos de barras necesita del orden de 2-3 píxeles por módulo (la barra más angosta);
 * por debajo de eso el código es irrecuperable por más nítida y bien iluminada que esté la
 * foto. En un encuadre típico el PDF417 ocupa cerca de la mitad del ancho de la tarjeta, así
 * que recortar el lado largo a ~1000px deja el código al borde de lo indecodificable. De ahí
 * que el tope de acá sea alto: sólo existe para que un sensor de 50MP no materialice un bitmap
 * de 200MB, no para "limpiar" la imagen.
 *
 * El moiré de las líneas de seguridad sobre el código es un problema real, pero se resuelve
 * probando varias escalas al decodificar (ver `MlKitDniBarcodeReader`), no tirando resolución
 * de entrada: bajar la resolución acá le saca al decodificador la única materia prima que no
 * puede recuperar después.
 */
private fun ByteArray.rotatedJpeg(rotationDegrees: Int): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    val sampleSize = sampleSizeForCapture(bounds.outWidth, bounds.outHeight)

    // Nada para hacer: ya está vertical y entra holgado. Se devuelven los bytes originales
    // en vez de recomprimirlos, que sólo agregaría artefactos de JPEG sobre el código.
    if (rotationDegrees == 0 && sampleSize == 1) return this

    val original = BitmapFactory
        .decodeByteArray(this, 0, size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?: return this

    val upright = if (rotationDegrees == 0) {
        original
    } else {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            .also { original.recycle() }
    }

    return try {
        ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    } finally {
        upright.recycle()
    }
}

/**
 * Potencia de dos que mantiene el lado mayor **por encima** de [MAX_CAPTURE_DIMENSION_PX].
 *
 * El `while` corta antes de cruzar el umbral, no después: `inSampleSize` sólo admite
 * potencias de dos, y bajar un escalón de más significa quedarse con la mitad de la
 * resolución de la que se pidió — que es exactamente cómo se pierde un PDF417.
 */
private fun sampleSizeForCapture(width: Int, height: Int): Int {
    val largestSide = maxOf(width, height)
    if (largestSide <= 0) return 1

    var sample = 1
    while (largestSide / (sample * 2) >= MAX_CAPTURE_DIMENSION_PX) {
        sample *= 2
    }
    return sample
}

// Calidad alta a propósito: el JPEG se recodifica una sola vez y los artefactos de
// compresión caen justo sobre los bordes de alto contraste del código de barras.
private const val JPEG_QUALITY = 95

/**
 * Piso de resolución del lado mayor. No es un tope exacto — `inSampleSize` trabaja por
 * potencias de dos, así que el resultado queda entre este valor y su doble.
 *
 * **Este número es lo que decide a qué distancia se puede sostener el documento.** La cuenta,
 * para un DNI leído a ~25cm (donde cualquier lente enfoca sin esfuerzo): a esa distancia la
 * tarjeta ocupa del orden del 27% del ancho del cuadro, y el PDF417 alrededor de la mitad de
 * la tarjeta, o sea ~13% del ancho. Con ~205 módulos y un mínimo de ~2.5 px por módulo hacen
 * falta unos 500px sobre el código, lo que exige un cuadro de ~3800px de ancho. Con el valor
 * anterior (2000) el código recibía la mitad de los píxeles que necesitaba y **era imposible
 * de leer salvo pegando la cámara al plástico** — justo la distancia a la que el lente ya no
 * enfoca. Ese era el círculo vicioso, y era de resolución, no de encuadre.
 *
 * En 3000 un sensor de 12MP (4000x3000) pasa entero sin submuestrear, y uno de 50MP se acota
 * a 4000px. El bitmap resultante ronda los 48MB: es mucho, pero es una única imagen fija, de
 * vida corta, y desde API 26 vive en el heap nativo.
 */
private const val MAX_CAPTURE_DIMENSION_PX = 3000

/** `ProcessCameraProvider.getInstance` expone un `ListenableFuture`; acá se espera sin bloquear. */
internal suspend fun Context.awaitCameraProviderInternal(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
