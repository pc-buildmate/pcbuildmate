package pcbuildmate.oracle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 参考模型自带 JSON 读取器的直接单测。
 *
 * 之前它只被交叉校验间接用到——"能解析完 60 条 fixture"不等于"解析错了会报错"。
 * 这里把转义、Unicode、数字形态和非法输入各自钉一条。
 */
class MiniJsonTest {

    @Test
    fun `parses the shapes used by rules and fixtures`() {
        val text = """
            {
              "rulesVersion": "v1",
              "rules": [{"ruleId": "CPU_SOCKET_MATCH", "severity": "BLOCKER", "formula": null}],
              "numeric": 337,
              "decimal": 1.2,
              "flag": true
            }
        """.trimIndent()

        val root = MiniJson.parse(text).asObject()
        assertEquals("v1", root["rulesVersion"].asText())
        assertEquals(337.0, root["numeric"].asNumber())
        assertEquals(1.2, root["decimal"].asNumber())
        assertEquals(true, root["flag"])
        assertNull(root["missing"])
        val rule = root["rules"].asArray().single().asObject()
        assertEquals("CPU_SOCKET_MATCH", rule["ruleId"].asText())
        assertNull(rule["formula"])
    }

    @Test
    fun `decodes escapes and unicode`() {
        val root = MiniJson.parse("""{"s":"a\"b\\c\/d\ne\tf中"}""").asObject()
        assertEquals("a\"b\\c/d\ne\tf中", root["s"].asText())
    }

    @Test
    fun `handles negative and exponent numbers`() {
        val root = MiniJson.parse("""{"a":-1,"b":-0.5,"c":1e3,"d":2.5E-2}""").asObject()
        assertEquals(-1.0, root["a"].asNumber())
        assertEquals(-0.5, root["b"].asNumber())
        assertEquals(1000.0, root["c"].asNumber())
        assertEquals(0.025, root["d"].asNumber())
    }

    @Test
    fun `parses empty containers`() {
        val root = MiniJson.parse("""{"o":{},"a":[]}""").asObject()
        assertTrue(root["o"].asObject().isEmpty())
        assertTrue(root["a"].asArray().isEmpty())
    }

    @Test
    fun `rejects trailing content`() {
        val e = assertFailsWith<IllegalArgumentException> { MiniJson.parse("""{"a":1} extra""") }
        assertTrue(e.message!!.contains("多余内容"), e.message)
    }

    @Test
    fun `rejects truncated input instead of guessing`() {
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("""{"a":""") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("""{"a":1""") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("""[1,""") }
    }
}
