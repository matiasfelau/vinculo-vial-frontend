package ar.felaufernandezbottaro.vinculovial.presentation.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import ar.felaufernandezbottaro.vinculovial.presentation.components.VvWordmark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 1_200L

/** "Carga de la Aplicación" del wireframe. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 600),
        label = "wordmark-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "wordmark-alpha",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(SPLASH_DURATION_MS)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VvMint),
        contentAlignment = Alignment.Center,
    ) {
        VvWordmark(
            modifier = Modifier
                .scale(scale)
                .alpha(alpha),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashPreview() {
    VinculoVialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VvMint),
            contentAlignment = Alignment.Center,
        ) {
            VvWordmark()
        }
    }
}
