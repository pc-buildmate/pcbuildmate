package pcbuildmate.domain.revision

import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.Category

/**
 * Build-level guard：这份配置能不能有显示输出。
 *
 * 不是 15 条成对兼容规则：它看的是“整机有没有 GPU 或核显”，
 * 所以放在 domain:build，而不是塞进 RuleOutcome。
 *
 * 规则：
 *  有独显 -> true
 *  无独显 -> CPU.integratedGraphics == YES
 *  找不到 CPU / 找不到记录 -> false（fail-closed）
 */
object DisplayOutputGuard {

    fun hasDisplayOutput(revision: BuildRevision, catalogue: CataloguePort): Boolean {
        if (revision.items.any { it.category == Category.GPU }) return true

        val cpuItem = revision.items.firstOrNull { it.category == Category.CPU } ?: return false
        val cpu = catalogue.getById(cpuItem.componentId) ?: return false
        return cpu.attributes["integratedGraphics"]?.value == "YES"
    }
}
