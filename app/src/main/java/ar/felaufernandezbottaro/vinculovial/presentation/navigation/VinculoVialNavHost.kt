package ar.felaufernandezbottaro.vinculovial.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import ar.felaufernandezbottaro.vinculovial.presentation.splash.SplashScreen

object Route {
    const val SPLASH = "splash"
    const val SIGN_IN = "signin"
    const val SETTINGS = "settings"
    const val SIGN_UP_GRAPH = "signup"
    const val SIGN_UP_FORM = "signup/form"
    const val SIGN_UP_CONSENT = "signup/consent"
    const val SIGN_UP_CAPTURE = "signup/capture"
    const val SIGN_UP_SUCCESS = "signup/success"

    const val SIGN_UP_DOCUMENT_SCAN = "signup/document-scan"
    const val SIGN_UP_LIVENESS = "signup/liveness"
}

@Composable
fun VinculoVialNavHost(navController: NavHostController = rememberNavController()) {
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
    LaunchedEffect(hasSession, currentEntry) {
        if (!hasSession && !isPublicRoute(currentEntry?.destination?.route)) {
            navController.navigate(Route.SIGN_IN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Route.SPLASH) {

        composable(Route.SPLASH) {
            SplashScreen(
                onFinished = {
                    // El login pasa a ser la puerta de entrada de la app (US-02). El caso
                    // "ya hay una sesión guardada" queda pendiente de una Home/Dashboard que
                    // todavía no existe (fuera del alcance de este CU) — por ahora se
                    // aterriza siempre en Login, sin distinguir si hay sesión o no.
                    navController.navigate(Route.SIGN_IN) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
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

            SettingsScreen(state = state, onLogout = viewModel::logout)
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
                        // Vuelve al formulario limpio: US-01 no deja sesión iniciada.
                        navController.navigate(Route.SIGN_UP_GRAPH) {
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
    route == null || route == Route.SPLASH || route == Route.SIGN_IN || route.startsWith(Route.SIGN_UP_GRAPH)

/**
 * El formulario, el consentimiento y la captura comparten un único ViewModel scopeado
 * al nav graph `signup`, así el estado sobrevive a la navegación entre pasos.
 */
@Composable
private fun NavBackStackEntry.sharedSignUpViewModel(navController: NavHostController): SignUpViewModel {
    val parentEntry = remember(this) { navController.getBackStackEntry(Route.SIGN_UP_GRAPH) }
    return hiltViewModel(parentEntry)
}
