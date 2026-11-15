package pcbuildmate.ui.preview

import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category

/**
 * 3D 场景最小模型。坐标系见 docs/18-3d-coordinates.md：
 *
 *   X = 0 机箱背板内侧，+ 向前（深度）
 *   Y = 0 机箱底部内壁，+ 向上
 *   Z = 0 主板表面，+ 向左侧板
 *
 * 盒体用 min/max 毫米坐标表达；先列坐标再画图，避免轴搞反。
 */
data class Vec3(val x: Float, val y: Float, val z: Float)

enum class BoxStyle { GPU, CASE, COOLER, BOARD }

data class Box3(
    val id: String,
    val label: String,
    val min: Vec3,
    val max: Vec3,
    val style: BoxStyle,
    val conflict: Boolean = false,
    val ghost: Boolean = false,
) {
    val center: Vec3
        get() = Vec3((min.x + max.x) / 2f, (min.y + max.y) / 2f, (min.z + max.z) / 2f)
    val size: Vec3
        get() = Vec3(max.x - min.x, max.y - min.y, max.z - min.z)
}

/** 半透明限制面（例如机箱限长 320mm 所在的平面）。 */
data class LimitPlane(
    val id: String,
    val label: String,
    val corners: List<Vec3>,
    val conflict: Boolean,
)

enum class Tone { NORMAL, WARN, MUTED }

data class DimLabel(val text: String, val tone: Tone)

data class Scene3d(
    val boxes: List<Box3>,
    val limits: List<LimitPlane>,
    val labels: List<DimLabel>,
    val notes: List<String>,
    val min: Vec3,
    val max: Vec3,
    val hasConflict: Boolean,
) {
    val extentX: Float get() = max.x - min.x
    val extentY: Float get() = max.y - min.y
    val extentZ: Float get() = max.z - min.z
    val center: Vec3 get() = Vec3((min.x + max.x) / 2f, (min.y + max.y) / 2f, (min.z + max.z) / 2f)
    val radius: Float get() = maxOf(extentX, extentY, extentZ, 100f)
}

object SceneBuilder {

    // ---- 规范常量（docs/18 §18.2）------------------------------------
    const val SLOT_PITCH_MM = 20.32f
    const val BRACKET_HEIGHT_MM = 120f
    const val IO_HEIGHT_MM = 44.45f
    const val MB_FLOOR_MM = 80f
    const val GPU_SLOT_INDEX = 2
    const val BOARD_THICKNESS_MM = 6f
    const val GPU_WIDTH_MM = 120f

    fun build(revision: BuildRevision?, catalogue: CataloguePort): Scene3d? {
        val items = revision?.items ?: return null
        if (items.isEmpty()) return null

        fun record(category: Category): CatalogueRecord? =
            items.firstOrNull { it.category == category }?.let { catalogue.getById(it.componentId) }

        val gpu = record(Category.GPU)
        val case = record(Category.CASE)
        val cooler = record(Category.COOLER)
        val board = record(Category.MOTHERBOARD)

        val (boardDepth, boardHeight) = boardDims(board?.text("formFactor"))
        val mbTop = MB_FLOOR_MM + boardHeight
        val caseLength = case?.num("maxGpuLengthMm")
        val caseCoolerHeight = case?.num("maxCoolerHeightMm") ?: 160f

        val boxes = mutableListOf<Box3>()
        val limits = mutableListOf<LimitPlane>()
        val labels = mutableListOf<DimLabel>()
        val notes = mutableListOf<String>()

        // 主板：X 深度、Y 高度、Z 厚度
        boxes += Box3(
            id = "board",
            label = "主板 ${board?.text("formFactor") ?: "?"}",
            min = Vec3(0f, MB_FLOOR_MM, 0f),
            max = Vec3(boardDepth, mbTop, BOARD_THICKNESS_MM),
            style = BoxStyle.BOARD,
        )

        // 机箱允许空间（估算，ghost 容器）
        val caseDepth = maxOf(caseLength ?: 320f, 300f) + 70f
        val caseTop = mbTop + 35f
        val caseWidth = BOARD_THICKNESS_MM + caseCoolerHeight
        boxes += Box3(
            id = "case",
            label = "机箱允许空间",
            min = Vec3(-15f, 0f, -10f),
            max = Vec3(caseDepth, caseTop, caseWidth),
            style = BoxStyle.CASE,
            ghost = true,
        )

        // 显卡：长度 X，厚度 Y（2–3 个槽位），卡身宽度 Z
        val gpuLength = gpu?.num("lengthMm")
        val gpuThickness = gpu?.num("thicknessMm") ?: 50f
        if (gpuLength != null) {
            val bracketTop = mbTop - IO_HEIGHT_MM - (GPU_SLOT_INDEX - 1) * SLOT_PITCH_MM
            val gpuMinY = bracketTop - gpuThickness
            val overLength = caseLength != null && gpuLength > caseLength
            boxes += Box3(
                id = "gpu",
                label = "显卡 ${gpu.text("model")}",
                min = Vec3(0f, gpuMinY, BOARD_THICKNESS_MM),
                max = Vec3(gpuLength, bracketTop, BOARD_THICKNESS_MM + GPU_WIDTH_MM),
                style = BoxStyle.GPU,
                conflict = overLength,
            )
            labels += DimLabel("显卡长 ${fmt(gpuLength)} mm", if (overLength) Tone.WARN else Tone.NORMAL)
            labels += DimLabel("厚 ${fmt(gpuThickness)} mm（${fmt(gpuThickness / SLOT_PITCH_MM, 1)} 槽）", Tone.NORMAL)
            notes += "显卡卡身宽度按 ${fmt(GPU_WIDTH_MM)} mm 估算（目录无该字段）；位置取第 $GPU_SLOT_INDEX 个 PCIe 槽位"
            if (overLength) {
                labels += DimLabel("超出限长 ${fmt(gpuLength - caseLength!!)} mm", Tone.WARN)
            }
        } else {
            labels += DimLabel("无独显 · 核显输出", Tone.MUTED)
            notes += "当前配置没有显卡条目：3D 图只显示主板与散热"
        }

        // 机箱限长平面：X = maxGpuLengthMm
        if (caseLength != null) {
            labels += DimLabel("机箱限长 ${fmt(caseLength)} mm", if (gpuLength != null && gpuLength > caseLength) Tone.WARN else Tone.NORMAL)
            val y0 = if (gpuLength != null) mbTop - IO_HEIGHT_MM - 4 * SLOT_PITCH_MM else mbTop - IO_HEIGHT_MM
            limits += LimitPlane(
                id = "case-length",
                label = "机箱限长 ${fmt(caseLength)} mm",
                corners = listOf(
                    Vec3(caseLength, y0, BOARD_THICKNESS_MM),
                    Vec3(caseLength, mbTop - IO_HEIGHT_MM, BOARD_THICKNESS_MM),
                    Vec3(caseLength, mbTop - IO_HEIGHT_MM, caseWidth),
                    Vec3(caseLength, y0, caseWidth),
                ),
                conflict = gpuLength != null && gpuLength > caseLength,
            )
        } else {
            labels += DimLabel("未选择机箱 · 不判限长", Tone.MUTED)
            notes += "配置里没有机箱条目：只显示主板/显卡/散热，不判断限长"
        }

        // 散热器：X/Y 为底面，高度沿 Z（从主板表面到侧板）
        val coolerHeight = cooler?.num("heightMm")
        if (coolerHeight != null) {
            val overHeight = coolerHeight > caseCoolerHeight
            val cx = boardDepth * 0.45f
            val cy = mbTop - 90f
            boxes += Box3(
                id = "cooler",
                label = "散热 ${cooler.text("model")}",
                min = Vec3(cx - 65f, cy - 65f, BOARD_THICKNESS_MM),
                max = Vec3(cx + 65f, cy + 65f, BOARD_THICKNESS_MM + coolerHeight),
                style = BoxStyle.COOLER,
                conflict = overHeight,
            )
            labels += DimLabel(
                "散热高 ${fmt(coolerHeight)} / 限高 ${fmt(caseCoolerHeight)} mm",
                if (overHeight) Tone.WARN else Tone.NORMAL,
            )
            // 散热限高平面：Z = 6 + maxCoolerHeightMm
            val zLimit = BOARD_THICKNESS_MM + caseCoolerHeight
            limits += LimitPlane(
                id = "case-cooler",
                label = "侧板限高 ${fmt(caseCoolerHeight)} mm",
                corners = listOf(
                    Vec3(cx - 65f, cy - 65f, zLimit),
                    Vec3(cx + 65f, cy - 65f, zLimit),
                    Vec3(cx + 65f, cy + 65f, zLimit),
                    Vec3(cx - 65f, cy + 65f, zLimit),
                ),
                conflict = overHeight,
            )
        }

        notes += "盒体只表达尺寸与间隙，不表达外观；红框 = 超出机箱上限"

        val all = boxes
        return Scene3d(
            boxes = all,
            limits = limits,
            labels = labels,
            notes = notes,
            min = Vec3(all.minOf { it.min.x }, all.minOf { it.min.y }, all.minOf { it.min.z }),
            max = Vec3(all.maxOf { it.max.x }, all.maxOf { it.max.y }, all.maxOf { it.max.z }),
            hasConflict = all.any { it.conflict } || limits.any { it.conflict },
        )
    }

    /** 板型 → (X 深度, Y 高度)，见 docs/18 §18.3。 */
    private fun boardDims(formFactor: String?): Pair<Float, Float> = when (formFactor?.uppercase()) {
        "ATX" -> 244f to 305f
        "MICRO_ATX" -> 244f to 244f
        "MINI_ITX" -> 170f to 170f
        "EATX" -> 305f to 330f
        else -> 244f to 244f
    }

    private fun CatalogueRecord.num(path: String): Float? =
        attributes[path]?.numeric?.toFloat() ?: attributes[path]?.value?.toFloatOrNull()

    private fun CatalogueRecord.text(path: String): String = attributes[path]?.value ?: "?"

    fun fmt(value: Float, decimals: Int = 0): String =
        if (decimals == 0 && value % 1f == 0f) value.toInt().toString()
        else String.format(java.util.Locale.ROOT, "%.${decimals}f", value)

    fun fmt(value: Float): String = fmt(value, 0)
}
