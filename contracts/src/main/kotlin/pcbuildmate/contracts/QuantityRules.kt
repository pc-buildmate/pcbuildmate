package pcbuildmate.contracts

/**
 * 数量策略：哪些类目允许 > 1 件。
 *
 * 消费级配置里 CPU / 主板 / 显卡 / 电源 / 机箱 / 散热固定 1 件：
 * - 15 条规则按"单件"评估，PSU 公式只取一颗 CPU 的 TDP；
 * - 允许 2 颗 CPU 会让总价翻倍但功耗判定不变，得到"看起来通过"的错误结论。
 *
 * 内存 / 硬盘允许 > 1 件：容量与槽位由 `MEMORY_SLOT_COUNT` / `MEMORY_CAPACITY_LIMIT`
 * 继续判定，不需要在这里设硬上限（超了应当报冲突，而不是静默拒绝）。
 *
 * 这是 build-level 约束，不是第 16 条成对规则；调用点在 `:feature` 的编辑入口。
 */
object QuantityRules {

    /** 允许多件的类目；其余类目数量恒为 1。 */
    val multiInstance: Set<Category> = setOf(Category.MEMORY, Category.STORAGE)

    fun allowsQuantityAboveOne(category: Category): Boolean = category in multiInstance

    /** 合法数量：>= 1；单件类目只能是 1。 */
    fun isValid(category: Category, quantity: Int): Boolean =
        quantity >= 1 && (quantity == 1 || allowsQuantityAboveOne(category))
}
