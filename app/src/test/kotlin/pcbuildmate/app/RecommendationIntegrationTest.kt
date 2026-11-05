package pcbuildmate.app

import java.io.File
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.RecommendationRequest
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.UseCase
import pcbuildmate.data.catalogue.AssetCatalogueAdapter
import pcbuildmate.data.rules.RuleParser
import pcbuildmate.domain.recommend.DefaultBudgetRecommender
import pcbuildmate.domain.rules.DefaultRuleEngine

/**
 * D4 集成：完整 15 条规则 + App 实际合并目录（30 条）。
 *
 * 只断言性质：预算、BLOCKER、GPU/iGPU、权重，不断言具体型号。
 */
class RecommendationIntegrationTest {

    private val root = File(System.getProperty("user.dir")!!).parentFile
    private val catalogue = AssetCatalogueAdapter {
        File(root, "assets/catalogue/catalogue-app.json").readText()
    }
    private val rules = RuleParser.parse(
        File(root, "assets/rules/rules-v1.json").readText(),
    ).rules
    private val engine = DefaultRuleEngine(rules, catalogue)
    private val recommender = DefaultBudgetRecommender(catalogue, engine)

    @Test
    fun `8000 gaming is feasible with no blocker`() {
        val result = recommender.recommend(request(8000, UseCase.GAMING))

        assertTrue(result.feasible)
        val build = assertNotNull(result.build)
        assertTrue(build.totalCny <= 8000)
        assertTrue(build.items.any { it.category == Category.GPU })
        assertTrue(result.weightsVisible.values.sum() in 0.999..1.001)
        assertTrue(
            engine.evaluate(build).none {
                it is RuleOutcome.Conflict && it.severity == Severity.BLOCKER
            },
            "完整规则下推荐结果不应有 BLOCKER",
        )
    }

    @Test
    fun `study office has no gpu and uses an igpu cpu`() {
        val result = recommender.recommend(request(8000, UseCase.STUDY_OFFICE))

        assertTrue(result.feasible)
        val build = assertNotNull(result.build)
        assertTrue(build.items.none { it.category == Category.GPU })
        val cpu = assertNotNull(build.items.firstOrNull { it.category == Category.CPU })
        assertTrue(
            catalogue.getById(cpu.componentId)?.attributes?.get("integratedGraphics")?.value == "YES",
            "无 GPU 时必须选有核显的 CPU",
        )
    }

    @Test
    fun `1000 gaming is infeasible`() {
        val result = recommender.recommend(request(1000, UseCase.GAMING))
        assertTrue(!result.feasible)
        assertTrue(result.shortfallCny > 0)
    }

    private fun request(budget: Long, useCase: UseCase) = RecommendationRequest(
        budgetCny = budget,
        useCase = useCase,
        preferences = Preferences(),
    )
}
