package ar.felaufernandezbottaro.vinculovial.presentation.inbox

/** Un efecto único de [InboxViewModel] que la navegación tiene que ejecutar. */
sealed interface InboxEvent {

    /** Abrir el chat del dueño de la alerta que quedó seleccionada. */
    data object OpenDetail : InboxEvent
}
