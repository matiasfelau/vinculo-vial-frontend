package com.vinculovial.app.data.edge.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedder facial sobre TensorFlow Lite, pensado para modelos tipo MobileFaceNet.
 *
 * **El modelo no viaja en el repositorio.** Hay que dejar el `.tflite` en
 * `app/src/main/assets/` con el nombre que indique `FACE_EMBEDDER_MODEL_ASSET`. Sin él,
 * [isAvailable] devuelve `false` y la verificación biométrica falla de forma explícita:
 * jamás se da por válido un match que no se pudo calcular.
 *
 * Las dimensiones no están hardcodeadas: se leen del propio modelo, así que sirve
 * cualquier variante (112x112 → 192-d de MobileFaceNet, 160x160 → 512-d de FaceNet)
 * sin tocar código.
 */
@Singleton
class TfLiteFaceEmbedder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelAssetName: String,
    private val inputScale: Float,
    private val inputMean: Float,
) : FaceEmbedder {

    private data class Engine(
        val interpreter: Interpreter,
        val inputSize: Int,
        val embeddingSize: Int,
    )

    /**
     * Se construye una sola vez y en forma perezosa: cargar el intérprete cuesta y
     * puede no llegar a necesitarse si el usuario abandona el registro.
     *
     * El fallo se loguea acá, en el único punto donde se conoce la excepción real: hacia
     * afuera sólo viaja `BiometricsUnavailable` (nunca se filtra detalle técnico a la UI,
     * como con cualquier otro error de US-01), así que sin este log un asset mal
     * nombrado, un modelo con la forma equivocada o un archivo corrupto son
     * indistinguibles entre sí para quien lo esté depurando.
     */
    private val engine: Result<Engine> by lazy {
        runCatching { loadEngine() }.onFailure {
            Log.e(TAG, "No se pudo inicializar el modelo de reconocimiento facial ($modelAssetName)", it)
        }
    }

    override fun isAvailable(): Boolean = engine.isSuccess

    override fun embed(faceCrop: Bitmap): FloatArray {
        val engine = engine.getOrElse {
            throw FaceEmbedderUnavailableException(
                "No se pudo inicializar el modelo de reconocimiento facial ($modelAssetName)",
                it,
            )
        }

        val scaled = faceCrop.scale(engine.inputSize, engine.inputSize)
        val output = Array(1) { FloatArray(engine.embeddingSize) }

        try {
            engine.interpreter.run(scaled.toModelInput(engine.inputSize), output)
        } finally {
            // El recorte escalado es otra copia de la cara: no sobrevive a esta llamada.
            if (scaled !== faceCrop) scaled.recycle()
        }

        return output[0].l2Normalized()
    }

    private fun loadEngine(): Engine {
        val interpreter = Interpreter(loadModel(), Interpreter.Options().apply { numThreads = 2 })

        // [1, alto, ancho, canales] y [1, dimensiones]
        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()

        check(inputShape.size == 4 && inputShape[1] == inputShape[2]) {
            "Se esperaba una entrada cuadrada NHWC, y el modelo declara ${inputShape.joinToString()}"
        }
        check(outputShape.size == 2 && outputShape[1] > 0) {
            "Se esperaba una salida [1, N], y el modelo declara ${outputShape.joinToString()}"
        }

        return Engine(
            interpreter = interpreter,
            inputSize = inputShape[1],
            embeddingSize = outputShape[1],
        )
    }

    /** Mapea el modelo desde el APK sin copiarlo a memoria; por eso no se comprime. */
    private fun loadModel(): ByteBuffer {
        val descriptor = context.assets.openFd(modelAssetName)
        return FileInputStream(descriptor.fileDescriptor).use { stream ->
            stream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength,
            )
        }
    }

    /**
     * Normaliza los píxeles al rango que espera el modelo. Para MobileFaceNet la
     * convención habitual es `(pixel - 127.5) / 128`.
     */
    private fun Bitmap.toModelInput(size: Int): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(FLOAT_BYTES * size * size * CHANNELS)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(size * size)
        getPixels(pixels, 0, size, 0, 0, size, size)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16 and 0xFF) - inputMean) / inputScale)
            buffer.putFloat(((pixel shr 8 and 0xFF) - inputMean) / inputScale)
            buffer.putFloat(((pixel and 0xFF) - inputMean) / inputScale)
        }

        // Los píxeles de la cara viven en este array: se limpia antes de soltarlo.
        pixels.fill(0)
        buffer.rewind()
        return buffer
    }

    private companion object {
        const val TAG = "TfLiteFaceEmbedder"
        const val FLOAT_BYTES = 4
        const val CHANNELS = 3
    }
}
