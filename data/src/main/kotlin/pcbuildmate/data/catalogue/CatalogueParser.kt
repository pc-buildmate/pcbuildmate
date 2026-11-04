package pcbuildmate.data.catalogue

import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldSource
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.VerificationState
import kotlinx.serialization.json.Json

/** 解析后的不可变目录；版本与价格日期在进程内不变（D1 输出契约）。 */
data class CatalogueSnapshot(
    val records: List<CatalogueRecord>,
    val catalogueVersion: String,
    val priceDate: String,
    val isDemo: Boolean,
)

/**
 * assets/catalogue 下的 JSON 解析入口。
 *
 * 失败一律抛 [IllegalArgumentException]：seed 非法属于启动即失败，
 * 不允许静默跳过坏记录（D1 失败模式）。
 */
object CatalogueParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(text: String): CatalogueSnapshot {
        val dto = try {
            json.decodeFromString(CatalogueFileDto.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("catalogue JSON 解析失败：${e.message}", e)
        }

        FieldPathRegistry.validateHeader(dto.catalogueVersion, dto.priceDate)

        val seen = HashSet<String>(dto.components.size)
        val records = dto.components.map { component ->
            if (!seen.add(component.componentId)) {
                throw IllegalArgumentException("catalogue seed 非法：重复 componentId=${component.componentId}")
            }
            if (component.catalogueVersion != dto.catalogueVersion) {
                throw IllegalArgumentException(
                    "catalogue seed 非法 [${component.componentId}]：组件版本 ${component.catalogueVersion} 与目录版本 ${dto.catalogueVersion} 不一致",
                )
            }
            if (component.priceDate != dto.priceDate) {
                throw IllegalArgumentException(
                    "catalogue seed 非法 [${component.componentId}]：组件价格日期 ${component.priceDate} 与目录价格日期 ${dto.priceDate} 不一致",
                )
            }
            if (dto.isDemo && !component.isDemo) {
                throw IllegalArgumentException(
                    "catalogue seed 非法 [${component.componentId}]：演示目录中的记录必须 isDemo=true（INV-11）",
                )
            }

            val category = enumValue<Category>(component.category, component.componentId, "category")
            val attributes = component.attributes.mapValues { (path, field) ->
                toFieldValue(component.componentId, path, field)
            }

            FieldPathRegistry.validate(
                CatalogueRecordView(
                    componentId = component.componentId,
                    category = category,
                    attributes = attributes,
                ),
            )

            CatalogueRecord(
                componentId = component.componentId,
                category = category,
                manufacturer = component.manufacturer,
                model = component.model,
                partNumber = component.partNumber,
                attributes = attributes,
                priceCny = component.priceCny,
                priceDate = component.priceDate,
                catalogueVersion = component.catalogueVersion,
                isDemo = component.isDemo,
            )
        }.sortedBy { it.componentId }

        return CatalogueSnapshot(
            records = records,
            catalogueVersion = dto.catalogueVersion,
            priceDate = dto.priceDate,
            isDemo = dto.isDemo,
        )
    }

    private fun toFieldValue(componentId: String, path: String, dto: FieldValueDto): FieldValue {
        val state = enumValue<VerificationState>(dto.state, componentId, "attributes.$path.state")
        val sources = dto.sources.map { source ->
            FieldSource(
                source = source.source,
                rawValue = source.rawValue,
                state = enumValue<VerificationState>(
                    source.state,
                    componentId,
                    "attributes.$path.sources[].state",
                ),
            )
        }
        if (dto.conflict && sources.size < 2) {
            throw IllegalArgumentException(
                "catalogue seed 非法 [$componentId]：attributes.$path conflict=true 但来源少于 2",
            )
        }
        return FieldValue(
            value = dto.value,
            state = state,
            sources = sources,
            conflict = dto.conflict,
            numeric = dto.numeric,
        )
    }

    private inline fun <reified E : Enum<E>> enumValue(raw: String, componentId: String, where: String): E =
        try {
            enumValueOf<E>(raw)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "catalogue seed 非法 [$componentId]：$where 未知取值 '$raw'",
                e,
            )
        }
}
