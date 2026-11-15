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
    fun `gpu is long on X, thick on Y, full-height on Z`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))
        val gpu = scene.boxes.first { it.style == BoxStyle.GPU }

        assertEquals(337f, gpu.size.x, 0.01f, "长度沿 X（前后）")
        assertEquals(61f, gpu.size.y, 0.01f, "厚度沿 Y（竖直，2–3 槽）")
        assertEquals(120f, gpu.size.z, 0.01f, "卡身宽度沿 Z（全高档板）")
        assertTrue(gpu.min.z >= 6f, "显卡从主板表面向外")
    }

    @Test
    fun `gpu longer than the case limit is flagged and draws a limit plane`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))
        val gpu = scene.boxes.first { it.style == BoxStyle.GPU }

        assertTrue(gpu.conflict)
        assertTrue(scene.limits.any { it.id == "case-length" && it.conflict })
        assertTrue(scene.labels.any { it.text.contains("超出限长 17") })
        assertTrue(scene.hasConflict)
    }

    @Test
    fun `gpu that fits is not flagged`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "300", case = "320"), catalogue()))

        assertTrue(!scene.hasConflict)
        assertTrue(scene.boxes.none { it.conflict })
        assertTrue(scene.limits.none { it.conflict })
    }

    @Test
    fun `motherboard uses ATX depth and height`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "300", case = "320"), catalogue()))
        val board = scene.boxes.first { it.style == BoxStyle.BOARD }

        assertEquals(244f, board.size.x, 0.01f, "ATX 深度 244")
        assertEquals(305f, board.size.y, 0.01f, "ATX 高度 305")
        assertEquals(SceneBuilder.MB_FLOOR_MM, board.min.y, 0.01f)
    }

    @Test
    fun `cooler height is measured along Z`() {
        val scene = assertNotNull(
            SceneBuilder.build(revisionWith(gpu = "300", case = "320", cooler = "160"), catalogue()),
        )
        val cooler = scene.boxes.first { it.style == BoxStyle.COOLER }

        assertEquals(160f, cooler.size.z, 0.01f, "散热高度沿 Z（到侧板）")
        assertTrue(!cooler.conflict)
    }

    @Test
    fun `cooler taller than the case limit is flagged`() {
        val scene = assertNotNull(
            SceneBuilder.build(revisionWith(gpu = "300", case = "320", cooler = "190"), catalogue()),
        )

        assertTrue(scene.boxes.first { it.style == BoxStyle.COOLER }.conflict)
        assertTrue(scene.limits.any { it.id == "case-cooler" && it.conflict })
    }

    @Test
    fun `case is a ghost container so it cannot hide parts`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = "337", case = "320"), catalogue()))

        assertTrue(scene.boxes.first { it.style == BoxStyle.CASE }.ghost)
        assertTrue(!scene.boxes.first { it.style == BoxStyle.GPU }.ghost)
    }

    @Test
    fun `no gpu still builds a scene with a note`() {
        val scene = assertNotNull(SceneBuilder.build(revisionWith(gpu = null, case = "320"), catalogue()))

        assertTrue(scene.boxes.none { it.style == BoxStyle.GPU })
        assertTrue(scene.labels.any { it.text.contains("无独显") })
        assertTrue(scene.notes.any { it.contains("没有显卡") })
    }

    @Test
    fun `projection keeps the scene center on screen center`() {
        val pose = ViewPose(yaw = 0.7f, pitch = 0.3f, distance = 1200f)
        val p = project(Vec3(10f, 10f, 0f), Vec3(10f, 10f, 0f), pose, 800f, 600f)

        assertEquals(400f, p.x, 0.01f)
        assertEquals(300f, p.y, 0.01f)
    }

    @Test
    fun `rotation by 90 degrees swaps x and z`() {
        val r = rotate(Vec3(1f, 0f, 0f), (Math.PI / 2).toFloat(), 0f)

        assertTrue(abs(r.x) < 1e-4f)
        assertTrue(abs(r.z - 1f) < 1e-4f)
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

    // ---------------------------------------------------------------- fixture

    private fun revisionWith(gpu: String?, case: String, cooler: String? = null): BuildRevision {
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

    private fun catalogue(): CataloguePort = MapCatalogue(
        listOf(
            record("mb", Category.MOTHERBOARD, mapOf("formFactor" to f("ATX"))),
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
