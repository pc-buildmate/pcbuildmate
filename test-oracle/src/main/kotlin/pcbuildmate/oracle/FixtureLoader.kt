package pcbuildmate.oracle

import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldSource
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.VerificationState

/**
 * fixture 的独立读取器：只负责把 JSON 变成 contracts 对象，不做任何断言。
 *
 * 生产侧读 fixture 的代码在 `domain/rules/src/test/.../FixtureTest.kt`，
 * 两边各读各的——如果读法本身有偏，交叉校验会因为输入不同而暴露出来。
 */
data class OracleFixture(
    val id: String,
    val ruleId: String,
    val case: String,
    val revision: BuildRevision,
    val catalogue: List<CatalogueRecord>,
    val expected: Map<String, String>,
)

object FixtureLoader {

    fun load(json: String): List<OracleFixture> {
        val root = MiniJson.parse(json).asObject()
        // 与生产侧一致：没有 expected 的 fixture（第 8 条 suggestion 场景）不参与单规则校验。
        return root["fixtures"].asArray()
            .map { it.asObject() }
            .filter { it.containsKey("expected") }
            .map { one(it) }
    }

    private fun one(f: Map<String, Any?>): OracleFixture {
        val items = f["items"].asArray().map { el ->
            val i = el.asObject()
            BuildItem(
                componentId = i["componentId"].asText(),
                category = Category.valueOf(i["category"].asText()),
                quantity = i["quantity"].asNumber().toInt(),
                unitPriceCny = i["unitPriceCny"].asNumber().toLong(),
            )
        }
        val catalogue = f["catalogue"].asArray().map { el -> record(el.asObject()) }
        val revision = BuildRevision(
            id = f["revisionId"].asNumber().toLong(),
            buildId = f["buildId"].asText(),
            items = items,
            totalCny = items.sumOf { it.quantity.toLong() * it.unitPriceCny },
            catalogueVersion = "",
            priceDate = "",
            userModified = false,
        )
        // expected 形如 { "RULE_ID": { "outcome": "PASSED", "weakest": "VERIFIED" } }
        val expected = (f["expected"] ?: emptyMap<String, Any?>()).asObject().mapValues { (_, v) ->
            val e = v.asObject()
            "${e["outcome"].asText()}/${(e["weakest"] as? String) ?: "-"}"
        }
        return OracleFixture(
            id = f["fixtureId"].asText(),
            ruleId = (f["ruleId"] as? String) ?: "",
            case = (f["case"] as? String) ?: (f["purpose"] as? String) ?: "",
            revision = revision,
            catalogue = catalogue,
            expected = expected,
        )
    }

    private fun record(r: Map<String, Any?>): CatalogueRecord {
        val attrs = r["attributes"].asObject().mapValues { (_, v) -> fieldValue(v.asObject()) }
        return CatalogueRecord(
            componentId = r["componentId"].asText(),
            category = Category.valueOf(r["category"].asText()),
            // fixture 里这些展示字段可能是 null，判定不依赖它们。
            manufacturer = (r["manufacturer"] as? String) ?: "",
            model = (r["model"] as? String) ?: "",
            partNumber = (r["partNumber"] as? String) ?: "",
            attributes = attrs,
            priceCny = r["priceCny"].asNumber().toLong(),
            priceDate = "",
            catalogueVersion = "",
            isDemo = true,
        )
    }

    private fun fieldValue(v: Map<String, Any?>): FieldValue {
        val state = VerificationState.valueOf(v["state"].asText())
        val sources = (v["sources"] ?: emptyList<Any?>()).asArray().map { el ->
            val s = el.asObject()
            FieldSource(
                source = s["source"].asText(),
                rawValue = s["rawValue"].asText(),
                state = VerificationState.valueOf(s["state"].asText()),
            )
        }
        return FieldValue(
            value = v["value"] as? String,
            state = state,
            sources = sources,
            conflict = (v["conflict"] as? Boolean) ?: false,
            numeric = v["numeric"] as? Double,
        )
    }
}
