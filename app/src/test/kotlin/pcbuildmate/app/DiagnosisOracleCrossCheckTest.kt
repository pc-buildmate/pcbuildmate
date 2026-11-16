package pcbuildmate.app

import org.junit.Assert.assertTrue
import org.junit.Test
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.DiagnosisEvent
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.TransitionResult
import pcbuildmate.contracts.UseCase
import pcbuildmate.domain.state.DiagnosisContext
import pcbuildmate.domain.state.DiagnosisMachine
import pcbuildmate.oracle.DiagnosisOracle
import pcbuildmate.oracle.OracleDiagnosisInput

/**
 * C.1 诊断状态机：生产实现 vs 参考模型（docs/04 §4.3）。
 *
 * 跑的是 4 个状态 × 4 类事件 × 3 种 guard 上下文的完整组合，
 * 比较下一状态、Effects 顺序、拒绝原因三件事。
 */
class DiagnosisOracleCrossCheckTest {

    private val revision7 = revision(7)
    private val revision8 = revision(8)
    private val diagnosis7 = Diagnosis(
        revisionId = 7,
        outcomes = emptyList(),
        overall = OverallStatus.PASSED,
        riskLevel = RiskLevel.LOW,
        score = null,
        computedAt = "2026-11-16T00:00:00Z",
    )
    private val build = Build(
        buildId = "b1", budgetCny = 8000, useCase = UseCase.GAMING, preferences = Preferences(),
        currentRevisionId = 7, userModified = false, createdAt = "", updatedAt = "",
    )

    @Test
    fun `diagnosis machine agrees with the oracle on the 4x4 matrix`() {
        val states = listOf(
            DiagnosisState.Idle,
            DiagnosisState.Running(7),
            DiagnosisState.Running(9),
            DiagnosisState.Done(7),
            DiagnosisState.Failed(7, "boom"),
        )
        val events = listOf(
            DiagnosisEvent.StartCheck,
            DiagnosisEvent.CheckCompleted(7, diagnosis7),
            DiagnosisEvent.CheckCompleted(9, diagnosis7),
            DiagnosisEvent.CheckFailed(7, "boom"),
            DiagnosisEvent.CheckFailed(9, "boom"),
            DiagnosisEvent.BuildEdited(revision8),
        )
        // guard 上下文：正常 / 没有 build / 没有 revision
        val contexts = listOf(
            Triple(DiagnosisContext(build, revision7, diagnosis7), OracleDiagnosisInput(hasBuild = true, revisionId = 7), "full"),
            Triple(DiagnosisContext(null, revision7, diagnosis7), OracleDiagnosisInput(hasBuild = false, revisionId = 7), "no-build"),
            Triple(DiagnosisContext(build, null, null), OracleDiagnosisInput(hasBuild = true, revisionId = null), "no-revision"),
        )

        val failures = mutableListOf<String>()
        for ((prodCtx, oracleInput, ctxName) in contexts) {
            for (state in states) {
                for (event in events) {
                    val prod = DiagnosisMachine.transition(state, event, prodCtx)
                    val oracle = DiagnosisOracle.transition(state, event, oracleInput)
                    if (!same(prod, oracle)) {
                        failures += "${state::class.simpleName}(${describeState(state)}) x ${eventName(event)} [$ctxName]\n" +
                            "      生产：${describe(prod)}\n      oracle：${describe(oracle)}"
                    }
                }
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun same(a: TransitionResult<DiagnosisState>, b: TransitionResult<DiagnosisState>): Boolean =
        a.state == b.state && a.effects == b.effects && a.rejection?.reason == b.rejection?.reason

    private fun describe(r: TransitionResult<DiagnosisState>): String =
        "${describeState(r.state)} effects=${r.effects} rejection=${r.rejection?.reason}"

    private fun describeState(s: DiagnosisState): String = when (s) {
        is DiagnosisState.Idle -> "Idle"
        is DiagnosisState.Running -> "Running(${s.revisionId})"
        is DiagnosisState.Done -> "Done(${s.revisionId})"
        is DiagnosisState.Failed -> "Failed(${s.revisionId})"
    }

    private fun eventName(e: DiagnosisEvent): String = when (e) {
        is DiagnosisEvent.StartCheck -> "StartCheck"
        is DiagnosisEvent.CheckCompleted -> "CheckCompleted(${e.revisionId})"
        is DiagnosisEvent.CheckFailed -> "CheckFailed(${e.revisionId})"
        is DiagnosisEvent.BuildEdited -> "BuildEdited(${e.revision.id})"
    }

    private fun revision(id: Long) = BuildRevision(
        id = id, buildId = "b1", items = emptyList(), totalCny = 0,
        catalogueVersion = "v1", priceDate = "2026-11-01", userModified = false,
    )
}
