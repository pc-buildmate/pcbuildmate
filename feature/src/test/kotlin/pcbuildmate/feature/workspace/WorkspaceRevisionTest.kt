package pcbuildmate.feature.workspace

import pcbuildmate.contracts.BudgetRecommenderPort
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.CompatibilityEvaluator
import pcbuildmate.contracts.RecommendationRequest
import pcbuildmate.contracts.RecommendationResult
import pcbuildmate.contracts.ReplacementAdvisorPort
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.SuggestionResult
import pcbuildmate.contracts.WorkspaceIntent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceRevisionTest {

    @Test
    fun `revision ids increase on edit and undo`() {
        val vm = WorkspaceViewModel(
            catalogue = DemoCatalogue,
            evaluator = EmptyEvaluator,
            advisor = NoAdvisor,
            recommender = NoRecommender,
        )
        vm.startDemo()
        val base = vm.uiState.value.revision!!
        assertEquals(1L, base.id)

        vm.onIntent(WorkspaceIntent.SetQuantity("gpu-demo-337", 2))
        val edited = vm.uiState.value.revision!!
        assertTrue(edited.id > base.id, "INV-02: 编辑后 revision id 必须递增")

        vm.onIntent(WorkspaceIntent.Undo)
        val undone = vm.uiState.value.revision!!
        assertTrue(undone.id > edited.id, "INV-10: Undo 必须产生更大的 id")
        assertEquals(base.totalCny, undone.totalCny)
        assertEquals(base.items, undone.items)
    }

    @Test
    fun `undo without history is a no-op`() {
        val vm = WorkspaceViewModel(
            catalogue = DemoCatalogue,
            evaluator = EmptyEvaluator,
            advisor = NoAdvisor,
            recommender = NoRecommender,
        )
        vm.startDemo()
        val before = vm.uiState.value.revision!!

        vm.onIntent(WorkspaceIntent.Undo)

        assertEquals(before, vm.uiState.value.revision)
    }

    private object DemoCatalogue : CataloguePort {
        private val ids = listOf(
            "cpu-demo-7600x" to Category.CPU,
            "mb-demo-b650" to Category.MOTHERBOARD,
            "mem-demo-ddr5-32" to Category.MEMORY,
            "gpu-demo-337" to Category.GPU,
            "psu-demo-750" to Category.PSU,
            "case-demo-320" to Category.CASE,
            "cooler-demo-160" to Category.COOLER,
            "storage-demo-nvme" to Category.STORAGE,
        )
        private val records = ids.associate { (id, category) ->
            id to CatalogueRecord(
                componentId = id,
                category = category,
                manufacturer = "demo",
                model = id,
                partNumber = id,
                attributes = emptyMap(),
                priceCny = 100,
                priceDate = "2026-11-04",
                catalogueVersion = "v1",
                isDemo = true,
            )
        }

        override fun getById(componentId: String) = records[componentId]
        override fun search(query: String, category: Category?) =
            records.values.filter { category == null || it.category == category }

        override fun listByCategory(category: Category) = records.values.filter { it.category == category }
        override fun catalogueVersion() = "v1"
        override fun priceDate() = "2026-11-04"
    }

    private object EmptyEvaluator : CompatibilityEvaluator {
        override fun evaluate(revision: BuildRevision): List<RuleOutcome> = emptyList()
    }

    private object NoAdvisor : ReplacementAdvisorPort {
        override fun suggest(base: BuildRevision, conflict: RuleOutcome.Conflict): SuggestionResult =
            error("not used")
    }

    private object NoRecommender : BudgetRecommenderPort {
        override fun recommend(request: RecommendationRequest): RecommendationResult =
            error("not used")
    }
}
