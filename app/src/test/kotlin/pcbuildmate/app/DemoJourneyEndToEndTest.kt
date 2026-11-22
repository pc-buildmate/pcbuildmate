package pcbuildmate.app

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pcbuildmate.contracts.BuildComparatorPort
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.TextKey
import pcbuildmate.contracts.UseCase
import pcbuildmate.contracts.WorkspaceIntent
import pcbuildmate.data.catalogue.AssetCatalogueAdapter
import pcbuildmate.data.rules.RuleParser
import pcbuildmate.domain.compare.DefaultBuildComparator
import pcbuildmate.domain.recommend.DefaultBudgetRecommender
import pcbuildmate.domain.replacement.DefaultReplacementAdvisor
import pcbuildmate.domain.rules.DefaultRuleEngine
import pcbuildmate.feature.workspace.WorkspaceViewModel

/**
 * 端到端综合测试：真实目录（30 条）+ 真实规则 + 真实引擎 + 真实 ViewModel，
 * 走一遍答辩脚本的完整用户旅程，把答辩要念的数字钉在生产代码路径上。
 *
 * 覆盖（按 docs/defense/DEMO.md 的九步）：
 *   预算推荐 → 载入演示配置 → 保存 A → 检查（52 分 / 337>320 BLOCKER / BIOS 未验证）
 *   → 选冲突 → 替换候选（300mm +100）→ 预览（7900）→ 应用并重检 → 保存 B
 *   → 比较 A/B → 编辑导致诊断过期 → 数量策略拒绝 → Undo。
 *
 * 与 `RecommendationIntegrationTest` 的区别：那个只断言"性质"，本测试断言
 * 答辩脚本里出现的**具体数字**（推荐 7900 / 演示 7800 / 替换后 7900 / 52 / +100），防止
 * 数据和讲稿脱节。数字变了这里会红，提醒先改 `Defense_Numbers_To_Confirm.md`。
 */
class DemoJourneyEndToEndTest {

    private val root = File(System.getProperty("user.dir")!!).parentFile
    private val catalogue = AssetCatalogueAdapter {
        File(root, "assets/catalogue/catalogue-app.json").readText()
    }
    private val rules = RuleParser.parse(
        File(root, "assets/rules/rules-v1.json").readText(),
    ).rules
    private val engine = DefaultRuleEngine(rules, catalogue)

    private fun newViewModel() = WorkspaceViewModel(
        catalogue = catalogue,
        evaluator = engine,
        advisor = DefaultReplacementAdvisor(catalogue, engine),
        recommender = DefaultBudgetRecommender(catalogue, engine),
    )

    @Test
    fun `the whole demo journey stays true to the defence script`() {
        val vm = newViewModel()

        // ---- 0. 预算推荐：8000 / 游戏 → 8 类（含机箱散热）、含独显、无 BLOCKER
        val rec = vm.recommend(8000, UseCase.GAMING)
        assertTrue(rec.feasible, "8000 游戏应可行")
        val recommended = assertNotNull(rec.build)
        assertEquals(7900, recommended.totalCny, "答辩脚本宣称的推荐总价")
        val cats = recommended.items.map { it.category }.toSet()
        assertEquals(setOf(Category.CPU, Category.MOTHERBOARD, Category.MEMORY, Category.GPU, Category.PSU, Category.STORAGE, Category.COOLER, Category.CASE), cats)
        assertTrue(engine.evaluate(recommended).none {
            it is RuleOutcome.Conflict && it.severity == Severity.BLOCKER
        }, "推荐结果不应有 BLOCKER")

        // ---- 1. 载入演示配置：8 件、¥7,800
        vm.startDemo()
        val demo = assertNotNull(vm.uiState.value.revision)
        assertEquals(8, demo.items.size)
        assertEquals(7800, demo.totalCny, "演示配置锚点总价")

        // ---- 2. 保存 A
        vm.onIntent(WorkspaceIntent.SaveBuild)
        assertEquals(1, vm.uiState.value.savedBuilds.size)
        assertTrue(vm.uiState.value.isSaved)

        // ---- 3. 检查：CONFLICT / HIGH / 52 分；1 BLOCKER + 1 MAJOR + BIOS 未验证
        vm.onIntent(WorkspaceIntent.StartCheck)
        val diag = assertNotNull(vm.uiState.value.currentDiagnosis)
        assertEquals(OverallStatus.CONFLICT, diag.overall)
        assertEquals(RiskLevel.HIGH, diag.riskLevel)
        assertEquals(52, diag.score, "100 - 30(BLOCKER) - 15(MAJOR) - 3(1 未验证)")

        val conflicts = diag.outcomes.filterIsInstance<RuleOutcome.Conflict>()
        val length = conflicts.single { it.ruleId == "GPU_CASE_LENGTH" }
        assertEquals(Severity.BLOCKER, length.severity)
        assertEquals("337", length.leftValue)
        assertEquals("320", length.rightValue)

        val thickness = conflicts.single { it.ruleId == "GPU_CASE_THICKNESS" }
        assertEquals(Severity.MAJOR, thickness.severity)

        val unverified = diag.outcomes.filterIsInstance<RuleOutcome.Unverified>()
        assertTrue(unverified.any { it.ruleId == "CPU_MB_BIOS_SUPPORT" }, "BIOS 未验证必须保留")

        // ---- 4. 选冲突 → 替换候选：300mm 卡 +¥100，rank 1
        vm.onIntent(WorkspaceIntent.SelectConflict(length.conflictId))
        val suggestions = assertNotNull(vm.uiState.value.suggestions)
        assertEquals(length.conflictId, suggestions.conflictId)
        val rank1 = assertNotNull(suggestions.candidates.firstOrNull())
        assertEquals("gpu-demo-300", rank1.componentId)
        assertEquals(100L, rank1.priceDeltaCny, "300mm 卡比 337 贵 ¥100")

        // ---- 5. 预览：¥7,900
        vm.onIntent(WorkspaceIntent.PreviewCandidate(rank1.componentId))
        val preview = assertNotNull(vm.uiState.value.previewRevision)
        assertEquals(7900, preview.totalCny, "替换预览总价")

        // ---- 6. 应用并全量重检：无 BLOCKER，BIOS 仍未验证
        vm.onIntent(WorkspaceIntent.ApplySuggestion)
        val applied = assertNotNull(vm.uiState.value.revision)
        assertEquals(7900, applied.totalCny)
        assertEquals("gpu-demo-300", applied.items.first { it.category == Category.GPU }.componentId)
        val appliedDiag = assertNotNull(vm.uiState.value.currentDiagnosis)
        assertFalse(
            appliedDiag.outcomes.filterIsInstance<RuleOutcome.Conflict>().any { it.severity == Severity.BLOCKER },
            "替换后不应再有任何 BLOCKER",
        )
        assertTrue(
            appliedDiag.outcomes.filterIsInstance<RuleOutcome.Unverified>().any { it.ruleId == "CPU_MB_BIOS_SUPPORT" },
            "修一个冲突 ≠ 全部兼容：BIOS 仍未验证",
        )

        // ---- 7. 保存 B → 比较 A(7800) / B(7900)
        vm.onIntent(WorkspaceIntent.SaveBuild)
        assertEquals(2, vm.uiState.value.savedBuilds.size)

        val snap = vm.savedSnapshot()
        val comparator: BuildComparatorPort = DefaultBuildComparator(
            snap.builds, snap.revisions, snap.diagnoses,
        )
        val comparison = comparator.compare(snap.builds.map { it.buildId })
        val totalDelta = comparison.deltas.single { it.field == "totalCny" }
        assertEquals(listOf("7800", "7900"), totalDelta.values)

        // ---- 8. 编辑内存数量（合法多件）→ 诊断过期，不自动重跑
        vm.onIntent(WorkspaceIntent.SetQuantity("mem-demo-ddr5-32", 2))
        assertNull(vm.uiState.value.currentDiagnosis, "旧诊断不得作为当前结论")
        assertTrue(vm.uiState.value.isDiagnosisStale, "应显示'配置已修改，请重新检查'")
        assertEquals(2, vm.uiState.value.revision!!.items.first { it.componentId == "mem-demo-ddr5-32" }.quantity)

        // ---- 9. 单件类目不允许改数量 → 拒绝，revision 不变
        val before = vm.uiState.value.revision
        vm.onIntent(WorkspaceIntent.SetQuantity("cpu-demo-7600x", 2))
        assertEquals(TextKey.QUANTITY_NOT_APPLICABLE, vm.uiState.value.banner)
        assertEquals(before, vm.uiState.value.revision, "非法数量不得产生新 revision")

        // ---- 10. Undo 恢复内存数量
        vm.onIntent(WorkspaceIntent.Undo)
        val undone = assertNotNull(vm.uiState.value.revision)
        assertEquals(1, undone.items.first { it.componentId == "mem-demo-ddr5-32" }.quantity)
        assertTrue(undone.id > before!!.id, "Undo 产生更大的 revision id（INV-10）")
    }

    @Test
    fun `recommend keeps its six categories and reports infeasible budgets`() {
        val vm = newViewModel()
        val infeasible = vm.recommend(500, UseCase.GAMING)
        assertFalse(infeasible.feasible)
        assertTrue(infeasible.shortfallCny > 0)

        val study = vm.recommend(8000, UseCase.STUDY_OFFICE)
        assertTrue(study.feasible)
        val studyBuild = assertNotNull(study.build)
        assertTrue(studyBuild.items.none { it.category == Category.GPU }, "办公配置无独显")
        assertTrue(
            catalogue.getById(studyBuild.items.first { it.category == Category.CPU }.componentId)
                ?.attributes?.get("integratedGraphics")?.value == "YES",
            "无独显必须选带核显 CPU",
        )
    }
}
