package pcbuildmate.oracle

import pcbuildmate.contracts.BuildItem

/**
 * 总价 / 预算算术的参考模型（docs/08 §8.3 的「总价 / 预算算术」一项）。
 *
 * 与生产实现的区别：这里只有算术，没有构造校验。生产的 `BuildRevision`
 * 在构造时就强制 `totalCny == Σ(qty × price)`（INV-01），所以这一项不需要
 * 交叉校验——但把公式单独写出来，可以让"总价怎么算的"在文档和测试里可见，
 * 而不是藏在某个 data class 的 init 里。
 *
 * 刻意简单：整数人民币，不做汇率、不做四舍五入、不处理负数（价格与数量
 * 的类型本身已排除负值）。
 */
object OracleBudget {

    /** 总价 = Σ(数量 × 单价)，与 INV-01 的定义一致。 */
    fun totalOf(items: List<BuildItem>): Long =
        items.sumOf { it.quantity.toLong() * it.unitPriceCny }

    /** 是否在预算内（含等于）。 */
    fun fits(totalCny: Long, budgetCny: Long): Boolean = totalCny <= budgetCny

    /** 超出预算的金额；未超出时为 0。 */
    fun overBy(totalCny: Long, budgetCny: Long): Long = (totalCny - budgetCny).coerceAtLeast(0L)

    /** 剩余预算；超出时为 0（与 overBy 互斥）。 */
    fun remaining(totalCny: Long, budgetCny: Long): Long = (budgetCny - totalCny).coerceAtLeast(0L)
}
