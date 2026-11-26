package pcbuildmate.ui.preview

import org.junit.Test
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.VerificationState
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Scene3dTest {

    @Test
    fun `board is 6 thick on negative X and 244 deep by 305 tall`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))
        val board = scene.boxes.first { it.style == BoxStyle.BOARD }

        assertEquals(6f, board.size.x, 0.01f, "板厚 X")
        assertEquals(-6f, board.min.x, 0.01f, "板体在 X 负侧")
        assertEquals(0f, board.max.x, 0.01f, "零件从 X=0 起")
        assertEquals(244f, board.size.y, 0.01f, "ATX 深度 Y")
        assertEquals(305f, board.size.z, 0.01f, "ATX 高度 Z")
    }

    @Test
    fun `io is perpendicular to the board`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))
        val io = scene.boxes.first { it.style == BoxStyle.IO }
        val board = scene.boxes.first { it.style == BoxStyle.BOARD }

        assertEquals(45f, io.size.x, 0.01f, "沿 X 伸出 45")
        assertEquals(6f, io.size.y, 0.01f, "Y 厚 6（板面内，故垂直于板）")
        assertEquals(160f, io.size.z, 0.01f, "Z 高 160")
        assertEquals(305f, io.max.z, 0.01f, "与板上沿对齐")
        assertEquals(board.max.x, io.min.x, 0.01f, "从板面起")
    }

    @Test
    fun `gpu uses width X, length Y, slot thickness Z`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))
        val gpu = scene.boxes.first { it.style == BoxStyle.GPU }

        assertEquals(110f, gpu.size.x, 0.01f, "卡宽沿 X")
        assertEquals(337f, gpu.size.y, 0.01f, "长度沿 Y")
        assertEquals(61f, gpu.size.z, 0.01f, "厚度沿 Z")
        assertEquals(0f, gpu.min.x, 0.01f, "从板面起")
    }

    @Test
    fun `gpu sits at the x16 slot read from the catalogue field`() {
        val scene = assertNotNull(
            SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue(slotMm = 137.5)),
        )
        val gpu = scene.boxes.first { it.style == BoxStyle.GPU }

        assertEquals(137.5f, gpu.max.z, 0.01f, "挡板上沿 = 字段给的 x16 槽中线")
    }

    @Test
    fun `missing x16 slot field falls back to the io offset constant`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))
        val gpu = scene.boxes.first { it.style == BoxStyle.GPU }
        val fallback = 305f - SceneBuilder.IO_HEIGHT_MM - SceneBuilder.FIRST_SLOT_FROM_IO_MM

        assertEquals(137.48f, fallback, 0.01f)
        assertEquals(fallback, gpu.max.z, 0.01f, "缺字段回退 I/O 下缘 - 7.52，放在首槽")
        assertTrue(scene.notes.any { it.contains("缺字段回退") }, "回退要在提示里说明")
    }

    @Test
    fun `slot count comes from the field when present`() {
        val scene = assertNotNull(
            SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue(slotMm = 137.5, slotCount = 4)),
        )

        assertTrue(scene.labels.any { it.text.contains("4 槽") }, "槽数用字段，不用 ATX 推断的 7")
        assertTrue(scene.labels.none { it.text.contains("7 槽") })
    }

    @Test
    fun `itx has one slot and the card may leave the board`() {
        val scene = assertNotNull(
            SceneBuilder.build(revisionWith(gpu = "337", case = "320", formFactor = "MINI_ITX"), catalogue(formFactor = "MINI_ITX")),
        )
        val board = scene.boxes.first { it.style == BoxStyle.BOARD }
        val gpu = scene.boxes.first { it.style == BoxStyle.GPU }

        assertEquals(170f, board.size.z, 0.01f, "ITX 板高 170")
        val slot1 = 170f - SceneBuilder.IO_HEIGHT_MM - SceneBuilder.FIRST_SLOT_FROM_IO_MM
        assertEquals(slot1, gpu.max.z, 0.01f, "ITX 与 ATX 同一规则：I/O 下缘 - 7.52")
        assertEquals(2.48f, gpu.max.z, 0.01f)
        assertTrue(gpu.min.z < 0f, "3 槽卡向下到 Z 负数，不 clamp")
        assertTrue(gpu.max.z < 170f - SceneBuilder.IO_HEIGHT_MM, "槽位在 I/O 下缘以下，不相交")
    }

    @Test
    fun `case top is aligned with board top`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))
        val board = scene.boxes.first { it.style == BoxStyle.BOARD }
        val caseBox = scene.boxes.first { it.style == BoxStyle.CASE }

        assertEquals(board.max.z, caseBox.max.z, 0.01f)
        assertTrue(caseBox.ghost, "机箱必须透明")
    }

    @Test
    fun `gpu longer than the case limit is flagged`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))

        assertTrue(scene.boxes.first { it.style == BoxStyle.GPU }.conflict)
        assertTrue(scene.limits.any { it.id == "case-length" && it.conflict })
        assertTrue(scene.hasConflict)
    }

    @Test
    fun `gpu that fits is not flagged`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "300", case = "320"), catalogue()))

        assertTrue(!scene.hasConflict)
        assertTrue(scene.limits.none { it.conflict })
    }

    @Test
    fun `cooler height is along X`() {
        val scene = assertNotNull(
            SceneBuilder.build(revisionWith(gpu = "300", case = "320", cooler = "160"), catalogue()),
        )
        val cooler = scene.boxes.first { it.style == BoxStyle.COOLER }

        assertEquals(160f, cooler.size.x, 0.01f)
        assertTrue(!cooler.conflict)
    }

    @Test
    fun `projection maps a point ahead of the camera to screen center`() {
        val pose = ViewPose(yaw = -1.35f, pitch = 0.35f, distance = 1200f)
        val p = project(Vec3(10f, 10f, 0f), Vec3(10f, 10f, 0f), pose, 800f, 600f)

        assertEquals(400f, p.x, 0.01f)
        assertEquals(300f, p.y, 0.01f)
    }

    @Test
    fun `rotation by 90 degrees around Z maps X to Y`() {
        val r = rotate(Vec3(1f, 0f, 0f), (Math.PI / 2).toFloat(), 0f)

        assertTrue(abs(r.x) < 1e-4f)
        assertTrue(abs(r.y - 1f) < 1e-4f)
    }

    @Test
    fun `each box exposes six faces and a convex box shows at most three`() {
        val box = Box3("b", "b", Vec3(0f, 0f, 0f), Vec3(100f, 100f, 100f), BoxStyle.CASE)
        val pose = ViewPose()
        val faces = facesOf(box)

        assertEquals(6, faces.size)
        val visible = faces.count { isVisible(it, pose, Vec3(50f, 50f, 50f)) }
        assertTrue(visible in 1..3, "实际 $visible")
    }

    @Test
    fun `surface texture coordinates are bilinear in the face plane`() {
        val quad = listOf(P2(0f, 0f, 1f), P2(100f, 20f, 1f), P2(90f, 80f, 1f), P2(10f, 60f, 1f))

        assertEquals(0f, bilinear(quad, 0f, 0f).x, 0.01f)
        assertEquals(100f, bilinear(quad, 1f, 0f).x, 0.01f)
        assertEquals(50f, bilinear(quad, 0.5f, 0.5f).x, 0.01f)
        assertEquals(40f, bilinear(quad, 0.5f, 0.5f).y, 0.01f)
    }

    @Test
    fun `no case means no cooler height verdict and no fake conflict`() {
        val scene = assertNotNull(
            SceneBuilder.build(
                revisionWith(gpu = "337", case = "320", cooler = "190", withCase = false),
                catalogue(),
            ),
        )

        assertTrue(scene.limits.none { it.id == "case-cooler" }, "没有机箱就不该有侧板限高面")
        assertTrue(
            scene.boxes.none { it.style == BoxStyle.COOLER && it.conflict },
            "没有限高数据时不能把默认 160 当成上限去判超高",
        )
        assertTrue(scene.labels.any { it.text.contains("不判限高") && it.tone == Tone.MUTED })
    }

    @Test
    fun `no case means no invented enclosure box`() {
        val scene = assertNotNull(
            SceneBuilder.build(revisionWith(gpu = "337", case = "320", withCase = false), catalogue()),
        )

        assertTrue(scene.boxes.none { it.style == BoxStyle.CASE }, "没有机箱记录时不该画「机箱允许空间」")
        assertTrue(scene.labels.any { it.text.contains("未选择机箱") })
    }

    @Test
    fun `gpu thicker than the case limit is flagged like the rule engine does`() {
        val scene = assertNotNull(
            SceneBuilder.build(
                revisionWith(gpu = "337", case = "320"),
                // 限长放宽到 400，只让厚度成为冲突（显卡 61，机箱 55）
                catalogue(caseMaxGpuLength = 400.0, caseMaxGpuThickness = 55.0),
            ),
        )
        val gpu = scene.boxes.first { it.style == BoxStyle.GPU }

        assertTrue(gpu.conflict, "厚度超限要和 `GPU_CASE_THICKNESS` 一样标红")
        assertTrue(scene.hasConflict)
        assertTrue(scene.labels.any { it.text.contains("超出限厚") })
    }

    @Test
    fun `slot label keeps one decimal instead of rounding to whole millimetres`() {
        val scene = assertNotNull(
            SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue(slotMm = 137.5)),
        )

        assertTrue(scene.labels.any { it.text.contains("137.5") }, "槽位标签应与目录字段一致")
        assertTrue(scene.labels.none { it.text.contains("槽位 Z 138") })
    }

    @Test
    fun `top view pitch survives the drag clamp`() {
        assertEquals(1.5708f, clampPitch(1.5708f), 1e-4f, "俯视预设不能被拖动用的限制压回去")
        assertTrue(clampPitch(3f) <= PITCH_LIMIT)
        assertTrue(clampPitch(-3f) >= -PITCH_LIMIT)
    }

    // ---------------------------------------------------------------- fixture

    private fun revisionWith(
        gpu: String?,
        case: String,
        cooler: String? = null,
        formFactor: String = "ATX",
        withCase: Boolean = true,
    ): BuildRevision {
        val items = mutableListOf(
            BuildItem("mb", Category.MOTHERBOARD, 1, 900),
        )
        if (withCase) items += BuildItem("case-$case", Category.CASE, 1, 400)
        if (gpu != null) items += BuildItem("gpu-$gpu", Category.GPU, 1, 3400)
        if (cooler != null) items += BuildItem("cooler-$cooler", Category.COOLER, 1, 300)
        return BuildRevision(
            id = 1,
            buildId = "scene",
            items = items,
            totalCny = BuildRevision.computeTotal(items),
            catalogueVersion = "v1",
            priceDate = "2026-11-16",
            userModified = false,
        )
    }

    private fun catalogue(
        formFactor: String = "ATX",
        slotMm: Double? = null,
        slotCount: Int? = null,
        caseMaxGpuLength: Double = 320.0,
        caseMaxGpuThickness: Double = 80.0,
        caseMaxCoolerHeight: Double = 175.0,
    ): CataloguePort {
        val boardAttrs = buildMap {
            put("formFactor", f(formFactor))
            if (slotMm != null) put("pcieX16SlotMm", f(fmtOf(slotMm), slotMm))
            if (slotCount != null) put("pcieSlotCount", f(slotCount.toString(), slotCount.toDouble()))
        }
        return catalogueOf(boardAttrs, caseMaxGpuLength, caseMaxGpuThickness, caseMaxCoolerHeight)
    }

    private fun fmtOf(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private fun catalogueOf(
        boardAttrs: Map<String, FieldValue>,
        caseMaxGpuLength: Double = 320.0,
        caseMaxGpuThickness: Double = 80.0,
        caseMaxCoolerHeight: Double = 175.0,
    ): CataloguePort = MapCatalogue(
        listOf(
            record("mb", Category.MOTHERBOARD, boardAttrs),
            record("case-320", Category.CASE, mapOf(
                "maxGpuLengthMm" to f(fmtOf(caseMaxGpuLength), caseMaxGpuLength),
                "maxGpuThicknessMm" to f(fmtOf(caseMaxGpuThickness), caseMaxGpuThickness),
                "maxCoolerHeightMm" to f(fmtOf(caseMaxCoolerHeight), caseMaxCoolerHeight),
            )),
            record("gpu-337", Category.GPU, mapOf(
                "lengthMm" to f("337", 337.0),
                "thicknessMm" to f("61", 61.0),
            )),
            record("gpu-300", Category.GPU, mapOf(
                "lengthMm" to f("300", 300.0),
                "thicknessMm" to f("50", 50.0),
            )),
            record("cooler-160", Category.COOLER, mapOf("heightMm" to f("160", 160.0))),
            record("cooler-190", Category.COOLER, mapOf("heightMm" to f("190", 190.0))),
        ),
    )

    private fun f(value: String, numeric: Double? = null) =
        FieldValue(value = value, state = VerificationState.VERIFIED, numeric = numeric)

    private fun record(id: String, category: Category, attributes: Map<String, FieldValue>) =
        CatalogueRecord(
            componentId = id,
            category = category,
            manufacturer = "demo",
            model = id,
            partNumber = id,
            attributes = attributes,
            priceCny = 100,
            priceDate = "2026-11-16",
            catalogueVersion = "v1",
            isDemo = true,
        )

    private class MapCatalogue(private val records: List<CatalogueRecord>) : CataloguePort {
        private val byId = records.associateBy { it.componentId }
        override fun getById(componentId: String) = byId[componentId]
        override fun search(query: String, category: Category?) = records
        override fun listByCategory(category: Category) = records.filter { it.category == category }
        override fun catalogueVersion() = "v1"
        override fun priceDate() = "2026-11-16"
    }
}
