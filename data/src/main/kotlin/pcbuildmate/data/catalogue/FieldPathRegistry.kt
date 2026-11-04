package pcbuildmate.data.catalogue

import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.VerificationState

/**
 * 字段路径冻结表的可执行副本（docs/03 §3.3）。
 *
 * 这里只做 seed 校验，不参与规则判定；规则引擎仍然只认 [FieldValue]。
 * 新增路径必须先改 docs/03 并走契约变更，然后同步本表。
 */
internal object FieldPathRegistry {

    internal enum class FieldType { STRING, CSV, NUMBER, VERSION }

    private val allowed: Map<Category, Map<String, FieldType>> = mapOf(
        Category.CPU to mapOf(
            "socket" to FieldType.STRING,
            "supportedChipsets" to FieldType.CSV,
            "tdpW" to FieldType.NUMBER,
            "minBiosGeneration" to FieldType.VERSION,
        ),
        Category.MOTHERBOARD to mapOf(
            "socket" to FieldType.STRING,
            "chipset" to FieldType.STRING,
            "biosGeneration" to FieldType.VERSION,
            "memoryGeneration" to FieldType.STRING,
            "memorySlots" to FieldType.NUMBER,
            "maxMemoryGb" to FieldType.NUMBER,
            "formFactor" to FieldType.STRING,
            "m2Slots" to FieldType.NUMBER,
            "sataPorts" to FieldType.NUMBER,
            "storageInterfaces" to FieldType.CSV,
        ),
        Category.MEMORY to mapOf(
            "generation" to FieldType.STRING,
            "moduleCapacityGb" to FieldType.NUMBER,
            "kitCapacityGb" to FieldType.NUMBER,
            "moduleCount" to FieldType.NUMBER,
        ),
        Category.GPU to mapOf(
            "lengthMm" to FieldType.NUMBER,
            "thicknessMm" to FieldType.NUMBER,
            "slotWidth" to FieldType.NUMBER,
            "tdpW" to FieldType.NUMBER,
            "recommendedPsuW" to FieldType.NUMBER,
            "requiredPsuConnectors" to FieldType.CSV,
        ),
        Category.PSU to mapOf(
            "continuousW" to FieldType.NUMBER,
            "connectors" to FieldType.CSV,
        ),
        Category.CASE to mapOf(
            "maxGpuLengthMm" to FieldType.NUMBER,
            "maxGpuThicknessMm" to FieldType.NUMBER,
            "maxGpuSlots" to FieldType.NUMBER,
            "maxCoolerHeightMm" to FieldType.NUMBER,
            "supportedFormFactors" to FieldType.CSV,
            "driveBays" to FieldType.NUMBER,
        ),
        Category.COOLER to mapOf(
            "heightMm" to FieldType.NUMBER,
            "socketMounts" to FieldType.CSV,
        ),
        Category.STORAGE to mapOf(
            "interface" to FieldType.STRING,
        ),
    )

    private val csvToken = Regex("^[A-Z0-9_]+$")
    private val versionPattern = Regex("^\\d+\\.\\d{1,2}$")
    private val isoDate = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun isAllowed(category: Category, path: String): Boolean =
        allowed[category]?.containsKey(path) == true

    fun validate(record: CatalogueRecordView) {
        val fields = allowed[record.category]
            ?: fail(record.componentId, "未知类目 ${record.category}")

        for ((path, value) in record.attributes) {
            val type = fields[path]
                ?: fail(record.componentId, "字段路径不在冻结表：$path")

            if (value.state == VerificationState.EMPTY) {
                if (value.value != null) {
                    fail(record.componentId, "$path state=EMPTY 但 value 非空")
                }
                continue
            }

            val raw = value.value?.takeIf { it.isNotBlank() }
                ?: fail(record.componentId, "$path state=${value.state} 但 value 为空")

            when (type) {
                FieldType.STRING -> Unit
                FieldType.CSV -> validateCsv(record.componentId, path, raw)
                FieldType.NUMBER -> validateNumber(record.componentId, path, raw, value)
                FieldType.VERSION -> validateVersion(record.componentId, path, raw)
            }
        }
    }

    fun validateHeader(version: String, priceDate: String) {
        if (version.isBlank()) fail("<catalogue>", "catalogueVersion 为空")
        if (!isoDate.matches(priceDate)) fail("<catalogue>", "priceDate 不是 ISO 日期：$priceDate")
        try {
            java.time.LocalDate.parse(priceDate)
        } catch (e: Exception) {
            fail("<catalogue>", "priceDate 非法：$priceDate")
        }
    }

    private fun validateCsv(componentId: String, path: String, raw: String) {
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                if (!csvToken.matches(token)) {
                    fail(componentId, "$path CSV token 非法：$token")
                }
            }
    }

    private fun validateNumber(componentId: String, path: String, raw: String, value: FieldValue) {
        val parsed = value.numeric ?: raw.toDoubleOrNull()
            ?: fail(componentId, "$path 不是数值：$raw")
        if (value.numeric != null && raw.toDoubleOrNull()?.let { kotlin.math.abs(it - parsed) > 1e-9 } == true) {
            fail(componentId, "$path numeric 与 value 不一致：${value.numeric} vs $raw")
        }
    }

    private fun validateVersion(componentId: String, path: String, raw: String) {
        if (!versionPattern.matches(raw)) {
            fail(componentId, "$path 版本格式非法：$raw（要求 主.次）")
        }
        val minor = raw.substringAfter('.').toInt()
        if (minor > 9) {
            fail(componentId, "$path 次版本号 > 9：$raw（见 docs/03 §3.3）")
        }
    }

    private fun fail(componentId: String, message: String): Nothing =
        throw IllegalArgumentException("catalogue seed 非法 [$componentId]：$message")
}

/** 校验器只依赖这几个字段，避免把 DTO 暴露给 registry。 */
internal data class CatalogueRecordView(
    val componentId: String,
    val category: Category,
    val attributes: Map<String, FieldValue>,
)
