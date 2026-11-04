package pcbuildmate.data.rules

import kotlinx.serialization.Serializable

@Serializable
internal data class RulesFileDto(
    val rulesVersion: String,
    val catalogueVersion: String,
    val rules: List<RuleDto> = emptyList(),
)

@Serializable
internal data class RuleDto(
    val ruleId: String,
    val kind: String,
    val left: OperandDto,
    val right: OperandDto,
    val severity: String,
    val aggregation: String = "SINGLE",
    val formula: String? = null,
    val fixtures: List<String> = emptyList(),
)

@Serializable
internal data class OperandDto(
    val category: String,
    val path: String,
)
