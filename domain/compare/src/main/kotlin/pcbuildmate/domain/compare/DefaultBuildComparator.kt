package pcbuildmate.domain.compare

import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildComparatorPort
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.ComparisonResult
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.FieldDelta
import pcbuildmate.contracts.TextKey

/**
 * D6 比较：只读已保存的 revision 与诊断，不调用规则引擎。
 *
 * 字段（首版可判定部分）：总价、兼容状态、各分类花费。
 * 功耗余量 / 升级余量 / 物理尺寸需要目录规格，本实现暂不输出；
 * 扩展时只能在 [deltas] 增加字段，不得重新计算兼容性。
 */
class DefaultBuildComparator(
    private val builds: List<Build>,
    private val revisions: Map<String, BuildRevision>,
    private val diagnoses: Map<String, Diagnosis?>,
) : BuildComparatorPort {

    override fun compare(buildIds: List<String>): ComparisonResult {
        if (buildIds.size < 2) {
            return ComparisonResult(
                buildIds = buildIds,
                revisions = emptyList(),
                diagnoses = emptyList(),
                deltas = emptyList(),
                warnings = listOf(TextKey.COMPARE_INSUFFICIENT_BUILDS),
            )
        }

        val byId = builds.associateBy { it.buildId }
        val revisionsOut = buildIds.map { id ->
            requireNotNull(byId[id]) { "build not found: $id" }
            requireNotNull(revisions[id]) { "revision not found for build: $id" }
        }

        val warnings = mutableListOf<TextKey>()
        val diagnosesOut = buildIds.map { id ->
            val rev = revisions.getValue(id)
            val d = diagnoses[id]
            if (d != null && d.revisionId != rev.id) {
                if (TextKey.COMPARE_DIAGNOSTICS_STALE !in warnings) {
                    warnings += TextKey.COMPARE_DIAGNOSTICS_STALE
                }
                null
            } else {
                d
            }
        }

        if (revisionsOut.map { it.catalogueVersion }.distinct().size > 1) {
            // 首版没有专门的 key；用 stale key 会让文案误导，这里先只保留版本差异在字段里
        }

        val total = delta("totalCny", buildIds, revisionsOut, diagnosesOut) { rev, _ ->
            rev.totalCny.toString()
        }
        val status = delta("status", buildIds, revisionsOut, diagnosesOut) { rev, d ->
            d?.overall?.name ?: "UNCHECKED"
        }

        val categories = Category.entries.map { category ->
            delta(category.name, buildIds, revisionsOut, diagnosesOut) { rev, _ ->
                rev.items.filter { it.category == category }
                    .sumOf { it.quantity.toLong() * it.unitPriceCny }
                    .toString()
            }
        }

        return ComparisonResult(
            buildIds = buildIds,
            revisions = revisionsOut,
            diagnoses = diagnosesOut,
            deltas = listOf(total, status) + categories,
            warnings = warnings,
        )
    }

    private inline fun delta(
        field: String,
        buildIds: List<String>,
        revisions: List<BuildRevision>,
        diagnoses: List<Diagnosis?>,
        crossinline value: (BuildRevision, Diagnosis?) -> String,
    ): FieldDelta {
        val values = buildIds.indices.map { i -> value(revisions[i], diagnoses[i]) }
        return FieldDelta(
            field = field,
            values = values,
            highlighted = values.distinct().size > 1,
        )
    }
}
