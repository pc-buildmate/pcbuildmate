package pcbuildmate.data.catalogue

import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.VerificationState
import pcbuildmate.contracts.test.CataloguePortContract

class InMemoryCatalogueContractTest : CataloguePortContract() {

    override fun newPort(records: List<CatalogueRecord>): CataloguePort = InMemoryCatalogue(records)

    override fun sampleRecords(): List<CatalogueRecord> = listOf(
        record(
            id = "gpu-1",
            category = Category.GPU,
            model = "RTX 4070 Ti",
            partNumber = "DEMO-4070",
        ),
        record(
            id = "cpu-1",
            category = Category.CPU,
            model = "Ryzen 5 7600X",
            partNumber = "R5-7600X",
        ),
    )

    private fun record(
        id: String,
        category: Category,
        model: String,
        partNumber: String,
    ) = CatalogueRecord(
        componentId = id,
        category = category,
        manufacturer = "test",
        model = model,
        partNumber = partNumber,
        attributes = mapOf(
            "socket" to FieldValue(value = "AM5", state = VerificationState.VERIFIED),
        ),
        priceCny = 1000,
        priceDate = "2026-10-31",
        catalogueVersion = "test-v1",
        isDemo = true,
    )
}
