package pcbuildmate.domain.replacement

import pcbuildmate.contracts.Aggregation
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.RuleDef
import pcbuildmate.contracts.RuleKind
import pcbuildmate.contracts.RuleOperand
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.UnverifiedReason
import pcbuildmate.contracts.VerificationState
import pcbuildmate.domain.rules.DefaultRuleEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D5 锚点：337 mm GPU → 300 mm GPU（+100），总价 7800 → 7900，
 * GPU_CASE_LENGTH 转 PASSED，BIOS 仍 UNVERIFIED。
 *
 * 测试只构造 D5 需要的 4 条规则与 9 条目录记录；不依赖 fixtures JSON，
 * 也不把规则引擎带进生产依赖（见 ci/allowlist.txt）。
 */
class DefaultReplacementAdvisorTest {

    private val rules = listOf(
        RuleDef(
            ruleId = "GPU_CASE_LENGTH",
            kind = RuleKind.NUMERIC_LE,
            left = RuleOperand(Category.GPU, "lengthMm"),
            right = RuleOperand(Category.CASE, "maxGpuLengthMm"),
            severity = Severity.BLOCKER,
        ),
        RuleDef(
            ruleId = "GPU_CASE_THICKNESS",
            kind = RuleKind.NUMERIC_LE,
            left = RuleOperand(Category.GPU, "thicknessMm"),
            right = RuleOperand(Category.CASE, "maxGpuThicknessMm"),
            severity = Severity.MAJOR,
        ),
        RuleDef(
            ruleId = "PSU_CONTINUOUS_POWER",
            kind = RuleKind.NUMERIC_GE,
            left = RuleOperand(Category.PSU, "continuousW"),
            right = RuleOperand(Category.CPU, "tdpW"),
            severity = Severity.BLOCKER,
            aggregation = Aggregation.SINGLE,
            formula = "requiredW = ceil((cpu.tdpW + gpu.tdpW + 100) * 1.2)",
        ),
        RuleDef(
            ruleId = "CPU_MB_BIOS_SUPPORT",
            kind = RuleKind.NUMERIC_GE,
            left = RuleOperand(Category.MOTHERBOARD, "biosGeneration"),
            right = RuleOperand(Category.CPU, "minBiosGeneration"),
            severity = Severity.BLOCKER,
        ),
    )

    @Test
    fun `suggests the 300mm gpu with plus 100 and rank 1`() {
        val advisor = advisor()
        val result = advisor.suggest(base(), conflict())

        assertEquals(1, result.candidates.size)
        val candidate = result.candidates.single()
        assertEquals("gpu-demo-300", candidate.componentId)
        assertEquals(100L, candidate.priceDeltaCny)
        assertEquals(1, candidate.rank)
        assertTrue(candidate.specDelta.getValue("lengthMm").contains("300"))
        assertEquals(null, result.noCandidate)
    }

    @Test
    fun `applying the candidate yields 7900 with GPU passed and BIOS still unverified`() {
        val candidate = advisor().suggest(base(), conflict()).candidates.single()

        val items = base().items.map { item ->
            if (item.componentId == "gpu-demo-337") {
                item.copy(componentId = candidate.componentId, unitPriceCny = 3500)
            } else {
                item
            }
        }
        val preview = base().copy(items = items, totalCny = BuildRevision.computeTotal(items))
        val outcomes = DefaultRuleEngine(rules, catalogue()).evaluate(preview)

        assertEquals(7900L, preview.totalCny)
        assertIs<RuleOutcome.Passed>(outcomes.first { it.ruleId == "GPU_CASE_LENGTH" })
        val bios = assertIs<RuleOutcome.Unverified>(outcomes.first { it.ruleId == "CPU_MB_BIOS_SUPPORT" })
        assertEquals(UnverifiedReason.MISSING_FIELD, bios.reason)
    }

    @Test
    fun `returns structured no-candidate when no alternative exists`() {
        val port = MapCatalogue(catalogue().records().filterNot { it.componentId == "gpu-demo-300" })
        val result = DefaultReplacementAdvisor(port, DefaultRuleEngine(rules, port))
            .suggest(base(), conflict())

        assertTrue(result.candidates.isEmpty())
        val reason = assertNotNull(result.noCandidate)
        assertEquals("GPU_CASE_LENGTH:lengthMm", reason.blockingConstraint)
        assertEquals(DefaultReplacementAdvisor.DIRECTION_CHANGE_COMPONENT_OR_BUDGET, reason.suggestedDirection)
    }

    @Test
    fun `filters a candidate that introduces a new blocker`() {
        val badGpu = gpu300.copy(
            attributes = gpu300.attributes + ("tdpW" to field("600", numeric = 600.0)),
        )
        val port = MapCatalogue(
            catalogue().records().map { if (it.componentId == "gpu-demo-300") badGpu else it },
        )
        val result = DefaultReplacementAdvisor(port, DefaultRuleEngine(rules, port))
            .suggest(base(), conflict())

        assertTrue(result.candidates.isEmpty(), "会引入 PSU BLOCKER 的候选必须被过滤")
        assertNotNull(result.noCandidate)
    }

    @Test
    fun `is deterministic`() {
        val advisor = advisor()
        val a = advisor.suggest(base(), conflict())
        val b = advisor.suggest(base(), conflict())

        assertEquals(a, b)
    }

    // ---------------------------------------------------------------- fixtures

    private fun advisor(): DefaultReplacementAdvisor {
        val port = catalogue()
        return DefaultReplacementAdvisor(port, DefaultRuleEngine(rules, port))
    }

    private fun conflict(): RuleOutcome.Conflict {
        val outcomes = DefaultRuleEngine(rules, catalogue()).evaluate(base())
        return outcomes.filterIsInstance<RuleOutcome.Conflict>()
            .first { it.ruleId == "GPU_CASE_LENGTH" }
    }

    private fun base(): BuildRevision {
        val items = listOf(
            item("cpu-demo-7600x", Category.CPU, 1500),
            item("mb-demo-b650", Category.MOTHERBOARD, 900),
            item("mem-demo-ddr5-32", Category.MEMORY, 500),
            item("gpu-demo-337", Category.GPU, 3400),
            item("psu-demo-750", Category.PSU, 600),
            item("case-demo-320", Category.CASE, 400),
            item("cooler-demo-160", Category.COOLER, 300),
            item("storage-demo-nvme", Category.STORAGE, 200),
        )
        return BuildRevision(
            id = 1,
            buildId = "fx-anchor-e2e",
            items = items,
            totalCny = BuildRevision.computeTotal(items),
            catalogueVersion = "static-v1",
            priceDate = "2026-10-31",
            userModified = false,
        )
    }

    private fun item(id: String, category: Category, price: Long) = BuildItem(
        componentId = id,
        category = category,
        quantity = 1,
        unitPriceCny = price,
    )

    private fun catalogue(): MapCatalogue = MapCatalogue(
        listOf(
            record(
                "cpu-demo-7600x", Category.CPU, 1500,
                "socket" to field("AM5"),
                "supportedChipsets" to field("B650,X670"),
                "tdpW" to field("105", numeric = 105.0),
                "minBiosGeneration" to field("1.0", numeric = 1.0),
            ),
            record(
                "mb-demo-b650", Category.MOTHERBOARD, 900,
                "socket" to field("AM5"),
                "chipset" to field("B650"),
                "biosGeneration" to field(null, VerificationState.EMPTY),
                "memoryGeneration" to field("DDR5"),
                "memorySlots" to field("4", numeric = 4.0),
                "maxMemoryGb" to field("128", numeric = 128.0),
                "formFactor" to field("ATX"),
                "storageInterfaces" to field("M2_NVME,SATA"),
            ),
            record(
                "mem-demo-ddr5-32", Category.MEMORY, 500,
                "generation" to field("DDR5"),
                "moduleCapacityGb" to field("16", numeric = 16.0),
                "kitCapacityGb" to field("32", numeric = 32.0),
                "moduleCount" to field("2", numeric = 2.0),
            ),
            gpu337,
            gpu300,
            record(
                "psu-demo-750", Category.PSU, 600,
                "continuousW" to field("750", numeric = 750.0),
                "connectors" to field("12VHPWR,8PIN_X2"),
            ),
            record(
                "case-demo-320", Category.CASE, 400,
                "maxGpuLengthMm" to field("320", numeric = 320.0),
                "maxGpuThicknessMm" to field("60", numeric = 60.0),
                "maxGpuSlots" to field("3", numeric = 3.0),
                "maxCoolerHeightMm" to field("160", numeric = 160.0),
                "supportedFormFactors" to field("ATX,MICRO_ATX"),
            ),
            record(
                "cooler-demo-160", Category.COOLER, 300,
                "heightMm" to field("160", numeric = 160.0),
                "socketMounts" to field("AM5,AM4,LGA1700"),
            ),
            record(
                "storage-demo-nvme", Category.STORAGE, 200,
                "interface" to field("M2_NVME"),
            ),
        ),
    )

    private val gpu337 = record(
        "gpu-demo-337", Category.GPU, 3400,
        "lengthMm" to field("337", numeric = 337.0),
        "thicknessMm" to field("61", numeric = 61.0),
        "slotWidth" to field("3", numeric = 3.0),
        "tdpW" to field("285", numeric = 285.0),
        "requiredPsuConnectors" to field("12VHPWR,8PIN_X2"),
    )

    private val gpu300 = record(
        "gpu-demo-300", Category.GPU, 3500,
        "lengthMm" to field("300", numeric = 300.0),
        "thicknessMm" to field("55", numeric = 55.0),
        "slotWidth" to field("3", numeric = 3.0),
        "tdpW" to field("200", numeric = 200.0),
        "requiredPsuConnectors" to field("12VHPWR,8PIN_X2"),
    )

    private fun field(
        value: String?,
        state: VerificationState = VerificationState.VERIFIED,
        numeric: Double? = null,
    ) = FieldValue(value = value, state = state, numeric = numeric)

    private fun record(
        id: String,
        category: Category,
        price: Long,
        vararg attributes: Pair<String, FieldValue>,
    ) = CatalogueRecord(
        componentId = id,
        category = category,
        manufacturer = "demo",
        model = id,
        partNumber = id,
        attributes = attributes.toMap(),
        priceCny = price,
        priceDate = "2026-10-31",
        catalogueVersion = "static-v1",
        isDemo = true,
    )

    private class MapCatalogue(private val items: List<CatalogueRecord>) : CataloguePort {
        private val byId = items.associateBy { it.componentId }

        fun records(): List<CatalogueRecord> = items

        override fun getById(componentId: String): CatalogueRecord? = byId[componentId]

        override fun search(query: String, category: Category?): List<CatalogueRecord> =
            items.filter { category == null || it.category == category }.sortedBy { it.componentId }

        override fun listByCategory(category: Category): List<CatalogueRecord> =
            items.filter { it.category == category }.sortedBy { it.componentId }

        override fun catalogueVersion(): String = "static-v1"
        override fun priceDate(): String = "2026-10-31"
    }
}
