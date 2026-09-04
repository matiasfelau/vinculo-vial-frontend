package ar.felaufernandezbottaro.vinculovial.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.R
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy

/** Campo del formulario: etiqueta en versalitas arriba, caja menta redondeada (wireframe). */
@Composable
fun VvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    errorText: String? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            isError = errorText != null,
            placeholder = {
                Text(
                    text = placeholder,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            visualTransformation = when {
                !isPassword || passwordVisible -> VisualTransformation.None
                else -> PasswordVisualTransformation()
            },
            keyboardOptions = keyboardOptions,
            trailingIcon = if (!isPassword) null else {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                            tint = VvNavy,
                        )
                    }
                }
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = VvMint,
                unfocusedContainerColor = VvMint,
                disabledContainerColor = VvMint.copy(alpha = 0.5f),
                errorContainerColor = VvMint,
                focusedBorderColor = VvNavy,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

/** Botón principal navy de ancho completo. */
@Composable
fun VvPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VvNavy,
            contentColor = Color.White,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Botón circular navy (flecha de volver, cámara). */
@Composable
fun VvCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 44,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = VvNavy,
        modifier = modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size((size * 0.5).dp),
            )
        }
    }
}

/**
 * Barra inferior menta con el isotipo, presente en todas las pantallas del wireframe.
 *
 * A diferencia de un `NavigationBar`/`BottomAppBar` de Material, este `Box` es propio y no
 * consume ningún inset por su cuenta: sin `navigationBarsPadding()`, `Scaffold` reserva sólo
 * su alto intrínseco para el contenido y la barra de navegación del sistema se dibuja encima,
 * tapando el isotipo.
 */
@Composable
fun VvBottomBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(VvMint)
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        VvLogoBadge()
    }
}

/** Isotipo circular: el logo real "MiniViVi". */
@Composable
fun VvLogoBadge(modifier: Modifier = Modifier, size: Int = 36) {
    Image(
        painter = painterResource(R.drawable.mini_vivi),
        contentDescription = "Vínculo Vial",
        modifier = modifier.size(size.dp),
    )
}

/** Logotipo "ViVi" real de la marca, usado en Splash y en la bienvenida. */
@Composable
fun VvWordmark(modifier: Modifier = Modifier, width: Dp = 220.dp) {
    Image(
        painter = painterResource(R.drawable.logo_vivi),
        contentDescription = "Vínculo Vial",
        modifier = modifier.width(width),
    )
}

/** Fila de acciones separadas de forma pareja (usada en pantallas de resultado). */
@Composable
fun VvActionRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
