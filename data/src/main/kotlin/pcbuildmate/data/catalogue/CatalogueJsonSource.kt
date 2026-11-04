package pcbuildmate.data.catalogue

/** 把“读 JSON 文本”从解析与端口实现里拆出来，JVM 测试不需要 Android。 */
fun interface CatalogueJsonSource {
    fun read(): String
}
