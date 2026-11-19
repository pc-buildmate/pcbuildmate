package pcbuildmate.oracle

import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 预算算术参考模型的单测。数字取答辩锚点：7800 在 8000 内，7900 仍在 8000 内，
 * 8001 超出 1 元。等号边界单独一条，因为"等于预算"必须算通过。
 */
class OracleBudgetTest {

    private fun item(id: String, price: Long, qty: Int = 1) =
        BuildItem(id, Category.GPU, qty, price)

    @Test
    fun `total multiplies quantity by unit price`() {
        val items = listOf(item("a", 1500), item("b", 900, qty = 2))
        assertEquals(1500L + 900L * 2, OracleBudget.totalOf(items))
    }

    @Test
    fun `empty build totals zero`() {
        assertEquals(0L, OracleBudget.totalOf(emptyList()))
    }

    @Test
    fun `the demo anchors fit the budget`() {
        assertTrue(OracleBudget.fits(7_800, 8_000))
        assertTrue(OracleBudget.fits(7_900, 8_000))
        assertEquals(200L, OracleBudget.remaining(7_800, 8_000))
        assertEquals(100L, OracleBudget.remaining(7_900, 8_000))
    }

    @Test
    fun `equality counts as fitting`() {
        assertTrue(OracleBudget.fits(8_000, 8_000))
        assertEquals(0L, OracleBudget.overBy(8_000, 8_000))
        assertEquals(0L, OracleBudget.remaining(8_000, 8_000))
    }

    @Test
    fun `one yuan over is over`() {
        assertFalse(OracleBudget.fits(8_001, 8_000))
        assertEquals(1L, OracleBudget.overBy(8_001, 8_000))
        assertEquals(0L, OracleBudget.remaining(8_001, 8_000))
    }
}
