package pcbuildmate.domain.state

import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Diagnosis

/**
 * 诊断状态机转移所需的只读上下文。
 *
 * 由调用方（feature 层 ViewModel）在每次调用 [DiagnosisMachine.transition] 前组装，
 * 状态机内部不持有可变引用。
 */
data class DiagnosisContext(
    val build: Build?,
    val revision: BuildRevision?,
    val diagnosis: Diagnosis?,
)

/**
 * 替换状态机转移所需的 guard 数据。
 *
 * - [currentRevisionId] 当前 build 的 currentRevisionId，用于判定 base 是否过期；
 * - [diagnosis] 当前诊断，用于查找冲突是否存在；
 * - [candidateIds] 当前已加载候选的 componentId 集合，用于判定候选是否存在。
 */
data class ReplacementGuard(
    val currentRevisionId: Long,
    val diagnosis: Diagnosis?,
    val candidateIds: Set<String>,
)
