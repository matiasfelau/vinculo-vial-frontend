package ar.felaufernandezbottaro.vinculovial.domain.terms

import javax.inject.Inject

/** De dónde sale el texto de los Términos. La implementación lee `res/raw/terminos.md`. */
interface TermsSource {

    /** El markdown crudo, con los marcadores `{{...}}` sin completar. */
    suspend fun markdown(): String
}

/** Arma el documento que se muestra: el markdown de [source] con los marcadores de [legalInfo] completados. */
class GetTermsUseCase @Inject constructor(
    private val source: TermsSource,
    private val legalInfo: LegalInfo,
) {
    suspend operator fun invoke(): List<TermsBlock> =
        TermsMarkdown.parse(source.markdown(), legalInfo.placeholders())
}
