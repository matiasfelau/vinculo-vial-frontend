package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportReason
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.alertMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * El chat de una alerta del lado del dueño: el mensaje con la evidencia en cada estado de carga,
 * la valoración y el reporte al emisor (con su diálogo de motivo).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class InboxDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val general = EvidenceRef("GENERAL", 0)
    private val plate = EvidenceRef("PLATE", 1)

    private fun item(
        type: String = "CRASHED",
        evidence: List<EvidenceRef> = listOf(general, plate),
        feedback: FeedbackRating? = null,
        reported: Boolean = false,
        expires: Instant? = null,
    ) = AlertItem(
        id = "a-1",
        alertType = type,
        createdAt = Instant.now().minusSeconds(120),
        evidence = evidence,
        evidenceExpiresAt = expires,
        myFeedback = feedback,
        reported = reported,
    )

    private fun show(
        item: AlertItem = item(),
        slots: List<EvidenceSlot> = emptyList(),
        feedbackFailed: Boolean = false,
        report: ReportUiState = ReportUiState.Idle,
    ) {
        compose.setContent {
            VinculoVialTheme {
                InboxDetailScreen(
                    detail = AlertDetailUiState(item, slots, feedbackFailed, report),
                    onBack = { calls += "back" },
                    onRetryEvidence = { calls += "retry:${it.role}" },
                    onRate = { calls += "rate:${it.name}" },
                    onReportSender = { calls += "report:${it.name}" },
                    onOpenTerms = { calls += "terms" },
                )
            }
        }
    }

    @Test
    fun `muestra el saludo de ViVIAna, el mensaje del tipo y el pedido de revisar las fotos`() {
        show(item(type = "LUCES_ENCENDIDAS"))

        compose.onNodeWithText(ViviMessages.OWNER_GREETING).assertIsDisplayed()
        compose.onNodeWithText(alertMessage("LUCES_ENCENDIDAS")).assertIsDisplayed()
        compose.onNodeWithText(ViviMessages.OWNER_REVIEW_PROMPT).assertIsDisplayed()
    }

    @Test
    fun `un choque usa su propio mensaje`() {
        show(item(type = "CRASHED"))

        compose.onNodeWithText(alertMessage("CRASHED")).assertIsDisplayed()
    }

    @Test
    fun `sin evidencia lo dice`() {
        show(item(evidence = emptyList()))

        compose.onNodeWithText(ViviMessages.EVIDENCE_EMPTY).assertIsDisplayed()
    }

    @Test
    fun `volver avisa`() {
        show()

        compose.onNodeWithContentDescription("Volver al buzón").performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `la evidencia cargando muestra un indicador por foto`() {
        show(slots = listOf(EvidenceSlot(general, EvidenceLoad.Loading), EvidenceSlot(plate, EvidenceLoad.Loading)))

        compose.onNodeWithText("MENSAJE").assertIsDisplayed()
        compose.onNodeWithText("EVIDENCIA").assertIsDisplayed()
    }

    @Test
    fun `la evidencia vencida avisa que ya no esta disponible`() {
        show(slots = listOf(EvidenceSlot(general, EvidenceLoad.Expired)))

        compose.onNodeWithText(ViviMessages.EVIDENCE_UNAVAILABLE).assertIsDisplayed()
    }

    @Test
    fun `una foto que fallo ofrece reintentar esa foto`() {
        show(slots = listOf(EvidenceSlot(plate, EvidenceLoad.Failed(InboxError.NotFound))))

        compose.onNodeWithText(InboxError.NotFound.message()).assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf("retry:PLATE"), calls)
    }

    @Test
    fun `una foto cargada se muestra con su etiqueta`() {
        val jpeg = ByteArray(32) { it.toByte() }
        show(slots = listOf(EvidenceSlot(general, EvidenceLoad.Loaded(jpeg))))

        compose.waitForIdle()

        compose.onNodeWithText("MENSAJE").assertIsDisplayed()
    }

    @Test
    fun `valorar avisa el sentido`() {
        show()

        compose.onNodeWithContentDescription("Me sirvió").performClick()
        compose.onNodeWithContentDescription("No me sirvió").performClick()

        assertEquals(listOf("rate:UP", "rate:DOWN"), calls)
    }

    @Test
    fun `una valoracion que no se pudo guardar lo avisa`() {
        show(feedbackFailed = true)

        compose.onNodeWithText("No pudimos guardar tu valoración. Intentá de nuevo.").assertIsDisplayed()
    }

    @Test
    fun `reportar abre el dialogo y exige elegir un motivo`() {
        show()

        compose.onNodeWithText(ViviMessages.REPORT_SENDER).performClick()
        // El título del diálogo y el botón de la pantalla dicen lo mismo.
        compose.onAllNodesWithText(ViviMessages.REPORT_SENDER).assertCountEquals(2)
        compose.onNodeWithText("Reportar").assertIsNotEnabled()

        compose.onNodeWithText(ReportReason.SPAM.label).performClick()
        compose.onNodeWithText("Reportar").assertIsEnabled().performClick()

        assertEquals(listOf("report:SPAM"), calls)
    }

    @Test
    fun `cancelar el dialogo no reporta`() {
        show()

        compose.onNodeWithText(ViviMessages.REPORT_SENDER).performClick()
        compose.onNodeWithText("Cancelar").performClick()

        assertEquals(emptyList<String>(), calls)
        compose.onAllNodesWithText(ViviMessages.REPORT_SENDER).assertCountEquals(1)
    }

    @Test
    fun `una alerta ya reportada no se puede reportar de nuevo`() {
        show(item(reported = true))

        compose.onNodeWithText(ViviMessages.REPORTED).assertIsNotEnabled()
    }

    @Test
    fun `mientras se envia el reporte no se puede reportar otra vez`() {
        show(report = ReportUiState.Sending)

        compose.onNodeWithText(ViviMessages.REPORT_SENDER).assertIsNotEnabled()
    }

    @Test
    fun `un reporte que fallo muestra el motivo`() {
        show(report = ReportUiState.Failed(InboxError.RateLimited))

        compose.onNodeWithText(InboxError.RateLimited.message()).assertIsDisplayed()
    }

    @Test
    fun `la linea de terminos de la valoracion lleva a los Terminos`() {
        show()

        compose.onNodeWithTag(FEEDBACK_TERMS_TAG).assertIsDisplayed()
    }
}
