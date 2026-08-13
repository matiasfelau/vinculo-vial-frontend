package com.vinculovial.app.presentation.signup

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
import com.vinculovial.app.presentation.components.VvBottomBar
import com.vinculovial.app.presentation.components.VvPrimaryButton
import com.vinculovial.app.presentation.theme.VinculoVialTheme
import com.vinculovial.app.presentation.theme.VvGreen
import com.vinculovial.app.presentation.theme.VvMuted
import com.vinculovial.app.presentation.theme.VvNavy

/** Cierre del flujo US-01: la cuenta quedó creada, pero todavía no hay sesión. */
@Composable
fun SignUpSuccessScreen(onFinish: () -> Unit) {
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
                text = "Tu identidad quedó validada y las imágenes ya se eliminaron del " +
                    "dispositivo. Vas a poder ingresar con tu email y contraseña.",
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
