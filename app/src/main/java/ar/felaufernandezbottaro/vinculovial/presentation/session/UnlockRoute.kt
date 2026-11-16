package ar.felaufernandezbottaro.vinculovial.presentation.session

import android.content.Context
import android.content.ContextWrapper
import ar.felaufernandezbottaro.vinculovial.ExcludeFromCoverage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Une el [UnlockViewModel] con el prompt biométrico, que necesita la Activity. Abre el prompt
 * apenas aparece la pantalla -- el usuario viene a desbloquear --, y el botón lo reabre si se
 * canceló o si no había red.
 */
@ExcludeFromCoverage(reason = "Une el ViewModel con BiometricPrompt, que necesita la Activity y la biometría del dispositivo; la pantalla (UnlockScreen) y el ViewModel sí se prueban")
@Composable
fun UnlockRoute(viewModel: UnlockViewModel, onUsePassword: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findFragmentActivity()

    val unlock = remember(viewModel, activity) {
        {
            viewModel.requestUnlock { cipher ->
                BiometricUnlockPrompter.show(
                    activity = activity,
                    cipher = cipher,
                    onAuthenticated = viewModel::onAuthenticated,
                    onFailed = viewModel::onPromptFailed,
                )
            }
        }
    }

    LaunchedEffect(unlock) { unlock() }

    UnlockScreen(state = state, onUnlock = unlock, onUsePassword = onUsePassword)
}

/** La Activity de Compose es una `FragmentActivity` (la exige `BiometricPrompt`); `MainActivity` lo es. */
private tailrec fun Context.findFragmentActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> error("El desbloqueo biométrico necesita una FragmentActivity")
}
