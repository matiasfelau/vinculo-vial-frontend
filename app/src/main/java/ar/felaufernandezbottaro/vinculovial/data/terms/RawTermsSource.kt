package ar.felaufernandezbottaro.vinculovial.data.terms

import android.content.Context
import ar.felaufernandezbottaro.vinculovial.R
import ar.felaufernandezbottaro.vinculovial.di.IoDispatcher
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Los Términos van dentro del APK, en `res/raw/terminos.md`: se leen sin red y no cambian sin una versión nueva. */
@Singleton
class RawTermsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : TermsSource {

    override suspend fun markdown(): String = withContext(dispatcher) {
        context.resources.openRawResource(R.raw.terminos).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
