package ar.felaufernandezbottaro.vinculovial.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import ar.felaufernandezbottaro.vinculovial.presentation.camera.CameraPermissionGate
import ar.felaufernandezbottaro.vinculovial.presentation.camera.DocumentScanScreen
import ar.felaufernandezbottaro.vinculovial.presentation.camera.LivenessCaptureScreen
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxDetailScreen
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxScreen
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.push.NotificationPermissionRequester
import ar.felaufernandezbottaro.vinculovial.presentation.push.planPushNavigation
import ar.felaufernandezbottaro.vinculovial.presentation.session.SessionViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.settings.SettingsScreen
import ar.felaufernandezbottaro.vinculovial.presentation.settings.SettingsViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.signin.SignInScreen
import ar.felaufernandezbottaro.vinculovial.presentation.signin.SignInStatus
import ar.felaufernandezbottaro.vinculovial.presentation.signin.SignInViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.signup.CaptureTarget
import ar.felaufernandezbottaro.vinculovial.presentation.signup.SignUpCaptureScreen
import ar.felaufernandezbottaro.vinculovial.presentation.signup.SignUpConsentScreen
import ar.felaufernandezbottaro.vinculovial.presentation.signup.SignUpFormScreen
import ar.felaufernandezbottaro.vinculovial.presentation.signup.SignUpStatus
import ar.felaufernandezbottaro.vinculovial.presentation.signup.SignUpSuccessScreen
import ar.felaufernandezbottaro.vinculovial.presentation.signup.SignUpViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.scan.EvidenceCaptureScreen
import ar.felaufernandezbottaro.vinculovial.presentation.scan.EvidencePhase
import ar.felaufernandezbottaro.vinculovial.presentation.scan.ReportChatScreen
import ar.felaufernandezbottaro.vinculovial.presentation.scan.ScanPlateScreen
import ar.felaufernandezbottaro.vinculovial.presentation.scan.ScanStatus
import ar.felaufernandezbottaro.vinculovial.presentation.scan.ScanViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.splash.SplashScreen
import ar.felaufernandezbottaro.vinculovial.presentation.vehicle.ScanCedulaScreen
import ar.felaufernandezbottaro.vinculovial.presentation.vehicle.VehicleViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.welcome.WelcomeScreen

object Route {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val SIGN_IN = "signin"
    const val SETTINGS = "settings"
    const val SIGN_UP_GRAPH = "signup"
    const val SIGN_UP_FORM = "signup/form"
    const val SIGN_UP_CONSENT = "signup/consent"
    const val SIGN_UP_CAPTURE = "signup/capture"
    const val SIGN_UP_SUCCESS = "signup/success"

    const val SIGN_UP_DOCUMENT_SCAN = "signup/document-scan"
    const val SIGN_UP_LIVENESS = "signup/liveness"

    const val LINK_VEHICLE = "vehicle/link"

    const val SCAN_GRAPH = "scan"
    const val SCAN_PLATE = "scan/plate"
    const val REPORT_CHAT = "scan/report-chat"
    const val EVIDENCE_CAPTURE = "scan/evidence-capture"

    const val INBOX = "inbox"
    const val INBOX_DETAIL = "inbox/detail"
}

@Composable
fun VinculoVialNavHost(
    navController: NavHostController = rememberNavController(),
    pendingAlertType: String? = null,
    onPendingAlertTypeConsumed: () -> Unit = {},
) {
    // RTR fallido (401/403 al refrescar) borra la sesión local en segundo plano, sin que la
    // pantalla que disparó la request original se entere -- SessionManager ni sabe qué
    // pantalla es. Este observer es el único lugar que reacciona a esa caída y saca al
    // usuario a Login, sea cual sea la pantalla en la que esté. Hoy no dispara en la
    // práctica: no hay ninguna pantalla protegida más allá de Login/Registro (no existe
    // todavía una Home/Dashboard, fuera del alcance de este CU) -- pero la próxima pantalla
    // autenticada hereda esta redirección gratis, sin tocar este archivo.
    val sessionViewModel = hiltViewModel<SessionViewModel>()
    val hasSession by sessionViewModel.hasSession.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()

    // Se pide el permiso de notificaciones una única vez, recién tras un login exitoso -- ver
    // NotificationPermissionRequester. Volver a Settings desde otra pantalla no lo vuelve a
    // disparar: se consume apenas se compone.
    var justSignedIn by remember { mutableStateOf(false) }
    LaunchedEffect(hasSession, currentEntry) {
        if (!hasSession && !isPublicRoute(currentEntry?.destination?.route)) {
            navController.navigate(Route.SIGN_IN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Al tocar una notificación se reconstruye el stack (Settings antes de Inbox, nunca Inbox
    // sola) en vez de navegar directo -- ver planPushNavigation. Sin sesión todavía, el efecto
    // de arriba ya se encarga de mandar a Login; esta misma clave (hasSession) hace que el
    // plan se reevalúe apenas haya sesión, así que un login iniciado desde la notificación
    // igual termina en la Bandeja de Entrada.
    LaunchedEffect(hasSession, pendingAlertType) {
        val plan = planPushNavigation(hasSession, pendingAlertType)
        if (plan.isNotEmpty()) {
            navController.navigate(plan.first()) {
                popUpTo(0) { inclusive = true }
            }
            plan.drop(1).forEach { route -> navController.navigate(route) }
            onPendingAlertTypeConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Route.SPLASH) {

        composable(Route.SPLASH) {
            SplashScreen(
                onFinished = {
                    // La bienvenida pasa a ser la puerta de entrada de la app. El caso "ya
                    // hay una sesión guardada" queda pendiente de una Home/Dashboard que
                    // todavía no existe (fuera del alcance de este CU) — por ahora se
                    // aterriza siempre acá, sin distinguir si hay sesión o no.
                    navController.navigate(Route.WELCOME) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.WELCOME) {
            WelcomeScreen(
                onGoToSignIn = { navController.navigate(Route.SIGN_IN) },
                onGoToSignUp = { navController.navigate(Route.SIGN_UP_GRAPH) },
            )
        }

        composable(Route.SIGN_IN) { entry ->
            val viewModel = hiltViewModel<SignInViewModel>(entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // Todavía no hay Home/Dashboard (fuera del alcance de US-02/US-03): Settings
            // hace de parada temporal tras el login, ya que es la única pantalla
            // autenticada que existe hoy.
            LaunchedEffect(state.status) {
                if (state.status is SignInStatus.SignedIn) {
                    justSignedIn = true
                    navController.navigate(Route.SETTINGS) {
                        popUpTo(Route.SIGN_IN) { inclusive = true }
                    }
                }
            }

            SignInScreen(
                state = state,
                onDniChange = viewModel::onDniChange,
                onPasswordChange = viewModel::onPasswordChange,
                onSubmit = viewModel::submit,
                onGoToSignUp = { navController.navigate(Route.SIGN_UP_GRAPH) },
            )
        }

        composable(Route.SETTINGS) { entry ->
            val viewModel = hiltViewModel<SettingsViewModel>(entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            SettingsScreen(
                state = state,
                onLogout = viewModel::logout,
                onLinkVehicle = { navController.navigate(Route.LINK_VEHICLE) },
                onScanPlate = { navController.navigate(Route.SCAN_GRAPH) },
                onOpenInbox = { navController.navigate(Route.INBOX) },
            )

            if (justSignedIn) {
                NotificationPermissionRequester(onFinished = { justSignedIn = false })
            }
        }

        composable(Route.INBOX) { entry ->
            val viewModel = hiltViewModel<InboxViewModel>(entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val readIds by viewModel.readAlertIds.collectAsStateWithLifecycle()

            InboxScreen(
                uiState = state,
                readIds = readIds,
                onRetry = viewModel::loadFirstPage,
                onLoadNextPage = viewModel::loadNextPage,
                onRetryNextPage = viewModel::retryNextPage,
                onOpenAlert = { alertId ->
                    viewModel.openAlert(alertId)
                    navController.navigate(Route.INBOX_DETAIL)
                },
            )
        }

        composable(Route.INBOX_DETAIL) { entry ->
            // El mismo `InboxViewModel` que la lista: lo que cambia acá (valoración, reporte) se ve al volver.
            val parentEntry = remember(entry) { navController.getBackStackEntry(Route.INBOX) }
            val viewModel = hiltViewModel<InboxViewModel>(parentEntry)
            val detail by viewModel.detail.collectAsStateWithLifecycle()

            // Al salir del detalle (por el botón, el gesto de volver o cualquier otra vía) se cierra:
            // las fotos que bajó se sobreescriben con ceros.
            DisposableEffect(Unit) { onDispose { viewModel.closeAlert() } }

            // Sin detalle abierto (p. ej. tras recrearse el proceso) no hay nada que mostrar. Sólo
            // se sale si el destino está en primer plano: durante la animación de salida el detalle
            // ya está cerrado y un segundo `popBackStack` se llevaría también al buzón.
            val current = detail
            if (current == null) {
                LaunchedEffect(Unit) {
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) navController.popBackStack()
                }
            } else {
                InboxDetailScreen(
                    detail = current,
                    onBack = { navController.popBackStack() },
                    onRetryEvidence = viewModel::retryEvidence,
                    onRate = viewModel::onRate,
                    onReportSender = viewModel::onReportSender,
                )
            }
        }

        composable(Route.LINK_VEHICLE) { entry ->
            val viewModel = hiltViewModel<VehicleViewModel>(entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            CameraPermissionGate(
                onDenied = { navController.popBackStack() },
                deniedRationale = "Sin cámara no podemos leer el QR de tu cédula. Los cuadros se " +
                    "analizan en tu dispositivo y se descartan al instante.",
                permanentlyDeniedRationale = "Activá el permiso de cámara desde los ajustes del " +
                    "sistema para vincular tu vehículo. Los cuadros se analizan en tu dispositivo " +
                    "y se descartan al instante.",
            ) {
                ScanCedulaScreen(
                    scanSession = viewModel.qrCedulaScanner,
                    uiState = state,
                    onCedulaScanned = viewModel::onCedulaScanned,
                    onErrorDismissed = viewModel::onErrorDismissed,
                    onLinked = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
        }

        navigation(startDestination = Route.SCAN_PLATE, route = Route.SCAN_GRAPH) {

            composable(Route.SCAN_PLATE) { entry ->
                val viewModel = entry.sharedScanViewModel(navController)
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // El intento contra /alerts/attempts terminó: el catálogo de incidentes y el
                // envío del reporte viven en ReportChatScreen, no en un menú flotante sobre
                // la cámara. Se saca ScanPlateScreen del back stack al navegar: no tiene
                // sentido volver a la cámara en vivo una vez identificado el vehículo.
                LaunchedEffect(state.status) {
                    if (state.status is ScanStatus.AwaitingAlertType) {
                        navController.navigate(Route.REPORT_CHAT) {
                            popUpTo(Route.SCAN_PLATE) { inclusive = true }
                        }
                    }
                }

                CameraPermissionGate(
                    onDenied = { navController.popBackStack() },
                    deniedRationale = "Sin cámara no podemos leer la patente. Los cuadros se analizan " +
                        "en tu dispositivo y se descartan al instante.",
                    permanentlyDeniedRationale = "Activá el permiso de cámara desde los ajustes del " +
                        "sistema para escanear una patente. Los cuadros se analizan en tu dispositivo " +
                        "y se descartan al instante.",
                ) {
                    ScanPlateScreen(
                        uiState = state,
                        onFrame = viewModel::processFrame,
                        onPlateConfirmed = viewModel::onPlateConfirmed,
                        onPlateRejected = viewModel::onPlateRejected,
                        onRescan = viewModel::onRescan,
                        onManualEntryOpened = viewModel::onManualEntryOpened,
                        onManualPlateSubmitted = viewModel::onManualPlateSubmitted,
                        onManualEntryDismissed = viewModel::onManualEntryDismissed,
                        onNotInNetworkDismissed = viewModel::onNotInNetworkDismissed,
                        onCheckErrorDismissed = viewModel::onCheckErrorDismissed,
                        onCancel = { navController.popBackStack() },
                    )
                }
            }

            composable(Route.REPORT_CHAT) { entry ->
                val viewModel = entry.sharedScanViewModel(navController)
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                ReportChatScreen(
                    uiState = state,
                    onAlertTypeSelected = viewModel::onAlertTypeSelected,
                    onStartCapture = {
                        viewModel.onCaptureStarted()
                        navController.navigate(Route.EVIDENCE_CAPTURE)
                    },
                    onReportRequested = viewModel::onReportRequested,
                    onRetry = viewModel::onAlertRetry,
                    onRestart = {
                        viewModel.onFlowRestarted()
                        navController.navigate(Route.SCAN_PLATE) {
                            popUpTo(Route.REPORT_CHAT) { inclusive = true }
                        }
                    },
                    onFinished = { navController.popBackStack(Route.SETTINGS, inclusive = false) },
                    onCancel = {
                        viewModel.onReportCancelled()
                        navController.popBackStack(Route.SETTINGS, inclusive = false)
                    },
                )
            }

            composable(Route.EVIDENCE_CAPTURE) { entry ->
                val viewModel = entry.sharedScanViewModel(navController)
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // Todas las fotos están: se vuelve al chat, que ofrece enviar el reporte.
                LaunchedEffect(state.evidence.phase) {
                    if (state.evidence.phase == EvidencePhase.COMPLETE) navController.popBackStack()
                }

                // Salir de la cámara sin terminar vuelve al chat con el mensaje elegido y "Tomar fotos".
                val discard = {
                    viewModel.onCaptureCancelled()
                    navController.popBackStack()
                }

                CameraPermissionGate(
                    onDenied = { discard() },
                    deniedRationale = "Sin cámara no podemos sacar las fotos de la evidencia. Las fotos " +
                        "se cifran en tu dispositivo antes de enviarse.",
                    permanentlyDeniedRationale = "Activá el permiso de cámara desde los ajustes del " +
                        "sistema para sacar las fotos de la evidencia. Las fotos se cifran en tu " +
                        "dispositivo antes de enviarse.",
                ) {
                    EvidenceCaptureScreen(
                        state = state.evidence,
                        galleryEvidenceSource = viewModel.galleryEvidenceSource,
                        decodeReviewPhoto = viewModel::decodeReviewPhoto,
                        onPhotoCaptured = viewModel::onPhotoCaptured,
                        onPhotoCaptureFailed = viewModel::onPhotoCaptureFailed,
                        onPhotoRetake = viewModel::onPhotoRetake,
                        onAddAnotherPhoto = viewModel::onAddAnotherPhoto,
                        onStepConfirmed = viewModel::onStepConfirmed,
                        onCancel = { discard() },
                    )
                }
            }
        }

        navigation(startDestination = Route.SIGN_UP_FORM, route = Route.SIGN_UP_GRAPH) {

            composable(Route.SIGN_UP_FORM) { entry ->
                val viewModel = entry.sharedSignUpViewModel(navController)
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                SignUpFormScreen(
                    state = state,
                    onDniChange = viewModel::onDniChange,
                    onEmailChange = viewModel::onEmailChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onPasswordConfirmationChange = viewModel::onPasswordConfirmationChange,
                    onValidateIdentity = {
                        if (viewModel.validateForm()) navController.navigate(Route.SIGN_UP_CONSENT)
                    },
                    onGoToLogin = { navController.navigate(Route.SIGN_IN) },
                )
            }

            composable(Route.SIGN_UP_CONSENT) {
                SignUpConsentScreen(
                    onBack = { navController.popBackStack() },
                    onStartValidation = { navController.navigate(Route.SIGN_UP_CAPTURE) },
                )
            }

            composable(Route.SIGN_UP_CAPTURE) { entry ->
                val viewModel = entry.sharedSignUpViewModel(navController)
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // El registro terminó: se sale de la captura sin dejarla en el back stack.
                LaunchedEffect(state.status) {
                    if (state.status is SignUpStatus.Registered) {
                        navController.navigate(Route.SIGN_UP_SUCCESS) {
                            popUpTo(Route.SIGN_UP_CAPTURE) { inclusive = true }
                        }
                    }
                }

                SignUpCaptureScreen(
                    state = state,
                    onBack = {
                        viewModel.onCaptureRestarted()
                        navController.popBackStack()
                    },
                    onCapture = { target ->
                        when (target) {
                            CaptureTarget.DOCUMENT -> navController.navigate(Route.SIGN_UP_DOCUMENT_SCAN)
                            // La selfie no tiene obturador: va a la pantalla de prueba de vida.
                            CaptureTarget.SELFIE -> navController.navigate(Route.SIGN_UP_LIVENESS)
                        }
                    },
                    onSubmit = viewModel::submit,
                    onErrorDismissed = viewModel::onErrorDismissed,
                    onEditForm = {
                        viewModel.onErrorDismissed()
                        navController.popBackStack(Route.SIGN_UP_FORM, inclusive = false)
                    },
                )
            }

            composable(Route.SIGN_UP_DOCUMENT_SCAN) { entry ->
                val viewModel = entry.sharedSignUpViewModel(navController)

                CameraPermissionGate(onDenied = { navController.popBackStack() }) {
                    DocumentScanScreen(
                        scanSession = viewModel.documentScanSession,
                        onScanned = { document ->
                            viewModel.onDocumentScanned(document)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }

            composable(Route.SIGN_UP_LIVENESS) { entry ->
                val viewModel = entry.sharedSignUpViewModel(navController)

                CameraPermissionGate(onDenied = { navController.popBackStack() }) {
                    LivenessCaptureScreen(
                        livenessSession = viewModel.livenessSession,
                        onCaptured = { image, livenessPassed ->
                            viewModel.onSelfieCaptured(image, livenessPassed)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }

            composable(Route.SIGN_UP_SUCCESS) {
                SignUpSuccessScreen(
                    onFinish = {
                        // US-01 no deja sesión iniciada: la cuenta quedó creada, pero hay
                        // que ingresar con las credenciales recién elegidas.
                        navController.navigate(Route.SIGN_IN) {
                            popUpTo(Route.SIGN_UP_GRAPH) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

/** Pantallas donde no hacer nada aunque no haya sesión: son justamente las que no la requieren. */
private fun isPublicRoute(route: String?): Boolean =
    route == null ||
        route == Route.SPLASH ||
        route == Route.WELCOME ||
        route == Route.SIGN_IN ||
        route.startsWith(Route.SIGN_UP_GRAPH)

/**
 * El formulario, el consentimiento y la captura comparten un único ViewModel scopeado
 * al nav graph `signup`, así el estado sobrevive a la navegación entre pasos.
 */
@Composable
private fun NavBackStackEntry.sharedSignUpViewModel(navController: NavHostController): SignUpViewModel {
    val parentEntry = remember(this) { navController.getBackStackEntry(Route.SIGN_UP_GRAPH) }
    return hiltViewModel(parentEntry)
}

/**
 * `ScanPlateScreen` y `ReportChatScreen` comparten un único ViewModel scopeado al nav graph
 * `scan`: el `pat_enc` que devuelve el check nunca sale de memoria del ViewModel hacia un
 * argumento de navegación serializado -- mismo criterio Zero-Retention que el resto de la app.
 */
@Composable
private fun NavBackStackEntry.sharedScanViewModel(navController: NavHostController): ScanViewModel {
    val parentEntry = remember(this) { navController.getBackStackEntry(Route.SCAN_GRAPH) }
    return hiltViewModel(parentEntry)
}
