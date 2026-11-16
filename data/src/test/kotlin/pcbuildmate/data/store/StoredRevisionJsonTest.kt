package pcbuildmate.data.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Room 持久化载体的字段契约（DATA-04）。
 *
 * `RoomBuildStore` 把 `BuildRevision` 的条目列表序列化进 `revisions.itemsJson`，
 * `RoomDraftStore` 把整份草稿序列化进 `drafts.revisionJson`，两者都落在
 * [StoredRevision] / [StoredItem] 这两个 `@Serializable` 载体上。
 *
 * 这层映射此前只有 `RoomStoreContractTest`（androidTest，需要模拟器）覆盖，
 * JVM 单测是空白：字段静默改名不会有任何测试变红。本类把载体的 JSON 形状、
 * 顺序与容错行为钉住，改名即失败。
 */
class StoredRevisionJsonTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sample() = StoredRevision(
        id = 7L,
        buildId = "build-a",
        totalCny = 7900L,
        catalogueVersion = "2026.11",
        priceDate = "2026-11-04",
        userModified = true,
        items = listOf(
            StoredItem("cpu-demo-9600x", "CPU", 1, 1349),
            StoredItem("gpu-demo-300", "GPU", 1, 3500),
        ),
    )

    @Test
    fun `revision survives a json round trip`() {
        val decoded = json.decodeFromString<StoredRevision>(json.encodeToString(sample()))

        assertEquals(sample(), decoded)
    }

    @Test
    fun `item order is preserved`() {
        val encoded = json.encodeToString(sample())
        val decoded = json.decodeFromString<StoredRevision>(encoded)

        assertEquals(listOf("cpu-demo-9600x", "gpu-demo-300"), decoded.items.map { it.componentId })
    }

    @Test
    fun `persisted schema keys are frozen`() {
        val root = Json.parseToJsonElement(json.encodeToString(sample())).jsonObject
        val item = root.getValue("items").jsonArray.first().jsonObject

        assertEquals(
            setOf("id", "buildId", "totalCny", "catalogueVersion", "priceDate", "userModified", "items"),
            root.keys,
        )
        assertEquals(setOf("componentId", "category", "quantity", "unitPriceCny"), item.keys)
    }

    @Test
    fun `an unknown key does not break an old row`() {
        val legacy = """
            {"id":1,"buildId":"b","totalCny":0,"catalogueVersion":"2026.11",
             "priceDate":"2026-11-04","userModified":false,"items":[],"legacyField":true}
        """.trimIndent()

        val decoded = json.decodeFromString<StoredRevision>(legacy)

        assertEquals(1L, decoded.id)
        assertEquals(emptyList(), decoded.items)
    }

    @Test
    fun `a missing field fails instead of defaulting`() {
        val broken = """
            {"id":1,"buildId":"b","totalCny":0,"catalogueVersion":"2026.11","items":[]}
        """.trimIndent()

        // 载体字段没有默认值：缺字段必须炸，不允许静默补 false/空串。
        assertFailsWith<Exception> {
            json.decodeFromString<StoredRevision>(broken)
        }
    }
}
