package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.felaufernandezbottaro.vinculovial.data.edge.SecureBitmap
import ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMint
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMintDark
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvMuted
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VvNavy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Cada cuánto avanza solo el carrusel del Home. */
const val CAROUSEL_AUTO_ADVANCE_MS = 5_000L

/** La página que sigue a [current], dando la vuelta al final. */
internal fun nextCarouselPage(current: Int, pageCount: Int): Int =
    if (pageCount <= 0) 0 else (current + 1) % pageCount

/**
 * El carrusel de banners del Home, debajo de "Mis alertas". Avanza solo cada
 * [autoAdvanceMillis] y se detiene mientras el usuario lo toca o lo arrastra. Sin beneficios no
 * dibuja nada: ni error ni espacio vacío.
 */
@Composable
fun BenefitsCarousel(
    benefits: List<Benefit>,
    loadBanner: suspend (String) -> ByteArray?,
    onOpenBenefit: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoAdvanceMillis: Long = CAROUSEL_AUTO_ADVANCE_MS,
) {
    if (benefits.isEmpty()) return

    val pagerState = rememberPagerState { benefits.size }
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    val touching = pressed || dragged

    // Se reinicia cada vez que el usuario suelta (o cambia la lista): la cuenta de 5 s arranca de cero.
    LaunchedEffect(touching, benefits.size) {
        if (touching || benefits.size < 2) return@LaunchedEffect
        while (true) {
            delay(autoAdvanceMillis)
            pagerState.animateScrollToPage(nextCarouselPage(pagerState.currentPage, benefits.size))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CAROUSEL_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            key = { benefits[it].id },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val benefit = benefits[page]
            BenefitBanner(
                benefit = benefit,
                loadBanner = loadBanner,
                height = CAROUSEL_HEIGHT,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$BENEFIT_ITEM_TAG${benefit.id}")
                    .clickable(interactionSource = pressSource, indication = null) { onOpenBenefit(benefit.id) },
            )
        }

        if (benefits.size > 1) {
            PageIndicator(pageCount = benefits.size, currentPage = pagerState.currentPage)
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Promoción ${currentPage + 1} de $pageCount" }
            .testTag(INDICATOR_TAG),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == currentPage) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (index == currentPage) VvNavy else VvMintDark),
            )
        }
    }
}

/**
 * El banner de [benefit]. Mientras no llega (o si no llega) se ve una tarjeta con el título: el
 * banner es decoración, su falla nunca es un error en pantalla.
 */
@Composable
fun BenefitBanner(
    benefit: Benefit,
    loadBanner: suspend (String) -> ByteArray?,
    height: Dp,
    modifier: Modifier = Modifier,
    /** Mientras no hay banner, escribe el título y el partner. El detalle ya los muestra debajo: ahí queda en `false`. */
    fallbackText: Boolean = true,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, benefit.id) {
        value = loadBanner(benefit.id)?.let { bytes ->
            withContext(Dispatchers.Default) { SecureBitmap.decode(bytes, BANNER_DECODE_PX) }
        }
    }
    DisposableEffect(bitmap) {
        val shown = bitmap
        onDispose { SecureBitmap.wipe(shown) }
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(VvMint)
            .semantics { contentDescription = benefit.bannerDescription() },
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (fallbackText) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = benefit.title, style = MaterialTheme.typography.titleMedium, color = VvNavy)
                Text(text = benefit.partnerName, style = MaterialTheme.typography.bodyMedium, color = VvMuted)
            }
        }
    }
}

internal const val CAROUSEL_TAG = "benefits_carousel"
internal const val BENEFIT_ITEM_TAG = "benefit_item_"
internal const val INDICATOR_TAG = "benefits_indicator"

private val CAROUSEL_HEIGHT = 150.dp
private const val BANNER_DECODE_PX = 1_200
