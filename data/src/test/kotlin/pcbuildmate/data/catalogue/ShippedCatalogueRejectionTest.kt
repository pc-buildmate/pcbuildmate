package pcbuildmate.data.catalogue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 上架资产的拒绝路径（DATA-04 补强）。
 *
 * `FieldPathRegistry` 里有四条校验分支此前没有任何测试踩到：`numeric` 与 `value` 不一致、
 * `state=EMPTY` 却带值、版本号次位 > 9、目录头日期非 ISO。这里逐条用真实上架资产
 * （`catalogue/catalogue-app.json`）改一个字符触发，确认解析器 fail-fast，
 * 而不是把坏数据读进内存。
 *
 * 只做字符串层面的最小改动，不改测试期望值、不改生产代码。
 */
class ShippedCatalogueRejectionTest {

    private fun shippedText(): String =
        checkNotNull(javaClass.classLoader?.getResource("catalogue/catalogue-app.json")) {
            "测试资源缺失：catalogue-app.json（:data 的 test sourceSet 指向仓库 assets/）"
        }.readText()

    private fun parse(mutated: String) = CatalogueParser.parse(mutated)

    @Test
    fun `unmutated asset still parses so the counter-examples mean something`() {
        assertEquals(30, parse(shippedText()).records.size)
    }

    @Test
    fun `rejects a numeric that disagrees with its raw value`() {
        // memorySlots 之类：value="4" 而 numeric 被改成 5
        val broken = shippedText().replaceFirst("\"numeric\": 4", "\"numeric\": 5")

        assertFailsWith<IllegalArgumentException> { parse(broken) }
    }

    @Test
    fun `rejects an empty field that still carries a value`() {
        // 全表只有 mb-demo-b650.biosGeneration 的 value 为 null
        val broken = shippedText().replaceFirst("\"value\": null", "\"value\": \"1.2\"")

        assertFailsWith<IllegalArgumentException> { parse(broken) }
    }

    @Test
    fun `rejects an empty field that still carries a numeric`() {
        // 与 apps 的 EMPTY 规则一致：EMPTY 只能表示「没有证据」，不许留一个能用的 numeric，
        // 否则 3D 的 num()（numeric ?: value，不看 state）会把它当已知尺寸用。
        // 资产在本地是 CRLF、在 CI 是 LF：先归一换行，否则测试只在其中一种环境里成立。
        val text = shippedText().replace("\r\n", "\n")
        val original = "          \"value\": null,\n          \"state\": \"EMPTY\",\n          \"sources\": [],\n          \"conflict\": false,\n          \"numeric\": null"
        val broken = original.replace("\"numeric\": null", "\"numeric\": 1.0")
        assertTrue(text.contains(original), "上架资产里找不到唯一的 EMPTY 字段块，测试需要更新")
        assertFailsWith<IllegalArgumentException> { parse(text.replaceFirst(original, broken)) }
    }

    @Test
    fun `rejects a secondary version above nine`() {
        // 版本号要求「主.次」且次版本号 <= 9（docs/03 §3.3）
        val broken = shippedText().replaceFirst("\"value\": \"1.0\"", "\"value\": \"1.10\"")

        assertFailsWith<IllegalArgumentException> { parse(broken) }
    }

    @Test
    fun `rejects a price date that is not iso`() {
        val broken = shippedText().replaceFirst("\"priceDate\": \"2026-11-04\"", "\"priceDate\": \"2026-11-4\"")

        assertFailsWith<IllegalArgumentException> { parse(broken) }
    }
}
