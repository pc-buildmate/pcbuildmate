package pcbuildmate.oracle

/**
 * 参考模型自带的最小 JSON 读取器。
 *
 * 刻意不使用 `kotlinx.serialization`：参考模型的价值在于"另一条独立实现"，
 * 如果连解析器都和生产共用，两边的偏误会一起偏。这里只支持 JSON 全集里
 * 本项目用到的部分（对象、数组、字符串、数字、布尔、null），不做 schema 校验。
 */
object MiniJson {

    fun parse(text: String): Any? = Parser(text).parseTop()

    private class Parser(private val s: String) {
        private var i = 0

        fun parseTop(): Any? {
            ws()
            val v = value()
            ws()
            require(i == s.length) { "JSON 末尾有多余内容，位置 $i" }
            return v
        }

        private fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char {
            require(i < s.length) { "JSON 意外结束，位置 $i" }
            return s[i]
        }

        private fun value(): Any? {
            ws()
            return when (val c = peek()) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { literal("true"); true }
                'f' -> { literal("false"); false }
                'n' -> { literal("null"); null }
                else -> {
                    require(c == '-' || c.isDigit()) { "非法字符 '$c'，位置 $i" }
                    num()
                }
            }
        }

        private fun literal(word: String) {
            require(s.startsWith(word, i)) { "期望 $word，位置 $i" }
            i += word.length
        }

        private fun obj(): Map<String, Any?> {
            i++ // {
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (peek() == '}') { i++; return out }
            while (true) {
                ws()
                val k = str()
                ws()
                require(peek() == ':') { "对象缺少 ':'，位置 $i" }
                i++
                out[k] = value()
                ws()
                when (val c = peek()) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> throw IllegalArgumentException("对象分隔符异常 '$c'，位置 $i")
                }
            }
        }

        private fun arr(): List<Any?> {
            i++ // [
            val out = ArrayList<Any?>()
            ws()
            if (peek() == ']') { i++; return out }
            while (true) {
                out.add(value())
                ws()
                when (val c = peek()) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> throw IllegalArgumentException("数组分隔符异常 '$c'，位置 $i")
                }
            }
        }

        private fun str(): String {
            require(peek() == '"') { "期望字符串，位置 $i" }
            i++
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "字符串未闭合" }
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(i < s.length) { "转义未闭合" }
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("未知转义 \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(start, i).toDouble()
        }
    }
}

/** 把 `Any?` 读成对象/数组/字符串的标准小工具，避免调用点到处强转。 */
@Suppress("UNCHECKED_CAST")
internal fun Any?.asObject(): Map<String, Any?> = this as? Map<String, Any?>
    ?: throw IllegalArgumentException("期望 JSON 对象，实际是 ${this?.javaClass?.simpleName}")

@Suppress("UNCHECKED_CAST")
internal fun Any?.asArray(): List<Any?> = this as? List<Any?>
    ?: throw IllegalArgumentException("期望 JSON 数组，实际是 ${this?.javaClass?.simpleName}")

internal fun Any?.asText(): String = this as? String
    ?: throw IllegalArgumentException("期望字符串，实际是 ${this?.javaClass?.simpleName}")

internal fun Any?.asNumber(): Double = this as? Double
    ?: throw IllegalArgumentException("期望数字，实际是 ${this?.javaClass?.simpleName}")
