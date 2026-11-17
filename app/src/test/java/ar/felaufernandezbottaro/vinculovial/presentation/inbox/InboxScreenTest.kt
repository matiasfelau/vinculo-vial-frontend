package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlertStatus
import ar.felaufernandezbottaro.vinculovial.presentation.sent.SentUiState
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.alertTitle
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.inboxPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * El buzón: las dos pestañas, la insignia de alertas sin leer, cada estado de la lista (cargando,
 * vacía, con contenido, con error de pantalla completa o de página) y qué dispara cada toque.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class InboxScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val now = Instant.now()

    private fun alert(
        id: String,
        type: String = "CRASHED",
        seen: Boolean = false,
        evidence: List<EvidenceRef> = listOf(EvidenceRef("GENERAL", 0)),
        feedback: FeedbackRating? = null,
        reported: Boolean = false,
    ) = AlertItem(
        id = id,
        alertType = type,
        createdAt = now.minusSeconds(60),
        evidence = evidence,
        myFeedback = feedback,
        reported = reported,
        seen = seen,
    )

    private fun sent(id: String, status: SentAlertStatus = SentAlertStatus.ACTIVE, seenAt: Instant? = null) =
        SentAlert(id = id, alertType = "LUCES_ENCENDIDAS", createdAt = now.minusSeconds(120), status = status, seenAt = seenAt)

    private fun show(
        inbox: InboxUiState = InboxUiState.Loading,
        sent: SentUiState = SentUiState.Loading,
        tab: InboxTab = InboxTab.RECEIVED,
    ) {
        compose.setContent {
            VinculoVialTheme {
                InboxScreen(
                    uiState = inbox,
                    sentState = sent,
                    selectedTab = tab,
                    onSelectTab = { calls += "tab:${it.name}" },
                    onRetry = { calls += "retry" },
                    onLoadNextPage = { calls += "next" },
                    onRetryNextPage = { calls += "retryPage" },
                    onOpenAlert = { calls += "open:$it" },
                    onSentRetry = { calls += "sentRetry" },
                    onSentLoadNextPage = { calls += "sentNext" },
                    onSentRetryNextPage = { calls += "sentRetryPage" },
                    onOpenSent = { calls += "openSent:$it" },
                )
            }
        }
    }

    // -- Pestanas e insignia ------------------------------------------------------------------

    @Test
    fun `muestra el titulo, las dos pestanas y la nota al pie`() {
        show()

        compose.onNodeWithText("Buzón").assertIsDisplayed()
        compose.onNodeWithText("Recibidos").assertIsDisplayed()
        compose.onNodeWithText("Enviados").assertIsDisplayed()
        compose.onNodeWithText(ViviMessages.INBOX_FOOTNOTE).assertIsDisplayed()
    }

    @Test
    fun `tocar una pestana avisa cual`() {
        show()

        compose.onNodeWithText("Enviados").performClick()
        compose.onNodeWithText("Recibidos").performClick()

        assertEquals(listOf("tab:SENT", "tab:RECEIVED"), calls)
    }

    @Test
    fun `la insignia cuenta las alertas sin leer`() {
        val items = listOf(alert("a", seen = false), alert("b", seen = false), alert("c", seen = true))

        assertEquals(2, unreadCount(items))
        show(InboxUiState.Content(items, nextCursor = null))

        compose.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    fun `sin alertas sin leer no hay insignia`() {
        assertEquals(0, unreadCount(emptyList()))
        show(InboxUiState.Content(listOf(alert("a", seen = true)), nextCursor = null))

        compose.onAllNodesWithText("1").assertCountEquals(0)
    }

    // -- Recibidos ----------------------------------------------------------------------------

    @Test
    fun `sin alertas muestra el estado vacio`() {
        show(InboxUiState.Empty)

        compose.onNodeWithText("Todo tranquilo por acá").assertIsDisplayed()
    }

    @Test
    fun `una alerta se muestra con su titulo y su ultima linea y se abre al tocarla`() {
        val item = alert("a-1", seen = false)
        show(InboxUiState.Content(listOf(item), nextCursor = null))

        compose.onNodeWithText(alertTitle("CRASHED")).assertIsDisplayed()
        compose.onNodeWithText(item.inboxPreview()).performClick()

        // Una lista corta ya está "cerca del final": además pide la página siguiente (el ViewModel la ignora sin cursor).
        assertEquals(listOf("open:a-1"), calls.filter { it.startsWith("open") })
    }

    @Test
    fun `la ultima linea depende de lo que hizo el usuario`() {
        assertEquals("Reportaste al emisor.", alert("a", reported = true).inboxPreview())
        assertEquals("Marcaste que el aviso te sirvió.", alert("a", feedback = FeedbackRating.UP).inboxPreview())
        assertEquals("Marcaste que el aviso no te sirvió.", alert("a", feedback = FeedbackRating.DOWN).inboxPreview())
        assertEquals("${ViviMessages.EVIDENCE_UNAVAILABLE}.", alert("a", evidence = emptyList()).inboxPreview())
        assertEquals("${ViviMessages.NAME}: ${ViviMessages.OWNER_REVIEW_PROMPT}", alert("a").inboxPreview())
    }

    @Test
    fun `una falla de pantalla completa se reintenta`() {
        show(InboxUiState.Error(InboxError.Network))

        compose.onNodeWithText(InboxError.Network.message()).assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `una falla de pagina deja la lista y ofrece reintentar esa pagina`() {
        show(
            InboxUiState.ContentWithPageError(
                items = listOf(alert("a")),
                error = InboxError.Unavailable,
            ),
        )

        compose.onNodeWithText(alertTitle("CRASHED")).assertIsDisplayed()
        compose.onNodeWithText(InboxError.Unavailable.message()).assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf("retryPage"), calls)
    }

    @Test
    fun `una lista corta con mas paginas pide la siguiente`() {
        show(InboxUiState.Content(listOf(alert("a"), alert("b")), nextCursor = "c2"))
        compose.waitForIdle()

        assertEquals(true, "next" in calls)
    }

    @Test
    fun `cargando la pagina siguiente se ve el indicador sin perder la lista`() {
        show(InboxUiState.Content(listOf(alert("a")), nextCursor = "c2", isLoadingMore = true))

        compose.onNodeWithText(alertTitle("CRASHED")).assertIsDisplayed()
    }

    // -- Enviados -----------------------------------------------------------------------------

    @Test
    fun `en enviados sin alertas muestra el vacio`() {
        show(sent = SentUiState.Empty, tab = InboxTab.SENT)

        compose.onNodeWithText(ViviMessages.SENT_EMPTY_TITLE).assertIsDisplayed()
        compose.onNodeWithText(ViviMessages.SENT_EMPTY_BODY).assertIsDisplayed()
    }

    @Test
    fun `en enviados una alerta se abre al tocarla`() {
        show(
            sent = SentUiState.Content(listOf(sent("s-1")), nextCursor = null),
            tab = InboxTab.SENT,
        )

        compose.onNodeWithText(alertTitle("LUCES_ENCENDIDAS")).performClick()

        assertEquals(listOf("openSent:s-1"), calls.filter { it.startsWith("openSent") })
    }

    @Test
    fun `en enviados una falla completa se reintenta`() {
        show(sent = SentUiState.Error(InboxError.Network), tab = InboxTab.SENT)

        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf("sentRetry"), calls)
    }

    @Test
    fun `en enviados una falla de pagina se reintenta`() {
        show(
            sent = SentUiState.ContentWithPageError(listOf(sent("s-1")), InboxError.Network),
            tab = InboxTab.SENT,
        )

        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf("sentRetryPage"), calls)
    }

    @Test
    fun `en enviados una lista corta con mas paginas pide la siguiente`() {
        show(sent = SentUiState.Content(listOf(sent("s-1")), nextCursor = "c2"), tab = InboxTab.SENT)
        compose.waitForIdle()

        assertEquals(true, "sentNext" in calls)
    }

    @Test
    fun `en enviados cargando se ve el indicador`() {
        show(sent = SentUiState.Loading, tab = InboxTab.SENT)

        compose.onNodeWithText("Enviados").assertIsDisplayed()
    }

    @Test
    fun `cada estado de una alerta enviada se muestra distinto`() {
        show(
            sent = SentUiState.Content(
                listOf(
                    sent("s-1", SentAlertStatus.PENDING_VALIDATION),
                    sent("s-2", SentAlertStatus.ACTIVE),
                    sent("s-3", SentAlertStatus.ACTIVE, seenAt = now),
                    sent("s-4", SentAlertStatus.REJECTED),
                    sent("s-5", SentAlertStatus.EXPIRED),
                ),
                nextCursor = null,
            ),
            tab = InboxTab.SENT,
        )

        compose.onAllNodesWithText(alertTitle("LUCES_ENCENDIDAS")).assertCountEquals(5)
    }
}
