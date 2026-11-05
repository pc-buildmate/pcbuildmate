package pcbuildmate.domain.recommend

import pcbuildmate.contracts.Aggregation
import pcbuildmate.contracts.BudgetRecommenderPort
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.RecommendationRequest
import pcbuildmate.contracts.RuleDef
import pcbuildmate.contracts.RuleKind
import pcbuildmate.contracts.RuleOperand
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.UseCase
import pcbuildmate.contracts.VerificationState
import pcbuildmate.domain.rules.DefaultRuleEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultBudgetRecommenderTest {

    private val rules = listOf(
        RuleDef("CPU_SOCKET_MATCH", RuleKind.EQUALS,
            RuleOperand(Category.CPU, "socket"), RuleOperand(Category.MOTHERBOARD, "socket"), Severity.BLOCKER),
        RuleDef("CPU_MB_CHIPSET_SUPPORT", RuleKind.CSV_CONTAINS,
            RuleOperand(Category.CPU, "supportedChipsets"), RuleOperand(Category.MOTHERBOARD, "chipset"), Severity.BLOCKER),
        RuleDef("CPU_MB_BIOS_SUPPORT", RuleKind.NUMERIC_GE,
            RuleOperand(Category.MOTHERBOARD, "biosGeneration"), RuleOperand(Category.CPU, "minBiosGeneration"), Severity.BLOCKER),
        RuleDef("MEMORY_DDR_GENERATION", RuleKind.EQUALS,
            RuleOperand(Category.MEMORY, "generation"), RuleOperand(Category.MOTHERBOARD, "memoryGeneration"), Severity.BLOCKER,
            Aggregation.PER_ITEM),
        RuleDef("MEMORY_SLOT_COUNT", RuleKind.COUNT_LE,
            RuleOperand(Category.MEMORY, "moduleCount"), RuleOperand(Category.MOTHERBOARD, "memorySlots"), Severity.MAJOR,
            Aggregation.COUNT),
        RuleDef("MEMORY_CAPACITY_LIMIT", RuleKind.SUM_LE,
            RuleOperand(Category.MEMORY, "kitCapacityGb"), RuleOperand(Category.MOTHERBOARD, "maxMemoryGb"), Severity.MAJOR,
            Aggregation.SUM),
        RuleDef("PSU_CONTINUOUS_POWER", RuleKind.NUMERIC_GE,
            RuleOperand(Category.PSU, "continuousW"), RuleOperand(Category.CPU, "tdpW"), Severity.BLOCKER,
            Aggregation.SINGLE, "requiredW = ceil((cpu.tdpW + gpu.tdpW + 100) * 1.2)"),
        RuleDef("PSU_CONNECTORS", RuleKind.CSV_SUBSET,
            RuleOperand(Category.PSU, "connectors"), RuleOperand(Category.GPU, "requiredPsuConnectors"), Severity.BLOCKER),
        RuleDef("STORAGE_MB_INTERFACE", RuleKind.CSV_CONTAINS,
            RuleOperand(Category.MOTHERBOARD, "storageInterfaces"), RuleOperand(Category.STORAGE, "interface"), Severity.MAJOR),
    )

    @Test
    fun `8000 gaming is feasible and within budget`() {
        val result = recommender().recommend(request(8000, UseCase.GAMING))

        assertTrue(result.feasible)
        val build = assertNotNull(result.build)
        assertTrue(build.totalCny <= 8000)
        assertTrue(build.items.any { it.category == Category.GPU })
        assertEquals(6, build.items.size)
        assertEquals(0, result.shortfallCny)
        assertEquals(1.0, result.weightsVisible.values.sum(), 1e-9)
        assertTrue(result.weightsVisible[Category.GPU]!! > 0)
        assertNoBlocker(build)
    }

    @Test
    fun `1000 gaming is infeasible with positive shortfall`() {
        val result = recommender().recommend(request(1000, UseCase.GAMING))

        assertTrue(!result.feasible)
        assertTrue(result.shortfallCny > 0)
    }

    @Test
    fun `study office omits gpu and picks an igpu cpu`() {
        val result = recommender().recommend(request(8000, UseCase.STUDY_OFFICE))

        assertTrue(result.feasible)
        val build = assertNotNull(result.build)
        assertTrue(build.items.none { it.category == Category.GPU })
        val cpu = build.items.first { it.category == Category.CPU }
        assertEquals("cpu-9600x", cpu.componentId)
        assertNoBlocker(build)
        assertTrue(result.weightsVisible.values.sum() > 0.99)
    }

    @Test
    fun `no-gpu study fails if the only cpu has no igpu`() {
        val noIgpu = catalogue().filterNot { it.componentId == "cpu-9600x" }
        val result = DefaultBudgetRecommender(MapCatalogue(noIgpu), engine(noIgpu))
            .recommend(request(8000, UseCase.STUDY_OFFICE))

        assertTrue(!result.feasible)
        assertEquals(null, result.build)
        assertTrue(result.shortfallCny > 0)
    }

    @Test
    fun `same input is deterministic`() {
        val a = recommender().recommend(request(8000, UseCase.GAMING))
        val b = recommender().recommend(request(8000, UseCase.GAMING))

        assertEquals(a, b)
    }

    // ---------------------------------------------------------------- fixture

    private fun recommender(): BudgetRecommenderPort {
        val records = catalogue()
        return DefaultBudgetRecommender(MapCatalogue(records), engine(records))
    }

    private fun engine(records: List<CatalogueRecord>) =
        DefaultRuleEngine(rules, MapCatalogue(records))

    private fun request(budget: Long, useCase: UseCase) = RecommendationRequest(
        budgetCny = budget,
        useCase = useCase,
        preferences = Preferences(),
    )

    private fun assertNoBlocker(build: pcbuildmate.contracts.BuildRevision) {
        val outcomes = engine(catalogue()).evaluate(build)
        assertTrue(
            outcomes.none { it is RuleOutcome.Conflict && it.severity == Severity.BLOCKER },
            "推荐结果不应有 BLOCKER: $outcomes",
        )
    }

    private fun catalogue(): List<CatalogueRecord> = listOf(
        record("cpu-9600x", Category.CPU, 1349,
            "socket" to f("AM5"), "supportedChipsets" to f("B650,B850"),
            "tdpW" to f("65", 65.0), "minBiosGeneration" to f("1.0", 1.0),
            "integratedGraphics" to f("YES")),
        record("cpu-7500f", Category.CPU, 999,
            "socket" to f("AM5"), "supportedChipsets" to f("B650,B850"),
            "tdpW" to f("65", 65.0), "minBiosGeneration" to f("1.0", 1.0),
            "integratedGraphics" to f("NO")),
        record("mb-b650", Category.MOTHERBOARD, 900,
            "socket" to f("AM5"), "chipset" to f("B650"), "biosGeneration" to f("1.0", 1.0),
            "memoryGeneration" to f("DDR5"), "memorySlots" to f("4", 4.0),
            "maxMemoryGb" to f("128", 128.0), "formFactor" to f("ATX"),
            "storageInterfaces" to f("M2_NVME,SATA")),
        record("mem-ddr5-32", Category.MEMORY, 500,
            "generation" to f("DDR5"), "moduleCapacityGb" to f("32", 32.0),
            "kitCapacityGb" to f("32", 32.0), "moduleCount" to f("1", 1.0)),
        record("gpu-5060", Category.GPU, 4099,
            "lengthMm" to f("231", 231.0), "thicknessMm" to f("49", 49.0),
            "slotWidth" to f("2", 2.0), "tdpW" to f("145", 145.0),
            "requiredPsuConnectors" to f("8PIN")),
        record("psu-750", Category.PSU, 600,
            "continuousW" to f("750", 750.0), "connectors" to f("8PIN")),
        record("ssd-nvme", Category.STORAGE, 200, "interface" to f("M2_NVME")),
    )

    private fun f(value: String, numeric: Double? = null) =
        FieldValue(value = value, state = VerificationState.VERIFIED, numeric = numeric)

    private fun record(
        id: String,
        category: Category,
        price: Long,
        vararg attrs: Pair<String, FieldValue>,
    ) = CatalogueRecord(
        componentId = id,
        category = category,
        manufacturer = "demo",
        model = id,
        partNumber = id,
        attributes = attrs.toMap(),
        priceCny = price,
        priceDate = "2026-11-04",
        catalogueVersion = "v1",
        isDemo = true,
    )

    private class MapCatalogue(private val records: List<CatalogueRecord>) : CataloguePort {
        private val byId = records.associateBy { it.componentId }

        override fun getById(componentId: String) = byId[componentId]
        override fun search(query: String, category: Category?) =
            records.filter { category == null || it.category == category }.sortedBy { it.componentId }

        override fun listByCategory(category: Category) =
            records.filter { it.category == category }.sortedBy { it.componentId }

        override fun catalogueVersion() = "v1"
        override fun priceDate() = "2026-11-04"
    }
}
