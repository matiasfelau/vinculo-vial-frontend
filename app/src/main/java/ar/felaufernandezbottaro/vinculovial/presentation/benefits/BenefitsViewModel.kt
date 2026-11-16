package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit
import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitError
import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitsRepository
import ar.felaufernandezbottaro.vinculovial.domain.benefits.ClaimResult
import ar.felaufernandezbottaro.vinculovial.domain.benefits.GetBenefitsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** El estado del pedido del código en el detalle. */
sealed interface ClaimState {
    data object Idle : ClaimState
    data object Claiming : ClaimState
    data class Failed(val error: BenefitError) : ClaimState
}

data class BenefitsUiState(
    /** Las promociones vigentes. Vacía si todavía no cargó o si la carga falló: el carrusel no se muestra. */
    val benefits: List<Benefit> = emptyList(),

    /**
     * La promoción abierta en el detalle. Es una copia: sigue ahí aunque una recarga ya no la
     * traiga (por ejemplo, tras un `BENEFIT_NOT_FOUND`), para que el detalle pueda explicarlo.
     */
    val selected: Benefit? = null,

    val claim: ClaimState = ClaimState.Idle,
)

/**
 * Compartido entre el Home (carrusel) y el detalle, atado a la entrada de Home: el código que se
 * pide en el detalle queda visible en la lista apenas se vuelve.
 */
@HiltViewModel
class BenefitsViewModel @Inject constructor(
    private val repository: BenefitsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BenefitsUiState())
    val uiState: StateFlow<BenefitsUiState> = _uiState.asStateFlow()

    private val refreshInFlight = AtomicBoolean(false)
    private val claimInFlight = AtomicBoolean(false)

    init {
        refresh()
    }

    /** Vuelve a pedir la lista. Una falla la deja vacía: el carrusel desaparece, sin error. */
    fun refresh() {
        if (!refreshInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                when (val result = repository.getBenefits()) {
                    is GetBenefitsResult.Success -> _uiState.update { state ->
                        state.copy(
                            benefits = result.benefits,
                            selected = state.selected?.let { open -> result.benefits.firstOrNull { it.id == open.id } ?: open },
                        )
                    }
                    GetBenefitsResult.Failed -> _uiState.update { it.copy(benefits = emptyList()) }
                }
            } finally {
                refreshInFlight.set(false)
            }
        }
    }

    /** Los bytes del banner, o `null`. Los trae el repositorio, que los guarda en memoria por `ETag`. */
    suspend fun banner(id: String): ByteArray? = repository.getBanner(id)

    /** Abre el detalle de [id]. No hace nada si la promoción ya no está en la lista. */
    fun open(id: String) {
        val benefit = _uiState.value.benefits.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(selected = benefit, claim = ClaimState.Idle) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(selected = null, claim = ClaimState.Idle) }
    }

    /** "Obtener código". Idempotente en el servidor: si ya lo tenía, vuelve el mismo. */
    fun claim() {
        val benefit = _uiState.value.selected ?: return
        if (benefit.code != null) return
        if (!claimInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(claim = ClaimState.Claiming) }
                when (val result = repository.claim(benefit.id)) {
                    is ClaimResult.Success -> _uiState.update { state ->
                        val withCode = benefit.copy(code = result.code)
                        state.copy(
                            selected = withCode,
                            benefits = state.benefits.map { if (it.id == benefit.id) withCode else it },
                            claim = ClaimState.Idle,
                        )
                    }
                    is ClaimResult.Failed -> {
                        _uiState.update { it.copy(claim = ClaimState.Failed(result.error)) }
                        // La promoción ya no está vigente: la lista ya no debería mostrarla.
                        if (result.error == BenefitError.NotFound) {
                            claimInFlight.set(false)
                            refresh()
                        }
                    }
                }
            } finally {
                claimInFlight.set(false)
            }
        }
    }
}
