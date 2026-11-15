package pcbuildmate.ui.preview

import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category

/**
 * 3D 场景最小模型（右手系，Z 向上）：
 *
 *   X = 主板法线（0 = 板面，+ 指向机箱内/显卡宽度方向）
 *   Y = 前后深度（0 = 后置 I/O 面，+ 向前/显卡长度方向）
 *   Z = 高度（0 = 主板下沿，+ 向上/PCIe 槽位排列方向）
 *
 * 主板厚度放在 X 负方向：板体 X -6..0，零件从 X=0 往外长。
 * 机箱与主板上沿对齐（caseTop = boardTop）。
 */
data class Vec3(val x: Float, val y: Float, val z: Float)

enum class BoxStyle { GPU, CASE, COOLER, BOARD, IO }

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

    const val BOARD_THICKNESS_MM = 6f
    const val SLOT_PITCH_MM = 20.32f
    /** ATX/mATX 第一个槽：I/O 下缘 - 0.30 in = [7.52] mm（图纸标注，括号内为 mm）。 */
    const val FIRST_SLOT_FROM_IO_MM = 7.52f
    /** Mini-ITX 单槽：从板下缘往上 0.25 in = [6.35] mm。 */
    const val ITX_SLOT_FROM_BOTTOM_MM = 6.35f
    const val IO_WIDTH_MM = 45f      // 沿 X（板法线方向伸出）
    const val IO_THICKNESS_MM = 6f   // 沿 Y
    const val IO_HEIGHT_MM = 160f    // 沿 Z，从板上沿往下
    const val GPU_WIDTH_MM = 110f    // 沿 X
    const val GPU_SLOT_INDEX = 2

    fun build(revision: BuildRevision?, catalogue: CataloguePort): Scene3d? {
        val items = revision?.items ?: return null
        if (items.isEmpty()) return null

        fun record(category: Category): CatalogueRecord? =
            items.firstOrNull { it.category == category }?.let { catalogue.getById(it.componentId) }

        val gpu = record(Category.GPU)
        val case = record(Category.CASE)
        val cooler = record(Category.COOLER)
        val board = record(Category.MOTHERBOARD)

        val formFactor = board?.text("formFactor") ?: "MICRO_ATX"
        val boardHeight = boardHeight(formFactor)
        val boardDepth = boardDepth(formFactor)
        val slotCount = slotCount(formFactor)
        val caseLength = case?.num("maxGpuLengthMm")
        val caseCoolerHeight = case?.num("maxCoolerHeightMm") ?: 160f
        val caseTop = boardHeight

        val boxes = mutableListOf<Box3>()
        val limits = mutableListOf<LimitPlane>()
        val labels = mutableListOf<DimLabel>()
        val notes = mutableListOf<String>()

        // 主板：X -6..0，Y 0..depth，Z 0..height
        boxes += Box3(
            id = "board",
            label = "主板 $formFactor",
            min = Vec3(-BOARD_THICKNESS_MM, 0f, 0f),
            max = Vec3(0f, boardDepth, boardHeight),
            style = BoxStyle.BOARD,
        )

        // I/O：从板上沿往下 160，沿 X 伸出 45，Y 厚 6（垂直于主板）
        boxes += Box3(
            id = "io",
            label = "后置 I/O",
            min = Vec3(0f, 0f, boardHeight - IO_HEIGHT_MM),
            max = Vec3(IO_WIDTH_MM, IO_THICKNESS_MM, boardHeight),
            style = BoxStyle.IO,
        )
        labels += DimLabel("I/O ${fmt(IO_WIDTH_MM)}×${fmt(IO_HEIGHT_MM)}", Tone.MUTED)

        // Mini-ITX 单槽从下缘往上 0.25"；ATX/mATX 从 I/O 下缘往下 0.25"
        val firstSlotZ = if (slotCount == 1) {
            ITX_SLOT_FROM_BOTTOM_MM
        } else {
            (boardHeight - IO_HEIGHT_MM) - FIRST_SLOT_FROM_IO_MM
        }
        fun slotZ(k: Int): Float = firstSlotZ - (k - 1) * SLOT_PITCH_MM
        val usedSlots = (1..slotCount).map { slotZ(it) }
        labels += DimLabel(
            "槽位 Z ${fmt(usedSlots.first())} → ${fmt(usedSlots.last())}（$slotCount 槽）",
            Tone.MUTED,
        )
        notes += if (slotCount == 1) {
            "Mini-ITX 单槽 = 下缘 + 0.25\" = ${fmt(firstSlotZ)}；I/O 下缘 ${fmt(boardHeight - IO_HEIGHT_MM)}，不相交"
        } else {
            "第一个槽 = I/O 下缘 ${fmt(boardHeight - IO_HEIGHT_MM)} - 7.52 = ${fmt(firstSlotZ)}（0.30 in）；向下每槽 20.32"
        }
        if (slotCount == 1) {
            notes += "Mini-ITX 单槽在 Z=${fmt(firstSlotZ)}；3 槽卡会到 Z=${fmt(firstSlotZ - gpuThicknessForHint(gpu))}，允许为负"
        }

        // 显卡
        val gpuLength = gpu?.num("lengthMm")
        val gpuThickness = gpu?.num("thicknessMm") ?: 50f
        val slotIndex = GPU_SLOT_INDEX.coerceAtMost(slotCount)
        val centerZ = slotZ(slotIndex)
        if (gpuLength != null) {
            val overLength = caseLength != null && gpuLength > caseLength
            // 挡板上沿 = 槽位中线；卡身向下占 thickness
            val gpuTop = centerZ
            val gpuBottom = centerZ - gpuThickness
            boxes += Box3(
                id = "gpu",
                label = "显卡 ${gpu.text("model")}",
                min = Vec3(0f, 0f, gpuBottom),
                max = Vec3(GPU_WIDTH_MM, gpuLength, gpuTop),
                style = BoxStyle.GPU,
                conflict = overLength,
            )
            labels += DimLabel("长 ${fmt(gpuLength)} mm", if (overLength) Tone.WARN else Tone.NORMAL)
            labels += DimLabel("厚 ${fmt(gpuThickness)} / ${fmt(gpuThickness / SLOT_PITCH_MM, 1)} 槽", Tone.NORMAL)
            notes += "显卡第 $slotIndex 槽（中线 Z=${fmt(centerZ)}），宽 ${fmt(GPU_WIDTH_MM)}，长度沿 Y"
            if (overLength) labels += DimLabel("超出限长 ${fmt(gpuLength - caseLength!!)} mm", Tone.WARN)
        } else {
            labels += DimLabel("无独显 · 核显输出", Tone.MUTED)
            notes += "当前配置没有显卡条目"
        }

        // 机箱限长面
        if (caseLength != null) {
            labels += DimLabel("机箱限长 ${fmt(caseLength)} mm", if (gpuLength != null && gpuLength > caseLength) Tone.WARN else Tone.NORMAL)
            limits += LimitPlane(
                id = "case-length",
                label = "机箱限长 ${fmt(caseLength)} mm",
                corners = listOf(
                    Vec3(0f, caseLength, centerZ - gpuThickness),
                    Vec3(GPU_WIDTH_MM, caseLength, centerZ - gpuThickness),
                    Vec3(GPU_WIDTH_MM, caseLength, centerZ),
                    Vec3(0f, caseLength, centerZ),
                ),
                conflict = gpuLength != null && gpuLength > caseLength,
            )
        } else {
            labels += DimLabel("未选择机箱 · 不判限长", Tone.MUTED)
        }

        // 散热：高度沿 X，底面在板面上方 CPU 区域
        val coolerHeight = cooler?.num("heightMm")
        if (coolerHeight != null) {
            val overHeight = coolerHeight > caseCoolerHeight
            // CPU 散热在板上部（I/O 下方、Y 向前）
            val z0 = boardHeight - 140f
            val z1 = z0 + 130f
            val y0 = 50f
            boxes += Box3(
                id = "cooler",
                label = "散热 ${cooler.text("model")}",
                // 底面离板面 1mm，避免与主板 X=0 面共面导致排序穿模
                min = Vec3(1f, y0, z0),
                max = Vec3(1f + coolerHeight, y0 + 130f, z1),
                style = BoxStyle.COOLER,
                conflict = overHeight,
            )
            labels += DimLabel("高 ${fmt(coolerHeight)} / 限高 ${fmt(caseCoolerHeight)}", if (overHeight) Tone.WARN else Tone.NORMAL)
            limits += LimitPlane(
                id = "case-cooler",
                label = "侧板限高 ${fmt(caseCoolerHeight)}",
                corners = listOf(
                    Vec3(caseCoolerHeight, y0, z0),
                    Vec3(caseCoolerHeight, y0 + 130f, z0),
                    Vec3(caseCoolerHeight, y0 + 130f, z1),
                    Vec3(caseCoolerHeight, y0, z1),
                ),
                conflict = overHeight,
            )
        }

        // 机箱允许空间：透明 ghost，上沿与主板上沿对齐
        val caseWidth = maxOf(GPU_WIDTH_MM, coolerHeight ?: 160f, IO_WIDTH_MM) + 20f
        val caseDepth = maxOf(caseLength ?: 320f, 300f) + 70f
        boxes += Box3(
            id = "case",
            label = "机箱允许空间",
            min = Vec3(-10f, -20f, 0f),
            max = Vec3(caseWidth, caseDepth, caseTop),
            style = BoxStyle.CASE,
            ghost = true,
        )

        notes += "盒体只表达尺寸与间隙；X=法线，Y=深度，Z=高度（docs/18）"

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

    private fun gpuThicknessForHint(gpu: CatalogueRecord?): Float =
        gpu?.num("thicknessMm") ?: 50f

    private fun boardHeight(formFactor: String): Float = when (formFactor.uppercase()) {
        "ATX" -> 305f
        "MICRO_ATX" -> 244f
        "MINI_ITX" -> 170f
        "EATX" -> 330f
        else -> 244f
    }

    private fun boardDepth(formFactor: String): Float = when (formFactor.uppercase()) {
        "ATX" -> 244f
        "MICRO_ATX" -> 244f
        "MINI_ITX" -> 170f
        "EATX" -> 305f
        else -> 244f
    }

    private fun slotCount(formFactor: String): Int = when (formFactor.uppercase()) {
        "ATX" -> 7
        "MICRO_ATX" -> 4
        "MINI_ITX" -> 1
        "EATX" -> 7
        else -> 4
    }

    private fun CatalogueRecord.num(path: String): Float? =
        attributes[path]?.numeric?.toFloat() ?: attributes[path]?.value?.toFloatOrNull()

    private fun CatalogueRecord.text(path: String): String = attributes[path]?.value ?: "?"

    fun fmt(value: Float, decimals: Int = 0): String =
        if (decimals == 0 && value % 1f == 0f) value.toInt().toString()
        else String.format(java.util.Locale.ROOT, "%.${decimals}f", value)

    fun fmt(value: Float): String = fmt(value, 0)
}
