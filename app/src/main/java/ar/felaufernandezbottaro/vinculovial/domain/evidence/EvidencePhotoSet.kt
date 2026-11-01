package ar.felaufernandezbottaro.vinculovial.domain.evidence

/**
 * Las fotos de evidencia ya procesadas (JPEG en claro, sin EXIF) que se van a cifrar y enviar.
 * Los bytes sólo se prestan dentro de [read]: quien los usa no debe guardarlos ni modificarlos.
 */
interface EvidencePhotoSet {

    /** Cuántas fotos hay de [role]. */
    fun count(role: EvidenceRole): Int

    /** Presta los bytes de la foto `(role, index)` al [block]; `null` si no existe. */
    fun <R> read(role: EvidenceRole, index: Int, block: (ByteArray) -> R): R?
}
