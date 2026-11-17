package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Copiar el código al portapapeles y abrir al partner: las dos salidas del detalle hacia el sistema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BenefitActionsTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val actions = BenefitActions(context)

    @Test
    fun `copiar deja el codigo en el portapapeles`() {
        actions.copyCode("ABCD-1234")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("ABCD-1234", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun `copiar marca el contenido como sensible`() {
        actions.copyCode("ABCD-1234")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val extras = clipboard.primaryClip?.description?.extras
        assertTrue(extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
    }

    @Test
    fun `copiar de nuevo reemplaza lo anterior`() {
        actions.copyCode("PRIMERO")
        actions.copyCode("SEGUNDO")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("SEGUNDO", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun `abrir lanza ACTION_VIEW sobre el open_url https`() {
        val opened = actions.open("https://partner.example/app?campania=1")

        assertTrue(opened)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://partner.example/app?campania=1", intent.data.toString())
    }

    @Test
    fun `abrir nunca lanza un esquema que no sea https`() {
        listOf(
            "http://partner.example/app",
            "intent://scan/#Intent;scheme=zxing;end",
            "javascript:alert(1)",
            "content://contacts/people",
            "file:///sdcard/x",
            "https://",
            "no es una url",
        ).forEach { url ->
            assertFalse(url, actions.open(url))
        }

        assertNull(shadowOf(context).nextStartedActivity)
    }
}
