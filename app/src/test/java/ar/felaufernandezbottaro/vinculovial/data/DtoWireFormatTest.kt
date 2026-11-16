package ar.felaufernandezbottaro.vinculovial.data

import ar.felaufernandezbottaro.vinculovial.data.account.dto.DeleteAccountRequestDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptRequestDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.AlertAttemptResponseDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.ApiErrorDto
import ar.felaufernandezbottaro.vinculovial.data.alert.dto.SendAlertMetadataDto
import ar.felaufernandezbottaro.vinculovial.data.benefits.dto.BenefitDto
import ar.felaufernandezbottaro.vinculovial.data.benefits.dto.ClaimResponseDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.AlertItemDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.EvidenceRefDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.FeedbackRequestDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.ReportRequestDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.SentAlertDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.SentPageDto
import ar.felaufernandezbottaro.vinculovial.data.notifications.dto.FcmTokenRequestDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.AuthErrorBodyDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.AuthErrorDetailDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.EmailVerificationConfirmPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.EmailVerificationRequestPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.HpkeEnvelopeDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.PasswordResetCompletePayloadDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.PasswordResetConfirmPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.PasswordResetRequestPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshResponseDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshTokenRequestDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.ResetTokenResponseDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.VerificationTokenResponseDto
import ar.felaufernandezbottaro.vinculovial.data.signin.dto.SignInPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.signin.dto.SignInRequestDto
import ar.felaufernandezbottaro.vinculovial.data.signin.dto.SignInResponseDto
import ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpErrorDetailDto
import ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpErrorResponseDto
import ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpPayloadDto
import ar.felaufernandezbottaro.vinculovial.data.signup.dto.SignUpRequestDto
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.LinkVehicleRequestDto
import ar.felaufernandezbottaro.vinculovial.data.vehicle.dto.VehiclePayloadDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El formato en el cable de cada DTO: los nombres de campo son el contrato con el backend (si uno
 * cambia, la request deja de entenderse) y el `toString` nunca puede imprimir un secreto, porque
 * los DTO terminan en logs y en mensajes de error.
 */
class DtoWireFormatTest {

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    private inline fun <reified T : Any> json(value: T): Map<String, Any?> =
        mapAdapter.fromJson(moshi.adapter(T::class.java).toJson(value))!!

    private inline fun <reified T : Any> parse(text: String): T = moshi.adapter(T::class.java).fromJson(text)!!

    private fun assertMasked(dto: Any, vararg secrets: String) {
        val text = dto.toString()
        secrets.forEach { assertFalse("toString filtró '$it': $text", text.contains(it)) }
    }

    // -- Envoltura HPKE y requests de auth ----------------------------------------------------

    @Test
    fun `la envoltura HPKE viaja con ephemeral_public_key y ciphertext`() {
        assertEquals(
            mapOf("ephemeral_public_key" to "EPK", "ciphertext" to "CT"),
            json(HpkeEnvelopeDto("EPK", "CT")),
        )
        assertEquals(HpkeEnvelopeDto("EPK", "CT"), parse<HpkeEnvelopeDto>("""{"ephemeral_public_key":"EPK","ciphertext":"CT"}"""))
        assertEquals(mapOf("ephemeral_public_key" to "EPK", "ciphertext" to "CT"), json(SignInRequestDto("EPK", "CT")))
        assertEquals(mapOf("ephemeral_public_key" to "EPK", "ciphertext" to "CT"), json(SignUpRequestDto("EPK", "CT")))
        assertEquals(SignInRequestDto("E", "C"), parse<SignInRequestDto>("""{"ephemeral_public_key":"E","ciphertext":"C"}"""))
        assertEquals(SignUpRequestDto("E", "C"), parse<SignUpRequestDto>("""{"ephemeral_public_key":"E","ciphertext":"C"}"""))
    }

    @Test
    fun `el payload del login lleva dni y password y no se imprime`() {
        val dto = SignInPayloadDto(dni = "12345678", password = "Secreta123!")

        assertEquals(mapOf("dni" to "12345678", "password" to "Secreta123!"), json(dto))
        assertEquals(dto, parse<SignInPayloadDto>("""{"dni":"12345678","password":"Secreta123!"}"""))
        assertMasked(dto, "12345678", "Secreta123!")
    }

    @Test
    fun `la respuesta del login trae los dos tokens y no se imprime`() {
        val dto = parse<SignInResponseDto>("""{"access_token":"AT","refresh_token":"RT"}""")

        assertEquals(SignInResponseDto("AT", "RT"), dto)
        assertEquals(mapOf("access_token" to "AT", "refresh_token" to "RT"), json(dto))
        assertMasked(dto, "AT", "RT")
    }

    @Test
    fun `el refresh envia refresh_token y recibe los dos tokens`() {
        val request = RefreshTokenRequestDto("RT")
        val response = parse<RefreshResponseDto>("""{"access_token":"AT2","refresh_token":"RT2"}""")

        assertEquals(mapOf("refresh_token" to "RT"), json(request))
        assertEquals(RefreshTokenRequestDto("RT"), parse<RefreshTokenRequestDto>("""{"refresh_token":"RT"}"""))
        assertEquals(RefreshResponseDto("AT2", "RT2"), response)
        assertEquals(mapOf("access_token" to "AT2", "refresh_token" to "RT2"), json(response))
        assertMasked(request, "RT")
        assertMasked(response, "AT2", "RT2")
    }

    @Test
    fun `el payload del alta usa los nombres del contrato de Identity`() {
        val dto = SignUpPayloadDto(
            dni = "12345678",
            procedureNumber = "00307513148",
            sex = "M",
            email = "u@ejemplo.com",
            password = "Secreta123!",
            selfie = "SELFIE64",
            emailVerificationToken = "VT",
            termsVersion = "1.0",
        )

        val expected = mapOf(
            "dni" to "12345678",
            "tramite" to "00307513148",
            "sexo" to "M",
            "email" to "u@ejemplo.com",
            "password" to "Secreta123!",
            "selfie" to "SELFIE64",
            "email_verification_token" to "VT",
            "terms_version" to "1.0",
        )
        assertEquals(expected, json(dto))
        assertEquals(dto, moshi.adapter(SignUpPayloadDto::class.java).fromJson(mapAdapter.toJson(expected)))
        assertMasked(dto, "12345678", "00307513148", "u@ejemplo.com", "Secreta123!", "SELFIE64", "VT")
    }

    @Test
    fun `el error del alta trae el codigo y el detalle por campo`() {
        val dto = parse<SignUpErrorResponseDto>(
            """{"code":"VALIDATION","details":[{"field":"email","code":"INVALID","message":"mal"},{"field":"dni","code":"TAKEN"}]}""",
        )

        assertEquals("VALIDATION", dto.code)
        assertEquals(
            listOf(SignUpErrorDetailDto("email", "INVALID", "mal"), SignUpErrorDetailDto("dni", "TAKEN", null)),
            dto.details,
        )
        assertNull(parse<SignUpErrorResponseDto>("""{"code":"X"}""").details)
        assertEquals(
            mapOf("field" to "dni", "code" to "TAKEN"),
            json(SignUpErrorDetailDto("dni", "TAKEN")),
        )
        assertEquals(mapOf("code" to "X"), json(SignUpErrorResponseDto("X")))
    }

    // -- Verificación de email y recuperación -------------------------------------------------

    @Test
    fun `los payloads de email y recuperacion usan sus campos y no se imprimen`() {
        val request = EmailVerificationRequestPayloadDto("u@ejemplo.com")
        val confirm = EmailVerificationConfirmPayloadDto("u@ejemplo.com", "123456")
        val resetRequest = PasswordResetRequestPayloadDto("12345678")
        val resetConfirm = PasswordResetConfirmPayloadDto("12345678", "654321")

        assertEquals(mapOf("email" to "u@ejemplo.com"), json(request))
        assertEquals(mapOf("email" to "u@ejemplo.com", "code" to "123456"), json(confirm))
        assertEquals(mapOf("dni" to "12345678"), json(resetRequest))
        assertEquals(mapOf("dni" to "12345678", "code" to "654321"), json(resetConfirm))
        assertEquals(request, parse<EmailVerificationRequestPayloadDto>("""{"email":"u@ejemplo.com"}"""))
        assertEquals(confirm, parse<EmailVerificationConfirmPayloadDto>("""{"email":"u@ejemplo.com","code":"123456"}"""))
        assertEquals(resetRequest, parse<PasswordResetRequestPayloadDto>("""{"dni":"12345678"}"""))
        assertEquals(resetConfirm, parse<PasswordResetConfirmPayloadDto>("""{"dni":"12345678","code":"654321"}"""))
        assertMasked(request, "u@ejemplo.com")
        assertMasked(confirm, "u@ejemplo.com", "123456")
        assertMasked(resetRequest, "12345678")
        assertMasked(resetConfirm, "12345678", "654321")
    }

    @Test
    fun `completar la recuperacion envia el token, el documento, la selfie y la contrasena nueva`() {
        val dto = PasswordResetCompletePayloadDto(
            resetToken = "RESET",
            dni = "12345678",
            procedureNumber = "00307513148",
            sex = "F",
            selfie = "SELFIE64",
            newPassword = "Nueva123!",
        )

        val expected = mapOf(
            "reset_token" to "RESET",
            "dni" to "12345678",
            "tramite" to "00307513148",
            "sexo" to "F",
            "selfie" to "SELFIE64",
            "new_password" to "Nueva123!",
        )
        assertEquals(expected, json(dto))
        assertEquals(dto, moshi.adapter(PasswordResetCompletePayloadDto::class.java).fromJson(mapAdapter.toJson(expected)))
        assertMasked(dto, "RESET", "12345678", "00307513148", "SELFIE64", "Nueva123!")
    }

    @Test
    fun `los tokens de verificacion y de reset traen su vencimiento opcional y no se imprimen`() {
        val verification = parse<VerificationTokenResponseDto>("""{"verification_token":"VT","expires_in":600}""")
        val reset = parse<ResetTokenResponseDto>("""{"reset_token":"RT","expires_in":300}""")

        assertEquals(VerificationTokenResponseDto("VT", 600L), verification)
        assertEquals(ResetTokenResponseDto("RT", 300L), reset)
        assertNull(parse<VerificationTokenResponseDto>("""{"verification_token":"VT"}""").expiresIn)
        assertNull(parse<ResetTokenResponseDto>("""{"reset_token":"RT"}""").expiresIn)
        assertEquals(mapOf("verification_token" to "VT", "expires_in" to 600.0), json(verification))
        assertEquals(mapOf("reset_token" to "RT", "expires_in" to 300.0), json(reset))
        assertMasked(verification, "VT")
        assertMasked(reset, "RT")
    }

    @Test
    fun `el cuerpo de error de auth tolera campos ausentes`() {
        val full = parse<AuthErrorBodyDto>("""{"code":"INVALID_CODE","details":[{"field":"code","code":"BAD"}]}""")
        val empty = parse<AuthErrorBodyDto>("{}")

        assertEquals("INVALID_CODE", full.code)
        assertEquals(listOf(AuthErrorDetailDto("code", "BAD")), full.details)
        assertNull(empty.code)
        assertNull(empty.details)
        assertEquals(AuthErrorDetailDto(null, null), parse<AuthErrorDetailDto>("{}"))
        assertEquals(mapOf("field" to "f", "code" to "c"), json(AuthErrorDetailDto("f", "c")))
        assertEquals(mapOf("code" to "X"), json(AuthErrorBodyDto(code = "X")))
    }

    // -- Cuenta, vehiculo, notificaciones -----------------------------------------------------

    @Test
    fun `la baja de cuenta envia la contrasena y no la imprime`() {
        val dto = DeleteAccountRequestDto("Secreta123!")

        assertEquals(mapOf("password" to "Secreta123!"), json(dto))
        assertEquals(dto, parse<DeleteAccountRequestDto>("""{"password":"Secreta123!"}"""))
        assertMasked(dto, "Secreta123!")
    }

    @Test
    fun `la vinculacion de vehiculo cifra chasis y patente con los nombres del contrato`() {
        val payload = VehiclePayloadDto(chasisBase64 = "CHASIS64", patenteBase64 = "PAT64")
        val request = LinkVehicleRequestDto(payloadEnc = "ENC")

        assertEquals(mapOf("chasis" to "CHASIS64", "patente" to "PAT64"), json(payload))
        assertEquals(mapOf("payload_enc" to "ENC"), json(request))
        assertEquals(payload, parse<VehiclePayloadDto>("""{"chasis":"CHASIS64","patente":"PAT64"}"""))
        assertEquals(request, parse<LinkVehicleRequestDto>("""{"payload_enc":"ENC"}"""))
        assertMasked(payload, "CHASIS64", "PAT64")
        assertMasked(request, "ENC")
    }

    @Test
    fun `el token de FCM viaja como fcm_token`() {
        val dto = FcmTokenRequestDto("TOKEN")

        assertEquals(mapOf("fcm_token" to "TOKEN"), json(dto))
        assertEquals(dto, parse<FcmTokenRequestDto>("""{"fcm_token":"TOKEN"}"""))
    }

    // -- Alertas ------------------------------------------------------------------------------

    @Test
    fun `el intento de alerta envia pat_enc y recibe attempt_id y expires_at`() {
        val request = AlertAttemptRequestDto("PATENC")
        val response = parse<AlertAttemptResponseDto>("""{"attempt_id":"att-1","expires_at":"2026-11-16T12:00:00Z"}""")

        assertEquals(mapOf("pat_enc" to "PATENC"), json(request))
        assertEquals(request, parse<AlertAttemptRequestDto>("""{"pat_enc":"PATENC"}"""))
        assertEquals(AlertAttemptResponseDto("att-1", "2026-11-16T12:00:00Z"), response)
        assertEquals(mapOf("attempt_id" to "att-1", "expires_at" to "2026-11-16T12:00:00Z"), json(response))
        assertMasked(request, "PATENC")
    }

    @Test
    fun `el metadata del envio lleva intento, tipo y patente cifrada, sin imprimir la patente`() {
        val dto = SendAlertMetadataDto(attemptId = "att-1", alertType = "CRASHED", patEnc = "PATENC")

        assertEquals(mapOf("attempt_id" to "att-1", "alert_type" to "CRASHED", "pat_enc" to "PATENC"), json(dto))
        assertEquals(dto, parse<SendAlertMetadataDto>("""{"attempt_id":"att-1","alert_type":"CRASHED","pat_enc":"PATENC"}"""))
        assertMasked(dto, "PATENC")
        assertTrue(dto.toString().contains("att-1"))
    }

    @Test
    fun `el error generico de la API tolera campos ausentes`() {
        val full = parse<ApiErrorDto>("""{"error":"limite","code":"ALERT_DAILY_LIMIT","available_at":"2026-11-17T10:00:00Z"}""")

        assertEquals(ApiErrorDto("limite", "ALERT_DAILY_LIMIT", "2026-11-17T10:00:00Z"), full)
        assertEquals(ApiErrorDto(), parse<ApiErrorDto>("{}"))
        assertEquals(
            mapOf("error" to "limite", "code" to "ALERT_DAILY_LIMIT", "available_at" to "2026-11-17T10:00:00Z"),
            json(full),
        )
    }

    // -- Recibidos y Enviados -----------------------------------------------------------------

    @Test
    fun `un item de Recibidos completo se lee con todos sus campos`() {
        val dto = parse<AlertItemDto>(
            """{"id":"a1","alert_type":"CRASHED","created_at":"2026-11-16T10:00:00Z","requires_review":true,
            "evidence":[{"role":"GENERAL","index":0},{"role":"PLATE","index":1}],
            "evidence_expires_at":"2026-12-16T10:00:00Z","my_feedback":"UP","reported":true,"seen":true}""",
        )

        assertEquals("a1", dto.id)
        assertEquals("CRASHED", dto.alertType)
        assertTrue(dto.requiresReview)
        assertEquals(listOf(EvidenceRefDto("GENERAL", 0), EvidenceRefDto("PLATE", 1)), dto.evidence)
        assertEquals("2026-12-16T10:00:00Z", dto.evidenceExpiresAt)
        assertEquals("UP", dto.myFeedback)
        assertTrue(dto.reported)
        assertTrue(dto.seen)
        assertEquals(dto, moshi.adapter(AlertItemDto::class.java).fromJson(moshi.adapter(AlertItemDto::class.java).toJson(dto)))
    }

    @Test
    fun `un item de Recibidos minimo usa los valores por defecto`() {
        val dto = parse<AlertItemDto>("""{"id":"a1","alert_type":"CRASHED","created_at":"2026-11-16T10:00:00Z"}""")

        assertFalse(dto.requiresReview)
        assertFalse(dto.reported)
        assertFalse(dto.seen)
        assertNull(dto.evidence)
        assertNull(dto.evidenceExpiresAt)
        assertNull(dto.myFeedback)
        assertEquals(
            mapOf(
                "id" to "a1",
                "alert_type" to "CRASHED",
                "created_at" to "2026-11-16T10:00:00Z",
                "requires_review" to false,
                "reported" to false,
                "seen" to false,
            ),
            json(dto),
        )
    }

    @Test
    fun `las paginas de Recibidos y Enviados traen el cursor`() {
        val inbox = parse<InboxPageDto>(
            """{"items":[{"id":"a1","alert_type":"CRASHED","created_at":"t"}],"next_cursor":"c2"}""",
        )
        val last = parse<InboxPageDto>("""{"items":[],"next_cursor":null}""")
        val sent = parse<SentPageDto>(
            """{"items":[{"id":"s1","alert_type":"CRASHED","created_at":"t","status":"DELIVERED","seen_at":"t2"}],"next_cursor":"c3"}""",
        )

        assertEquals("c2", inbox.nextCursor)
        assertEquals(1, inbox.items.size)
        assertNull(last.nextCursor)
        assertEquals("c3", sent.nextCursor)
        assertEquals(SentAlertDto("s1", "CRASHED", "t", "DELIVERED", "t2"), sent.items.single())
        assertEquals(mapOf("items" to emptyList<Any>(), "next_cursor" to "c9"), json(InboxPageDto(emptyList(), "c9")))
        assertEquals(mapOf("items" to emptyList<Any>()), json(SentPageDto(emptyList(), null)))
    }

    @Test
    fun `un envio sin vista no trae seen_at`() {
        val dto = parse<SentAlertDto>("""{"id":"s1","alert_type":"CRASHED","created_at":"t","status":"PENDING"}""")

        assertNull(dto.seenAt)
        assertEquals(
            mapOf("id" to "s1", "alert_type" to "CRASHED", "created_at" to "t", "status" to "PENDING"),
            json(dto),
        )
    }

    @Test
    fun `la valoracion y el reporte envian rating y reason`() {
        assertEquals(mapOf("rating" to "UP"), json(FeedbackRequestDto("UP")))
        assertEquals(mapOf("reason" to "SPAM"), json(ReportRequestDto("SPAM")))
        assertEquals(FeedbackRequestDto("DOWN"), parse<FeedbackRequestDto>("""{"rating":"DOWN"}"""))
        assertEquals(ReportRequestDto("SPAM"), parse<ReportRequestDto>("""{"reason":"SPAM"}"""))
        assertEquals(mapOf("role" to "PLATE", "index" to 1.0), json(EvidenceRefDto("PLATE", 1)))
    }

    // -- Beneficios ---------------------------------------------------------------------------

    @Test
    fun `una promocion sin reclamar no trae codigo y no imprime el codigo si lo tiene`() {
        val body = """{"id":"b1","partner_name":"Estación","title":"20 %","description":"d","terms":"t",
            "open_url":"https://ejemplo.com","ends_at":"2030-11-30T15:00:00Z"}"""
        val without = parse<BenefitDto>(body)
        val with = without.copy(code = "ABCD-1234")

        assertNull(without.code)
        assertEquals("Estación", without.partnerName)
        assertEquals("https://ejemplo.com", without.openUrl)
        assertEquals("2030-11-30T15:00:00Z", without.endsAt)
        assertEquals("ABCD-1234", json(with)["code"])
        assertEquals(setOf("id", "partner_name", "title", "description", "terms", "open_url", "ends_at"), json(without).keys)
        assertMasked(with, "ABCD-1234")
        assertTrue(without.toString().contains("code=null"))
    }

    @Test
    fun `reclamar una promocion devuelve el codigo y no lo imprime`() {
        val dto = parse<ClaimResponseDto>("""{"code":"ABCD-1234"}""")

        assertEquals(ClaimResponseDto("ABCD-1234"), dto)
        assertEquals(mapOf("code" to "ABCD-1234"), json(dto))
        assertMasked(dto, "ABCD-1234")
    }
}
