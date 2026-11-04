package pcbuildmate.contracts

/**
 * 目录类目。首版恰好八个；新增类目走契约变更（02 章 §2.9）。
 * 顺序不重要，禁止依赖枚举顺序做逻辑。
 */
enum class Category { CPU, MOTHERBOARD, MEMORY, GPU, PSU, CASE, COOLER, STORAGE }

/**
 * 字段级证据状态。三层状态的第一层（01 章 §1.1）。
 * 顺序即强弱：EMPTY 最弱，VERIFIED 最强。禁止用 enumeration index 做比较之外的用途。
 */
enum class VerificationState { VERIFIED, REPORTED, EMPTY }

/** 冲突严重度。BLOCKER 表示装不上/开不了机；MAJOR 表示受限；MINOR 表示体验损失。 */
enum class Severity { BLOCKER, MAJOR, MINOR }

/** 用途。决定预算分配权重（D4），不影响规则判定。 */
enum class UseCase { GAMING, STUDY_OFFICE, CONTENT_CREATION, LOCAL_AI }

/** 一个来源给出的原始值与它自己的证据态。 */
data class FieldSource(
    val source: String,
    val rawValue: String,
    val state: VerificationState,
)

/**
 * 一个字段的合并视图。
 *
 * 不变量：
 * - [conflict] 为 true 时，[sources] 至少两个且数值互不相同；
 * - [conflict] 为 true 时 [state] 必须为最弱来源态；
 * - [numeric] 只在字段是数值型时有意义，解析失败必须为 null 而不是 0.0。
 */
data class FieldValue(
    val value: String?,
    val state: VerificationState,
    val sources: List<FieldSource> = emptyList(),
    val conflict: Boolean = false,
    val numeric: Double? = null,
) {
    init {
        require(value != null || state == VerificationState.EMPTY) {
            "value 只能为 null 当 state == EMPTY"
        }
        require(!conflict || sources.size >= 2) {
            "conflict=true 需要至少两个 FieldSource"
        }
    }
}

/** 指向某个部件的某个字段。字段路径必须在 03 章 §3.3 的冻结表内。 */
data class FieldRef(
    val componentId: String,
    val fieldPath: String,
)

/**
 * 一条硬件记录。只读；不参与兼容性判断。
 * 所有 attributes 的 key 必须是冻结路径（03 章 §3.3），否则启动时 fail-fast。
 */
data class CatalogueRecord(
    val componentId: String,
    val category: Category,
    val manufacturer: String,
    val model: String,
    val partNumber: String,
    val attributes: Map<String, FieldValue>,
    val priceCny: Long,
    val priceDate: String,
    val catalogueVersion: String,
    val isDemo: Boolean,
)

/** 配置中的一条。quantity >= 1；unitPriceCny 是下单时的快照价，不随后续价格变动。 */
data class BuildItem(
    val componentId: String,
    val category: Category,
    val quantity: Int,
    val unitPriceCny: Long,
) {
    init {
        require(quantity >= 1) { "quantity 必须 >= 1" }
        require(unitPriceCny >= 0) { "unitPriceCny 必须 >= 0" }
    }
}

/**
 * 配置的不可变快照。
 *
 * 不变量（INV-01）：totalCny == items.sumOf { quantity * unitPriceCny }。
 * 任何构造都会校验，因此"先建对象后补总价"在类型层面就不可行。
 */
data class BuildRevision(
    val id: Long,
    val buildId: String,
    val items: List<BuildItem>,
    val totalCny: Long,
    val catalogueVersion: String,
    val priceDate: String,
    val userModified: Boolean,
) {
    init {
        require(id >= 1L) { "revision id 从 1 开始" }
        require(totalCny == computeTotal(items)) {
            "totalCny 与条目不一致：expected=" + computeTotal(items) + " actual=" + totalCny
        }
    }

    companion object {
        @JvmStatic
        fun computeTotal(items: List<BuildItem>): Long =
            items.sumOf { it.quantity.toLong() * it.unitPriceCny }
    }
}

/** 用户偏好。只影响推荐权重与提示，不参与硬约束。 */
data class Preferences(
    val caseSize: String? = null,
    val noiseLevel: String? = null,
    val upgradeRoom: Boolean = false,
)

/** 一份配置工程。currentRevisionId 指向当前快照；保存的配置之间可以比较。 */
data class Build(
    val buildId: String,
    val budgetCny: Long,
    val useCase: UseCase,
    val preferences: Preferences,
    val currentRevisionId: Long,
    val userModified: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
