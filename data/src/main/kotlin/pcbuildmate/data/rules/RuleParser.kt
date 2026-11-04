package pcbuildmate.data.rules

import pcbuildmate.contracts.Aggregation
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.RuleDef
import pcbuildmate.contracts.RuleKind
import pcbuildmate.contracts.RuleOperand
import pcbuildmate.contracts.Severity
import pcbuildmate.data.catalogue.FieldPathRegistry
import kotlinx.serialization.json.Json

/** 解析后的规则表；版本在进程内不变。 */
data class RuleTable(
    val rules: List<RuleDef>,
    val rulesVersion: String,
    val catalogueVersion: String,
)

/**
 * `assets/rules/rules-v1.json` 的解析入口。
 *
 * 只做形状与引用校验；规则语义由 `:domain:rules` 负责。
 */
object RuleParser {

    /** docs/03 §3.7 冻结公式；只允许这一条。 */
    const val PSU_REQUIRED_W_FORMULA = "requiredW = ceil((cpu.tdpW + gpu.tdpW + 100) * 1.2)"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(text: String): RuleTable {
        val dto = try {
            json.decodeFromString(RulesFileDto.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("rules JSON 解析失败：${e.message}", e)
        }

        require(dto.rulesVersion.isNotBlank()) { "rulesVersion 为空" }
        require(dto.catalogueVersion.isNotBlank()) { "catalogueVersion 为空" }
        require(dto.rules.isNotEmpty()) { "规则表为空" }

        val seen = HashSet<String>(dto.rules.size)
        val rules = dto.rules.map { rule ->
            if (!seen.add(rule.ruleId)) {
                throw IllegalArgumentException("规则表非法：重复 ruleId=${rule.ruleId}")
            }

            val kind = enumValue<RuleKind>(rule.kind, rule.ruleId, "kind")
            val severity = enumValue<Severity>(rule.severity, rule.ruleId, "severity")
            val aggregation = enumValue<Aggregation>(rule.aggregation, rule.ruleId, "aggregation")

            val left = operand(rule.ruleId, "left", rule.left)
            val right = operand(rule.ruleId, "right", rule.right)

            when {
                rule.formula == null -> Unit
                rule.formula != PSU_REQUIRED_W_FORMULA ->
                    throw IllegalArgumentException(
                        "规则表非法 [${rule.ruleId}]：未知 formula '${rule.formula}'（只允许 docs/03 §3.7 冻结式）",
                    )
                rule.ruleId != "PSU_CONTINUOUS_POWER" || kind != RuleKind.NUMERIC_GE ->
                    throw IllegalArgumentException(
                        "规则表非法 [${rule.ruleId}]：formula 只允许用于 PSU_CONTINUOUS_POWER / NUMERIC_GE",
                    )
            }

            RuleDef(
                ruleId = rule.ruleId,
                kind = kind,
                left = left,
                right = right,
                severity = severity,
                aggregation = aggregation,
                formula = rule.formula,
            )
        }

        return RuleTable(
            rules = rules.sortedBy { it.ruleId },
            rulesVersion = dto.rulesVersion,
            catalogueVersion = dto.catalogueVersion,
        )
    }

    private fun operand(ruleId: String, side: String, dto: OperandDto): RuleOperand {
        val category = enumValue<Category>(dto.category, ruleId, "$side.category")
        if (!FieldPathRegistry.isAllowed(category, dto.path)) {
            throw IllegalArgumentException(
                "规则表非法 [$ruleId]：$side.path '${dto.path}' 不在 ${category} 的冻结字段表内",
            )
        }
        return RuleOperand(category = category, path = dto.path)
    }

    private inline fun <reified E : Enum<E>> enumValue(raw: String, ruleId: String, where: String): E =
        try {
            enumValueOf<E>(raw)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("规则表非法 [$ruleId]：$where 未知取值 '$raw'", e)
        }
}
