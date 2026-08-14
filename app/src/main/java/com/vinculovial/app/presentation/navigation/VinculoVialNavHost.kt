package com.vinculovial.app.presentation.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.vinculovial.app.presentation.camera.CameraCaptureScreen
import com.vinculovial.app.presentation.camera.CameraCaptureSpec
import com.vinculovial.app.presentation.camera.CameraPermissionGate
import com.vinculovial.app.presentation.camera.CaptureGuide
import com.vinculovial.app.presentation.signup.CaptureTarget
import com.vinculovial.app.presentation.signup.SignUpCaptureScreen
import com.vinculovial.app.presentation.signup.SignUpConsentScreen
import com.vinculovial.app.presentation.signup.SignUpFormScreen
import com.vinculovial.app.presentation.signup.SignUpStatus
import com.vinculovial.app.presentation.signup.SignUpSuccessScreen
import com.vinculovial.app.presentation.signup.SignUpViewModel
import com.vinculovial.app.presentation.splash.SplashScreen

object Route {
    const val SPLASH = "splash"
    const val SIGN_UP_GRAPH = "signup"
    const val SIGN_UP_FORM = "signup/form"
    const val SIGN_UP_CONSENT = "signup/consent"
    const val SIGN_UP_CAPTURE = "signup/capture"
    const val SIGN_UP_SUCCESS = "signup/success"

    const val CAMERA_TARGET_ARG = "target"
    const val SIGN_UP_CAMERA = "signup/camera"

    fun camera(target: CaptureTarget) = "$SIGN_UP_CAMERA/${target.name}"
}

@Composable
fun VinculoVialNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Route.SPLASH) {

        composable(Route.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Route.SIGN_UP_GRAPH) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
            )
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
                    onGoToLogin = {
                        // El login es US-02; por ahora sólo avisamos.
                        Toast.makeText(context, "El inicio de sesión llega en la próxima entrega.", Toast.LENGTH_SHORT).show()
                    },
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
                    onCaptureDni = { navController.navigate(Route.camera(CaptureTarget.DNI)) },
                    onCaptureSelfie = { navController.navigate(Route.camera(CaptureTarget.SELFIE)) },
                    onSubmit = viewModel::submit,
                    onErrorDismissed = viewModel::onErrorDismissed,
                    onEditForm = {
                        viewModel.onErrorDismissed()
                        navController.popBackStack(Route.SIGN_UP_FORM, inclusive = false)
                    },
                )
            }

            composable(
                route = "${Route.SIGN_UP_CAMERA}/{${Route.CAMERA_TARGET_ARG}}",
                arguments = listOf(navArgument(Route.CAMERA_TARGET_ARG) { type = NavType.StringType }),
            ) { entry ->
                val viewModel = entry.sharedSignUpViewModel(navController)
                val target = remember(entry) {
                    CaptureTarget.valueOf(
                        entry.arguments?.getString(Route.CAMERA_TARGET_ARG) ?: CaptureTarget.DNI.name,
                    )
                }

                CameraPermissionGate(onDenied = { navController.popBackStack() }) {
                    CameraCaptureScreen(
                        spec = target.toCaptureSpec(),
                        onCaptured = { image ->
                            viewModel.onImageCaptured(target, image)
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

/** Cómo se le pide cada foto al usuario. */
private fun CaptureTarget.toCaptureSpec(): CameraCaptureSpec = when (this) {
    CaptureTarget.DNI -> CameraCaptureSpec(
        title = "Frente del DNI",
        instruction = "Encuadrá el frente del documento dentro del recuadro, con buena luz y sin reflejos.",
        useFrontLens = false,
        guide = CaptureGuide.DOCUMENT,
    )

    CaptureTarget.SELFIE -> CameraCaptureSpec(
        title = "Selfie",
        instruction = "Mirá de frente a la cámara, sin anteojos de sol ni gorra.",
        useFrontLens = true,
        guide = CaptureGuide.FACE,
    )
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
