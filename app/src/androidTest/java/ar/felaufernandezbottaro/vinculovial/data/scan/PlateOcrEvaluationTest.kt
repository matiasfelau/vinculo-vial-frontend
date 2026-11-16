package ar.felaufernandezbottaro.vinculovial.data.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Medición de exactitud del OCR de la app sobre un set de recortes de patentes. No es una prueba
 * de regresión: no afirma ninguna tasa de acierto, sólo exporta un CSV para analizarlo afuera.
 *
 * Corre cada recorte por el mismo reconocimiento que usa la app, sin la parte de cámara
 * (`FrameCropper` no interviene: los recortes ya vienen recortados):
 * `MLKitRecognitionStrategy.recognize` -> `PlateRegexFilter.filter`. Ninguno de los dos se
 * modifica ni se reimplementa acá.
 *
 * El set NO está en el repo. Se empuja con `adb push` a `/data/local/tmp` y se copia a la
 * carpeta interna de la app con `run-as` (el almacenamiento externo no es visible bajo
 * `am instrument` en Android 11+). La prueba lo lee de `filesDir/ocr-eval/in/`:
 *
 *     indice.csv      id,formato,luz
 *     <id>.png        un recorte por fila del índice
 *
 * y escribe en `filesDir/ocr-eval/out/` (se baja con `run-as ... cat`):
 *
 *     ocr-results.csv id,formato,luz,lectura,candidatos,confianza,tiempo_ms
 *     ocr-device.txt  modelo del dispositivo, versión de Android y cantidad de recortes
 *
 * Columnas del CSV:
 *  - `lectura`: lo que devuelve `PlateRegexFilter` (patente normalizada) o vacío si ninguna línea
 *    tiene forma de patente.
 *  - `candidatos`: las líneas crudas que devolvió ML Kit, separadas por `|`.
 *  - `confianza`: confianza de ML Kit para la línea de la que salió `lectura` (vacía si no hubo
 *    lectura). La app no la usa y su estrategia no la expone, así que se obtiene con una segunda
 *    pasada de ML Kit, fuera de la medición de tiempo.
 *  - `tiempo_ms`: `recognize` + `filter`, medido con `System.nanoTime`, sin decodificar el PNG.
 *
 * Las patentes del set aparecen en el CSV porque es el producto de la medición; esta prueba no
 * loguea ninguna. (El log de depuración de `PlateRegexFilter`, ya existente, sí imprime los
 * candidatos crudos en `debug`.)
 */
@RunWith(AndroidJUnit4::class)
class PlateOcrEvaluationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val baseDir = File(context.filesDir, "ocr-eval")
    private val inDir = File(baseDir, "in")
    private val outDir = File(baseDir, "out")

    @Test
    fun evaluaElSetDeRecortes() = runBlocking {
        val index = readIndex()
        assertTrue("indice.csv sin filas en ${inDir.path}", index.isNotEmpty())
        val missing = index.filterNot { File(inDir, "${it.id}.png").isFile }
        assertTrue("Faltan ${missing.size} PNG listados en indice.csv, p. ej. ${missing.take(3).map { it.id }}", missing.isEmpty())

        awaitModelReady(File(inDir, "${index.first().id}.png"))

        val strategy = MLKitRecognitionStrategy()
        val filter = PlateRegexFilter()
        val confidenceClient = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Calentamiento: las primeras inferencias pagan la inicialización del modelo y del
        // delegado; sin esto sesgarían hacia arriba el tiempo de los primeros recortes.
        decode(File(inDir, "${index.first().id}.png")).let { warm ->
            repeat(WARMUP_RUNS) { filter.filter(strategy.recognize(warm)) }
            warm.recycle()
        }

        outDir.mkdirs()
        val rows = StringBuilder("id,formato,luz,lectura,candidatos,confianza,tiempo_ms\n")
        for (entry in index) {
            val bitmap = decode(File(inDir, "${entry.id}.png"))

            val t0 = System.nanoTime()
            val candidates = strategy.recognize(bitmap)
            val plate = filter.filter(candidates)
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0

            val confidence = plate?.let { lineConfidence(confidenceClient, bitmap, it) }
            bitmap.recycle()

            rows.append(
                listOf(
                    entry.id,
                    entry.formato,
                    entry.luz,
                    plate.orEmpty(),
                    candidates.joinToString("|"),
                    confidence?.let { String.format(Locale.ROOT, "%.4f", it) }.orEmpty(),
                    String.format(Locale.ROOT, "%.1f", elapsedMs),
                ).joinToString(",") { csv(it) },
            ).append('\n')
        }
        confidenceClient.close()

        File(outDir, "ocr-results.csv").writeText(rows.toString())
        File(outDir, "ocr-device.txt").writeText(
            "modelo=${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "recortes=${index.size}\n" +
                "warmup=$WARMUP_RUNS\n",
        )
    }

    /**
     * La estrategia de la app traga las excepciones y devuelve lista vacía, así que un modelo de
     * ML Kit aún no descargado (el artefacto es "unbundled", lo sirve Play Services) se vería como
     * "ningún recorte tiene texto". Se reintenta con un cliente propio hasta que el modelo
     * responda, para que ese caso falle fuerte acá en vez de contaminar el CSV.
     */
    private suspend fun awaitModelReady(sample: File) {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val bitmap = decode(sample)
        var lastError: Exception? = null
        val deadline = System.nanoTime() + MODEL_READY_TIMEOUT_MS * 1_000_000
        while (System.nanoTime() < deadline) {
            try {
                client.process(InputImage.fromBitmap(bitmap, 0)).await()
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
                delay(MODEL_READY_RETRY_MS)
            }
        }
        bitmap.recycle()
        client.close()
        lastError?.let {
            throw AssertionError(
                "ML Kit no quedó listo en ${MODEL_READY_TIMEOUT_MS / 1000}s (¿Play Services y red para bajar el modelo?)",
                it,
            )
        }
    }

    private suspend fun lineConfidence(
        client: TextRecognizer,
        bitmap: Bitmap,
        plate: String,
    ): Float? {
        val result = try {
            client.process(InputImage.fromBitmap(bitmap, 0)).await()
        } catch (e: Exception) {
            return null
        }
        return result.textBlocks.flatMap { it.lines }
            .firstOrNull { it.text.trim().uppercase().replace(NOISE, "") == plate }
            ?.confidence
    }

    private fun decode(file: File) =
        requireNotNull(BitmapFactory.decodeFile(file.path)) { "No se pudo decodificar ${file.name}" }

    private data class Entry(val id: String, val formato: String, val luz: String)

    private fun readIndex(): List<Entry> {
        val file = File(inDir, "indice.csv")
        assertTrue("No existe ${file.path}: copiá el set a filesDir con run-as (ver KDoc)", file.isFile)
        return file.readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val cols = line.trim().split(",")
                Entry(id = cols[0], formato = cols.getOrElse(1) { "" }, luz = cols.getOrElse(2) { "" })
            }
    }

    private fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""

    private companion object {
        const val WARMUP_RUNS = 3
        const val MODEL_READY_TIMEOUT_MS = 90_000L
        const val MODEL_READY_RETRY_MS = 2_000L
        val NOISE = Regex("[\\s-]")
    }
}
