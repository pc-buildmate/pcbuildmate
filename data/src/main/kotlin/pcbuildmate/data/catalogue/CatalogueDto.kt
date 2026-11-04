package pcbuildmate.data.catalogue

import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogueFileDto(
    val catalogueVersion: String,
    val priceDate: String,
    val isDemo: Boolean = false,
    val note: String? = null,
    val components: List<ComponentDto> = emptyList(),
)

@Serializable
internal data class ComponentDto(
    val componentId: String,
    val category: String,
    val manufacturer: String,
    val model: String,
    val partNumber: String,
    val priceCny: Long,
    val attributes: Map<String, FieldValueDto> = emptyMap(),
    val catalogueVersion: String,
    val priceDate: String,
    val isDemo: Boolean = false,
)

@Serializable
internal data class FieldValueDto(
    val value: String? = null,
    val state: String,
    val sources: List<FieldSourceDto> = emptyList(),
    val conflict: Boolean = false,
    val numeric: Double? = null,
)

@Serializable
internal data class FieldSourceDto(
    val source: String,
    val rawValue: String,
    val state: String,
)
