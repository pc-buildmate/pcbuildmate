package pcbuildmate.domain.revision

import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Category
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RevisionInvariantsTest {

    @Test
    fun `inconsistent total is rejected`() {
        assertFailsWith<IllegalArgumentException>("INV-01: totalCny 必须等于条目合计") {
            BuildRevision(
                id = 1,
                buildId = "b",
                items = listOf(BuildItem("cpu", Category.CPU, 1, 100)),
                totalCny = 101,
                catalogueVersion = "v1",
                priceDate = "2026-11-04",
                userModified = false,
            )
        }
    }
}
