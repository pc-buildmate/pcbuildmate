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
    fun `gpu sits at the second slot centerline`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))
        val gpu = scene.boxes.first { it.style == BoxStyle.GPU }
        val first = 305f - SceneBuilder.IO_HEIGHT_MM - SceneBuilder.FIRST_SLOT_FROM_IO_MM
        val expected = first - SceneBuilder.SLOT_PITCH_MM

        assertEquals(expected, gpu.max.z, 0.01f, "挡板上沿 = 槽位中线")
        assertEquals(117.16f, expected, 0.01f)
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

    // ---------------------------------------------------------------- fixture

    private fun revisionWith(
        gpu: String?,
        case: String,
        cooler: String? = null,
        formFactor: String = "ATX",
    ): BuildRevision {
        val items = mutableListOf(
            BuildItem("mb", Category.MOTHERBOARD, 1, 900),
            BuildItem("case-$case", Category.CASE, 1, 400),
        )
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

    private fun catalogue(formFactor: String = "ATX"): CataloguePort = MapCatalogue(
        listOf(
            record("mb", Category.MOTHERBOARD, mapOf("formFactor" to f(formFactor))),
            record("case-320", Category.CASE, mapOf(
                "maxGpuLengthMm" to f("320", 320.0),
                "maxGpuThicknessMm" to f("80", 80.0),
                "maxCoolerHeightMm" to f("175", 175.0),
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
