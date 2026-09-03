package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.ui.graphics.vector.ImageVector
import ar.felaufernandezbottaro.vinculovial.data.inbox.AlertTypeIconKind

/** Traduce la clasificación de ícono (libre de Compose) al `ImageVector` concreto que se dibuja. */
fun AlertTypeIconKind.toImageVector(): ImageVector = when (this) {
    AlertTypeIconKind.LUCES_ENCENDIDAS -> Icons.Filled.Lightbulb
    AlertTypeIconKind.PARKED_ON_GARAGE -> Icons.Filled.Garage
    AlertTypeIconKind.PARKED_TOO_CLOSE -> Icons.Filled.CompareArrows
    AlertTypeIconKind.CRASHED -> Icons.Filled.CarCrash
    AlertTypeIconKind.GENERIC -> Icons.Filled.ReportProblem
}
