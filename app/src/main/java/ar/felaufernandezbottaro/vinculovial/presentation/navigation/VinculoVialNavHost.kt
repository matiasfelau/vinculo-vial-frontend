package ar.felaufernandezbottaro.vinculovial.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import ar.felaufernandezbottaro.vinculovial.data.session.SessionState
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountError
import ar.felaufernandezbottaro.vinculovial.presentation.account.DeleteAccountScreen
import ar.felaufernandezbottaro.vinculovial.presentation.account.DeleteAccountStatus
import ar.felaufernandezbottaro.vinculovial.presentation.account.DeleteAccountViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.camera.CameraPermissionGate
import ar.felaufernandezbottaro.vinculovial.presentation.camera.DocumentScanScreen
import ar.felaufernandezbottaro.vinculovial.presentation.camera.LivenessCaptureScreen
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxDetailScreen
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxEvent
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxScreen
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxTab
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.InboxViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.push.NotificationPermissionRequester
import ar.felaufernandezbottaro.vinculovial.presentation.recovery.RecoveryCodeScreen
import ar.felaufernandezbottaro.vinculovial.presentation.recovery.RecoveryDniScreen
import ar.felaufernandezbottaro.vinculovial.presentation.recovery.RecoveryDoneScreen
import ar.felaufernandezbottaro.vinculovial.presentation.recovery.RecoveryEvent
import ar.felaufernandezbottaro.vinculovial.presentation.recovery.RecoveryPasswordScreen
import ar.felaufernandezbottaro.vinculovial.presentation.recovery.RecoveryViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.push.PushTarget
import ar.felaufernandezbottaro.vinculovial.presentation.push.planPushNavigation
import ar.felaufernandezbottaro.vinculovial.presentation.sent.SentChatScreen
import ar.felaufernandezbottaro.vinculovial.presentation.sent.SentEvent
import ar.felaufernandezbottaro.vinculovial.presentation.sent.SentViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.session.SessionViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.session.UnlockRoute
import ar.felaufernandezbottaro.vinculovial.presentation.session.UnlockViewModel
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
import ar.felaufernandezbottaro.vinculovial.presentation.signup.VerifyEmailEvent
import ar.felaufernandezbottaro.vinculovial.presentation.signup.VerifyEmailScreen
import ar.felaufernandezbottaro.vinculovial.presentation.signup.VerifyEmailViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.scan.EvidenceCaptureScreen
import ar.felaufernandezbottaro.vinculovial.presentation.scan.EvidencePhase
import ar.felaufernandezbottaro.vinculovial.presentation.scan.ReportChatScreen
import ar.felaufernandezbottaro.vinculovial.presentation.scan.ScanPlateScreen
import ar.felaufernandezbottaro.vinculovial.presentation.scan.ScanStatus
import ar.felaufernandezbottaro.vinculovial.presentation.scan.ScanViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.splash.SplashScreen
import ar.felaufernandezbottaro.vinculovial.presentation.vehicle.MyVehiclesScreen
import ar.felaufernandezbottaro.vinculovial.presentation.vehicle.VehiclesViewModel
import ar.felaufernandezbottaro.vinculovial.presentation.welcome.WelcomeScreen

object Route {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val SIGN_IN = "signin"
    const val UNLOCK = "unlock"
    const val SETTINGS = "settings"
    const val DELETE_ACCOUNT = "settings/delete-account"
    const val SIGN_UP_GRAPH = "signup"
    const val SIGN_UP_FORM = "signup/form"
    const val SIGN_UP_VERIFY_EMAIL = "signup/verify-email"
    const val SIGN_UP_CONSENT = "signup/consent"
    const val SIGN_UP_CAPTURE = "signup/capture"
    const val SIGN_UP_SUCCESS = "signup/success"

    const val SIGN_UP_DOCUMENT_SCAN = "signup/document-scan"
    const val SIGN_UP_LIVENESS = "signup/liveness"

    const val RECOVERY_GRAPH = "recovery"
    const val RECOVERY_DNI = "recovery/dni"
    const val RECOVERY_CODE = "recovery/code"
    const val RECOVERY_PASSWORD = "recovery/password"
    const val RECOVERY_DOCUMENT_SCAN = "recovery/document-scan"
    const val RECOVERY_LIVENESS = "recovery/liveness"
    const val RECOVERY_DONE = "recovery/done"

    const val VEHICLES = "vehicles"

    const val SCAN_GRAPH = "scan"
    const val SCAN_PLATE = "scan/plate"
    const val REPORT_CHAT = "scan/report-chat"
    const val EVIDENCE_CAPTURE = "scan/evidence-capture"

    const val INBOX = "inbox"
    const val INBOX_DETAIL = "inbox/detail"
    const val SENT_CHAT = "inbox/sent-chat"
}

@Composable
fun VinculoVialNavHost(
    navController: NavHostController = rememberNavController(),
    pendingPush: PushTarget? = null,
    onPendingPushConsumed: () -> Unit = {},
) {
    // La sesión puede cambiar en segundo plano, sin que la pantalla que disparó la request
    // original se entere -- SessionManager ni sabe qué pantalla es: un RTR fallido la borra, el
    // bloqueo (US-12) la deja en LOCKED, el login o el desbloqueo la pasan a UNLOCKED. Este
    // observer es el único lugar que reacciona y aplica la regla de mundos (#9) -- ver
    // worldRedirect --, sea cual sea la pantalla en la que esté el usuario. Toda transición entre
    // mundos vacía el back stack, así que Back nunca muestra una pantalla de otro mundo.
    val sessionViewModel = hiltViewModel<SessionViewModel>()
    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()

    // Se pide el permiso de notificaciones una única vez, recién tras un login exitoso -- ver
    // NotificationPermissionRequester. Volver a Settings desde otra pantalla no lo vuelve a
    // disparar: se consume apenas se compone.
    var justSignedIn by remember { mutableStateOf(false) }
    LaunchedEffect(sessionState, currentEntry) {
        // La ruta se lee en vivo y no de `currentEntry`: otra navegación de esta misma pasada (el
        // plan del push, por ejemplo) pudo cambiarla antes de que este efecto corra.
        val target = worldRedirect(sessionState, navController.currentDestination?.route)
        if (target != null) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Al tocar una notificación se reconstruye el stack (Settings antes de Inbox, nunca Inbox
    // sola) en vez de navegar directo -- ver planPushNavigation. Sin sesión desbloqueada todavía,
    // el efecto de arriba ya se encarga de mandar a Login (sin sesión) o a Desbloqueo (bloqueada);
    // esta misma clave (sessionState) hace que el plan se reevalúe apenas la sesión esté
    // desbloqueada, así que un push tocado con la app bloqueada pide la huella y después abre el
    // buzón. El destino no se consume acá sino en el buzón, que es quien abre el chat concreto
    // una vez cargada la lista.
    LaunchedEffect(sessionState, pendingPush) {
        val plan = planPushNavigation(sessionState == SessionState.UNLOCKED, pendingPush)
        if (plan.isNotEmpty()) {
            navController.navigate(plan.first()) {
                popUpTo(0) { inclusive = true }
            }
            plan.drop(1).forEach { route -> navController.navigate(route) }
        }
    }

    NavHost(navController = navController, startDestination = Route.SPLASH) {

        composable(Route.SPLASH) {
            SplashScreen(
                onFinished = {
                    // El Splash decide el mundo: sesión bloqueada -> Desbloqueo; sin sesión ->
                    // Bienvenida. El estado se lee ahora, no al componer: pudo cambiar mientras
                    // se mostraba el Splash.
                    navController.navigate(splashDestination(sessionViewModel.sessionState.value)) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.UNLOCK) { entry ->
            val viewModel = hiltViewModel<UnlockViewModel>(entry)

            // "Usar contraseña" lleva a Login con la sesión todavía bloqueada: el blob se borra
            // recién cuando ese login sale bien (SecureTokenStore.startSession).
            UnlockRoute(
                viewModel = viewModel,
                onUsePassword = {
                    navController.navigate(Route.SIGN_IN) { popUpTo(0) { inclusive = true } }
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
            val notice by viewModel.notice.collectAsStateWithLifecycle()

            // Settings hace de Home tras el login. El salto lo hace el observer de mundos de
            // arriba (la sesión pasa a UNLOCKED y Login es del mundo público), con el back stack
            // vacío: acá sólo se marca el login para pedir el permiso de notificaciones.
            LaunchedEffect(state.status) {
                if (state.status is SignInStatus.SignedIn) justSignedIn = true
            }

            SignInScreen(
                state = state,
                notice = notice,
                onDniChange = viewModel::onDniChange,
                onPasswordChange = viewModel::onPasswordChange,
                onSubmit = viewModel::submit,
                onGoToSignUp = { navController.navigate(Route.SIGN_UP_GRAPH) },
                onForgotPassword = { navController.navigate(Route.RECOVERY_GRAPH) },
            )
        }

        composable(Route.SETTINGS) { entry ->
            val viewModel = hiltViewModel<SettingsViewModel>(entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            SettingsScreen(
                // El ofrecimiento de la huella espera a que termine el pedido de permiso de
                // notificaciones: dos diálogos a la vez no se entienden.
                state = state.copy(showBiometricOffer = state.showBiometricOffer && !justSignedIn),
                onLogout = viewModel::logout,
                onOpenVehicles = { navController.navigate(Route.VEHICLES) },
                onScanPlate = { navController.navigate(Route.SCAN_GRAPH) },
                onOpenInbox = { navController.navigate(Route.INBOX) },
                onDeleteAccount = { navController.navigate(Route.DELETE_ACCOUNT) },
                onBiometricToggle = viewModel::onBiometricToggle,
                onBiometricOfferDismissed = viewModel::onBiometricOfferDismissed,
                onBiometricActivationFailureDismissed = viewModel::onBiometricActivationFailureDismissed,
            )

            if (justSignedIn) {
                NotificationPermissionRequester(onFinished = { justSignedIn = false })
            }
        }

        composable(Route.DELETE_ACCOUNT) { entry ->
            val viewModel = hiltViewModel<DeleteAccountViewModel>(entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // Esta pantalla es la que decide a dónde ir cuando la sesión desaparece -- ver
            // managesOwnSessionLoss --, con sus dos salidas, ambas sin dejar nada en el back stack:
            // la baja hecha vuelve a la bienvenida; la sesión vencida, a Login como en cualquier
            // otra pantalla.
            LaunchedEffect(state.status) {
                when (val status = state.status) {
                    DeleteAccountStatus.Deleted -> navController.navigate(Route.WELCOME) {
                        popUpTo(0) { inclusive = true }
                    }
                    is DeleteAccountStatus.Failed -> if (status.error == DeleteAccountError.SessionExpired) {
                        navController.navigate(Route.SIGN_IN) { popUpTo(0) { inclusive = true } }
                    }
                    DeleteAccountStatus.Idle,
                    DeleteAccountStatus.Submitting,
                    -> Unit
                }
            }

            DeleteAccountScreen(
                state = state,
                onPasswordChange = viewModel::onPasswordChange,
                onConfirm = viewModel::submit,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.INBOX) { entry ->
            val viewModel = hiltViewModel<InboxViewModel>(entry)
            val sentViewModel = hiltViewModel<SentViewModel>(entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val sentState by sentViewModel.uiState.collectAsStateWithLifecycle()

            // Un push de novedades al emisor abre directamente en "Enviados": al volver del chat
            // se queda ahí, no en "Recibidos".
            var selectedTab by rememberSaveable {
                mutableStateOf(if (pendingPush is PushTarget.SentUpdated) InboxTab.SENT else InboxTab.RECEIVED)
            }

            // El destino del push se consume acá, con el buzón ya en pantalla: cada ViewModel abre
            // el chat apenas tiene la alerta cargada y avisa por `events`.
            LaunchedEffect(pendingPush) {
                when (val target = pendingPush) {
                    is PushTarget.NewAlert -> target.alertId?.let(viewModel::openAlertFromPush)
                    is PushTarget.SentUpdated -> {
                        selectedTab = InboxTab.SENT
                        val alertId = target.alertId
                        if (alertId != null) sentViewModel.openChatFromPush(alertId) else sentViewModel.refresh()
                    }
                    PushTarget.InboxUpdated -> {
                        selectedTab = InboxTab.RECEIVED
                        viewModel.refresh()
                    }
                    null -> Unit
                }
                if (pendingPush != null) onPendingPushConsumed()
            }
            LaunchedEffect(viewModel) {
                viewModel.events.collect { event ->
                    when (event) {
                        InboxEvent.OpenDetail -> navController.navigate(Route.INBOX_DETAIL)
                    }
                }
            }
            LaunchedEffect(sentViewModel) {
                sentViewModel.events.collect { event ->
                    when (event) {
                        SentEvent.OpenChat -> navController.navigate(Route.SENT_CHAT)
                    }
                }
            }

            InboxScreen(
                uiState = state,
                sentState = sentState,
                selectedTab = selectedTab,
                onSelectTab = { tab ->
                    selectedTab = tab
                    // Entrar a Enviados trae el estado actual: cambia solo del lado del servidor.
                    if (tab == InboxTab.SENT) sentViewModel.refresh()
                },
                onRetry = viewModel::loadFirstPage,
                onLoadNextPage = viewModel::loadNextPage,
                onRetryNextPage = viewModel::retryNextPage,
                onOpenAlert = { alertId ->
                    viewModel.openAlert(alertId)
                    navController.navigate(Route.INBOX_DETAIL)
                },
                onSentRetry = sentViewModel::refresh,
                onSentLoadNextPage = sentViewModel::loadNextPage,
                onSentRetryNextPage = sentViewModel::retryNextPage,
                onOpenSent = { alertId ->
                    sentViewModel.openChat(alertId)
                    navController.navigate(Route.SENT_CHAT)
                },
            )
        }

        composable(Route.SENT_CHAT) { entry ->
            // El mismo `SentViewModel` que la lista: el chat se arma con la alerta seleccionada.
            val parentEntry = remember(entry) { navController.getBackStackEntry(Route.INBOX) }
            val viewModel = hiltViewModel<SentViewModel>(parentEntry)
            val chat by viewModel.chat.collectAsStateWithLifecycle()

            DisposableEffect(Unit) { onDispose { viewModel.closeChat() } }

            // Sin chat abierto (p. ej. tras recrearse el proceso) no hay nada que mostrar; mismo
            // criterio que el chat del dueño: sólo se sale si el destino está en primer plano.
            val current = chat
            if (current == null) {
                LaunchedEffect(Unit) {
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) navController.popBackStack()
                }
            } else {
                SentChatScreen(alert = current, onBack = { navController.popBackStack() })
            }
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

        composable(Route.VEHICLES) { entry ->
            val viewModel = hiltViewModel<VehiclesViewModel>(entry)
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            MyVehiclesScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onLink = viewModel::onLinkClicked,
                onUnlink = viewModel::onUnlinkClicked,
                onUnlinkAll = viewModel::onUnlinkAllClicked,
                onRetry = viewModel::load,
                onPlateChange = viewModel::onPlateChange,
                onPlateContinue = viewModel::onPlateContinue,
                onPlateBack = viewModel::onPlateBack,
                onPlateConfirm = viewModel::onPlateConfirmed,
                onUnlinkAllConfirm = viewModel::onUnlinkAllConfirmed,
                onDialogDismiss = viewModel::onDialogDismissed,
            )
        }

        navigation(startDestination = Route.SCAN_PLATE, route = Route.SCAN_GRAPH) {

            composable(Route.SCAN_PLATE) { entry ->
                val viewModel = entry.sharedScanViewModel(navController)
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // El intento contra /alerts/attempts terminó: el catálogo de incidentes y el
                // envío del reporte viven en ReportChatScreen, no en un menú flotante sobre
                // la cámara. Se saca ScanPlateScreen del back stack al navegar: no tiene
                // sentido volver a la cámara en vivo una vez identificado el vehículo. Con el cupo
                // diario ocupado (US-16) también se abre el chat: ViVIAna lo explica ahí.
                LaunchedEffect(state.status) {
                    if (state.status is ScanStatus.AwaitingAlertType || state.status is ScanStatus.DailyLimitReached) {
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
                        if (viewModel.validateForm()) {
                            // Con el email ya verificado (volvió atrás sin tocarlo) no se pide otro código.
                            val next = if (state.isEmailVerified) Route.SIGN_UP_CONSENT else Route.SIGN_UP_VERIFY_EMAIL
                            navController.navigate(next)
                        }
                    },
                    onGoToLogin = { navController.navigate(Route.SIGN_IN) },
                )
            }

            composable(Route.SIGN_UP_VERIFY_EMAIL) { entry ->
                val signUpViewModel = entry.sharedSignUpViewModel(navController)
                val signUpState by signUpViewModel.uiState.collectAsStateWithLifecycle()
                val viewModel = hiltViewModel<VerifyEmailViewModel>(entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // Pide el código al email del formulario; con el mismo email no vuelve a pedir otro.
                LaunchedEffect(signUpState.form.email) { viewModel.start(signUpState.form.email) }

                // El token pasa al SignUpViewModel, que lo guarda sólo en memoria. Se sigue al
                // consentimiento sacando esta pantalla del stack: atrás vuelve al formulario.
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is VerifyEmailEvent.Verified -> {
                                signUpViewModel.onEmailVerified(event.email, event.token)
                                navController.navigate(Route.SIGN_UP_CONSENT) {
                                    popUpTo(Route.SIGN_UP_VERIFY_EMAIL) { inclusive = true }
                                }
                            }
                        }
                    }
                }

                VerifyEmailScreen(
                    state = state,
                    onCodeChange = viewModel::onCodeChange,
                    onConfirm = viewModel::confirm,
                    onResend = viewModel::resend,
                    onBack = { navController.popBackStack() },
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
                    // EMAIL_NOT_VERIFIED: el token venció o no corresponde. Se vuelve a verificar el
                    // email sobre el formulario, sin dejar el consentimiento ni la captura en el stack.
                    onVerifyEmail = {
                        viewModel.onErrorDismissed()
                        navController.navigate(Route.SIGN_UP_VERIFY_EMAIL) {
                            popUpTo(Route.SIGN_UP_FORM) { inclusive = false }
                        }
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

        // Recuperación de contraseña (US-13): mundo público, con un único ViewModel scopeado al grafo.
        navigation(startDestination = Route.RECOVERY_DNI, route = Route.RECOVERY_GRAPH) {

            composable(Route.RECOVERY_DNI) { entry ->
                val viewModel = entry.sharedRecoveryViewModel(navController)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                RecoveryEventsEffect(viewModel, navController)

                RecoveryDniScreen(
                    state = state,
                    onDniChange = viewModel::onDniChange,
                    onSubmit = viewModel::requestCode,
                    onBack = { navController.popBackStack(Route.SIGN_IN, inclusive = false) },
                )
            }

            composable(Route.RECOVERY_CODE) { entry ->
                val viewModel = entry.sharedRecoveryViewModel(navController)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                RecoveryEventsEffect(viewModel, navController)

                RecoveryCodeScreen(
                    state = state,
                    onCodeChange = viewModel::onCodeChange,
                    onConfirm = viewModel::confirmCode,
                    onResend = viewModel::resend,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Route.RECOVERY_PASSWORD) { entry ->
                val viewModel = entry.sharedRecoveryViewModel(navController)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                RecoveryEventsEffect(viewModel, navController)

                RecoveryPasswordScreen(
                    state = state,
                    onPasswordChange = viewModel::onPasswordChange,
                    onPasswordConfirmationChange = viewModel::onPasswordConfirmationChange,
                    onStartCapture = {
                        if (viewModel.validatePassword()) navController.navigate(Route.RECOVERY_DOCUMENT_SCAN)
                    },
                    onErrorDismissed = viewModel::onErrorDismissed,
                    onEditPassword = viewModel::onErrorDismissed,
                    onRequestNewCode = viewModel::onRestartRequested,
                    onBack = {
                        viewModel.onCaptureCancelled()
                        navController.popBackStack()
                    },
                )
            }

            composable(Route.RECOVERY_DOCUMENT_SCAN) { entry ->
                val viewModel = entry.sharedRecoveryViewModel(navController)

                CameraPermissionGate(onDenied = { navController.popBackStack() }) {
                    DocumentScanScreen(
                        scanSession = viewModel.documentScanSession,
                        onScanned = { document ->
                            // Si el DNI escaneado no es el ingresado, se vuelve a la contraseña con el
                            // aviso y sin llamar al backend; si coincide, sigue la selfie.
                            if (viewModel.onDocumentScanned(document)) {
                                navController.navigate(Route.RECOVERY_LIVENESS) {
                                    popUpTo(Route.RECOVERY_DOCUMENT_SCAN) { inclusive = true }
                                }
                            } else {
                                navController.popBackStack()
                            }
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }

            composable(Route.RECOVERY_LIVENESS) { entry ->
                val viewModel = entry.sharedRecoveryViewModel(navController)

                CameraPermissionGate(
                    onDenied = {
                        viewModel.onCaptureCancelled()
                        navController.popBackStack()
                    },
                ) {
                    LivenessCaptureScreen(
                        livenessSession = viewModel.livenessSession,
                        // Con las dos capturas el envío arranca solo; la pantalla de la contraseña
                        // muestra el progreso o el error.
                        onCaptured = { image, livenessPassed ->
                            viewModel.onSelfieCaptured(image, livenessPassed)
                            navController.popBackStack()
                        },
                        onCancel = {
                            viewModel.onCaptureCancelled()
                            navController.popBackStack()
                        },
                    )
                }
            }

            composable(Route.RECOVERY_DONE) {
                RecoveryDoneScreen(
                    onGoToLogin = {
                        // Salida del mundo de recuperación: la sesión ya se borró y Login lleva el aviso.
                        navController.navigate(Route.SIGN_IN) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
        }
    }
}

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

/** Todas las pantallas del grafo `recovery` comparten el ViewModel: el de la entrada del grafo. */
@Composable
private fun NavBackStackEntry.sharedRecoveryViewModel(navController: NavHostController): RecoveryViewModel {
    val parentEntry = remember(this) { navController.getBackStackEntry(Route.RECOVERY_GRAPH) }
    return hiltViewModel(parentEntry)
}

/**
 * Traduce los eventos del `RecoveryViewModel` en navegación. Lo usa cada pantalla del grafo: sólo
 * hay una compuesta a la vez, y la que está en pantalla es la que originó el evento.
 */
@Composable
private fun RecoveryEventsEffect(viewModel: RecoveryViewModel, navController: NavHostController) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RecoveryEvent.CodeRequested -> navController.navigate(Route.RECOVERY_CODE)
                RecoveryEvent.CodeConfirmed -> navController.navigate(Route.RECOVERY_PASSWORD)
                RecoveryEvent.RestartRequested -> navController.popBackStack(Route.RECOVERY_DNI, inclusive = false)
                // La recuperación terminó: ya no hay a dónde volver dentro del grafo.
                RecoveryEvent.Completed -> navController.navigate(Route.RECOVERY_DONE) {
                    popUpTo(Route.RECOVERY_DNI) { inclusive = true }
                }
            }
        }
    }
}
