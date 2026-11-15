package pcbuildmate.ui.preview

import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.CatalogueRecord

/** 3D 场景的最小几何模型：只用轴对齐立方体，尺寸全部毫米。 */
data class Vec3(val x: Float, val y: Float, val z: Float)

enum class BoxStyle { GPU, CASE, COOLER, BOARD }

data class Box3(
    val id: String,
    val label: String,
    val center: Vec3,
    val size: Vec3,
    val style: BoxStyle,
    val conflict: Boolean = false,
    /** 只画半透明轮廓（机箱允许空间），不遮挡内部零件。 */
    val ghost: Boolean = false,
)

enum class Tone { NORMAL, WARN, MUTED }

data class DimLabel(val text: String, val tone: Tone)

/**
 * 尺寸示意场景。
 *
 * 诚实边界：只有目录里有的字段才画真实尺寸；缺的字段用常量并写进 [notes]，
 * 绝不假装这是产品外观模型。
 */
data class Scene3d(
    val boxes: List<Box3>,
    val labels: List<DimLabel>,
    val notes: List<String>,
    val maxExtentMm: Float,
    val hasConflict: Boolean,
)

object SceneBuilder {

    /** 显卡高度字段不在契约里；ATX 挡板高度约 120mm，作为估算值并标注。 */
    const val ASSUMED_GPU_HEIGHT_MM = 120f

    private const val MIN_EXTENT_MM = 60f

    fun build(revision: BuildRevision?, catalogue: CataloguePort): Scene3d? {
        val items = revision?.items ?: return null
        if (items.isEmpty()) return null

        fun record(category: Category): CatalogueRecord? =
            items.firstOrNull { it.category == category }?.let { catalogue.getById(it.componentId) }

        val gpu = record(Category.GPU)
        val case = record(Category.CASE)
        val cooler = record(Category.COOLER)
        val board = record(Category.MOTHERBOARD)

        val gpuLength = gpu?.num("lengthMm")
        val gpuThickness = gpu?.num("thicknessMm") ?: 50f
        val caseLength = case?.num("maxGpuLengthMm")
        val caseThickness = case?.num("maxGpuThicknessMm") ?: 80f
        val caseHeight = case?.num("maxCoolerHeightMm") ?: 170f
        val coolerHeight = cooler?.num("heightMm")

        val depth = maxOf(caseLength ?: gpuLength ?: 320f, gpuLength ?: 0f, 280f)
        val height = maxOf(caseHeight, coolerHeight ?: 0f, 150f)
        val width = maxOf(caseThickness, 180f)

        val boxes = mutableListOf<Box3>()
        val notes = mutableListOf<String>()

        boxes += Box3(
            id = "case",
            label = "机箱允许空间",
            center = Vec3(depth / 2f, height / 2f, 0f),
            size = Vec3(depth, height, width),
            style = BoxStyle.CASE,
            ghost = true,
        )
        val labels = mutableListOf<DimLabel>()
        if (caseLength != null) {
            labels += DimLabel("机箱上限 ${fmt(caseLength)} mm", Tone.NORMAL)
        } else {
            labels += DimLabel("未选择机箱 · 仅看比例", Tone.MUTED)
            notes += "配置里没有机箱条目：盒体按显卡长度占位，仅用于看比例，不代表机箱上限"
        }

        if (board != null) {
            val (bw, bd) = boardFormFactor(board.text("formFactor"))
            boxes += Box3(
                id = "board",
                label = "主板 ${board.text("formFactor")}",
                center = Vec3(bd / 2f, 3f, 0f),
                size = Vec3(bd, 6f, bw),
                style = BoxStyle.BOARD,
            )
        }

        if (gpuLength != null) {
            val overLength = caseLength != null && gpuLength > caseLength
            boxes += Box3(
                id = "gpu",
                label = "显卡 ${gpu.text("model")}",
                center = Vec3(gpuLength / 2f, ASSUMED_GPU_HEIGHT_MM / 2f + 12f, 0f),
                size = Vec3(gpuLength, ASSUMED_GPU_HEIGHT_MM, gpuThickness),
                style = BoxStyle.GPU,
                conflict = overLength,
            )
            labels += DimLabel("显卡 ${fmt(gpuLength)} mm", if (overLength) Tone.WARN else Tone.NORMAL)
            if (overLength) {
                labels += DimLabel("超出 ${fmt(gpuLength - caseLength)} mm", Tone.WARN)
            }
            notes += "显卡高度按 ${fmt(ASSUMED_GPU_HEIGHT_MM)} mm 估算（目录无该字段）"
        } else {
            labels += DimLabel("无独显 · 核显输出", Tone.MUTED)
            notes += "当前配置没有显卡条目：3D 图只显示机箱与主板"
        }

        if (cooler != null && coolerHeight != null) {
            val overHeight = coolerHeight > caseHeight
            boxes += Box3(
                id = "cooler",
                label = "散热 ${cooler.text("model")}",
                center = Vec3(depth * 0.62f, coolerHeight / 2f + 6f, 0f),
                size = Vec3(130f, coolerHeight, 130f),
                style = BoxStyle.COOLER,
                conflict = overHeight,
            )
            labels += DimLabel(
                "散热 ${fmt(coolerHeight)} / 限高 ${fmt(caseHeight)} mm",
                if (overHeight) Tone.WARN else Tone.NORMAL,
            )
        }

        notes += "盒体只表达尺寸与间隙，不表达外观；红框 = 超出机箱上限"

        return Scene3d(
            boxes = boxes,
            labels = labels,
            notes = notes,
            maxExtentMm = maxOf(depth, height, width, MIN_EXTENT_MM),
            hasConflict = boxes.any { it.conflict },
        )
    }

    private fun CatalogueRecord.num(path: String): Float? =
        attributes[path]?.numeric?.toFloat() ?: attributes[path]?.value?.toFloatOrNull()

    private fun CatalogueRecord.text(path: String): String =
        attributes[path]?.value ?: "?"

    private fun boardFormFactor(value: String): Pair<Float, Float> = when (value.uppercase()) {
        "ATX" -> 244f to 305f
        "MICRO_ATX" -> 244f to 244f
        "MINI_ITX" -> 170f to 170f
        "EATX" -> 272f to 330f
        else -> 244f to 244f
    }

    fun fmt(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString()
        else String.format(java.util.Locale.ROOT, "%.1f", value)
}
