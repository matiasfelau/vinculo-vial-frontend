package ar.felaufernandezbottaro.vinculovial.presentation.signup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvBottomBar
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvPrimaryButton
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvGreen
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/** Cierre del flujo US-01: la cuenta quedó creada, pero todavía no hay sesión. */
@Composable
fun SignUpSuccessScreen(onFinish: () -> Unit) {
    // Punto sin retorno del flujo: dejar caer el botón físico "Atrás" al comportamiento
    // por defecto de navegación llevaría a la pantalla anterior en el back stack — con el
    // registro ya completado, eso significa reabrir una pantalla de un flujo que ya
    // terminó. Se intercepta y se hace exactamente lo mismo que "Finalizar": ir a Inicio
    // de Sesión con las credenciales recién creadas.
    BackHandler(onBack = onFinish)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VvBottomBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(VvGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }

            Text(
                text = "¡Cuenta creada!",
                style = MaterialTheme.typography.displaySmall,
                color = VvNavy,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )

            Text(
                text = "Tu identidad quedó validada y ya destruimos tus datos. Vas a poder " +
                    "ingresar con tu número de DNI y contraseña.",
                style = MaterialTheme.typography.bodyLarge,
                color = VvMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 32.dp),
            )

            VvPrimaryButton(text = "Finalizar", onClick = onFinish)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignUpSuccessPreview() {
    VinculoVialTheme {
        SignUpSuccessScreen(onFinish = {})
    }
}
