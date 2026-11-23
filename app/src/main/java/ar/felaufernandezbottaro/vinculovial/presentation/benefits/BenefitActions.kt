package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle

/**
 * Las dos salidas del detalle hacia el resto del sistema: copiar el código al portapapeles y abrir
 * la app (o la ficha de Play Store) del partner. Es un adaptador fino sobre `Context`; las
 * decisiones (qué se ofrece, cuándo) viven en [ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit]
 * y en el ViewModel.
 */
class BenefitActions(private val context: Context) {

    /**
     * Copia [code] como un dato sensible (Android 13 o superior lo oculta en la vista previa del
     * portapapeles). Nunca se loguea.
     */
    fun copyCode(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(CLIP_LABEL, code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }

    /**
     * `ACTION_VIEW` sobre [url]. Solo `https://`: cualquier otro esquema no se abre. `false` si no
     * se abrió (esquema no permitido o ninguna app que lo resuelva).
     */
    fun open(url: String): Boolean {
        val uri = Uri.parse(url)
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return false

        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            // Fuera de una Activity (por ejemplo un contexto de aplicación) Android exige una tarea nueva.
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val CLIP_LABEL = "Código de descuento"
    }
}
