package pcbuildmate.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import pcbuildmate.contracts.BudgetRecommenderPort
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CompatibilityEvaluator
import pcbuildmate.contracts.ReplacementAdvisorPort
import pcbuildmate.contracts.WorkspaceIntent
import pcbuildmate.data.catalogue.assetCatalogue
import pcbuildmate.data.rules.RuleParser
import pcbuildmate.domain.compare.DefaultBuildComparator
import pcbuildmate.domain.recommend.DefaultBudgetRecommender
import pcbuildmate.domain.replacement.DefaultReplacementAdvisor
import pcbuildmate.domain.rules.DefaultRuleEngine
import pcbuildmate.feature.workspace.WorkspaceViewModel
import pcbuildmate.ui.CompareScreen
import pcbuildmate.ui.DiagnosticsScreen
import pcbuildmate.ui.EntryScreen
import pcbuildmate.ui.WorkspaceScreen
import pcbuildmate.ui.theme.PcBuildmateTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        val catalogue = assetCatalogue(this)
        val rules = runCatching {
            RuleParser.parse(
                assets.open("rules/rules-v1.json").bufferedReader().use { it.readText() },
            ).rules
        }.getOrElse { emptyList() }
        val evaluator = DefaultRuleEngine(rules, catalogue)
        val advisor = DefaultReplacementAdvisor(catalogue, evaluator)
        val recommender = DefaultBudgetRecommender(catalogue, evaluator)

        setContent {
            PcBuildmateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PcBuildmateRoot(catalogue, evaluator, advisor, recommender)
                }
            }
        }
    }
}

@Composable
private fun PcBuildmateRoot(
    catalogue: CataloguePort,
    evaluator: CompatibilityEvaluator,
    advisor: ReplacementAdvisorPort,
    recommender: BudgetRecommenderPort,
) {
    val viewModel: WorkspaceViewModel = viewModel(
        factory = WorkspaceViewModelFactory(catalogue, evaluator, advisor, recommender),
    )
    val state by viewModel.uiState.collectAsState()
    val labels = remember(catalogue) {
        catalogue.search("", null).associate { it.componentId to it.model }
    }

    var screen by rememberSaveable { mutableStateOf(Screen.Entry.name) }
    var entryError by rememberSaveable { mutableStateOf<String?>(null) }
    var weights by remember { mutableStateOf<Map<pcbuildmate.contracts.Category, Double>>(emptyMap()) }
    val current = runCatching { Screen.valueOf(screen) }.getOrDefault(Screen.Entry)

    when (current) {
        Screen.Entry -> EntryScreen(
            onStartDemo = {
                entryError = null
                weights = emptyMap()
                viewModel.startDemo()
                screen = Screen.Workspace.name
            },
            onRecommend = { budget, useCase ->
                val result = viewModel.recommend(budget, useCase)
                if (result.feasible) {
                    entryError = null
                    weights = result.weightsVisible
                    screen = Screen.Workspace.name
                } else {
                    entryError = "当前预算不可行" +
                        if (result.shortfallCny > 0) "，还差约 ¥${result.shortfallCny}" else ""
                }
            },
            error = entryError,
        )

        Screen.Workspace -> WorkspaceScreen(
            state = state,
            labels = labels,
            weights = weights,
            onIntent = viewModel::onIntent,
            onCheck = {
                viewModel.onIntent(WorkspaceIntent.StartCheck)
                screen = Screen.Diagnostics.name
            },
            onCompare = { screen = Screen.Compare.name },
            onBack = {
                entryError = null
                screen = Screen.Entry.name
            },
        )

        Screen.Diagnostics -> DiagnosticsScreen(
            state = state,
            labels = labels,
            onIntent = viewModel::onIntent,
            onBack = { screen = Screen.Workspace.name },
        )

        Screen.Compare -> {
            val snapshot = viewModel.savedSnapshot()
            val comparison = DefaultBuildComparator(
                builds = snapshot.builds,
                revisions = snapshot.revisions,
                diagnoses = snapshot.diagnoses,
            ).compare(snapshot.builds.map { it.buildId })
            CompareScreen(
                result = comparison,
                labels = labels,
                onBack = { screen = Screen.Workspace.name },
            )
        }
    }
}

private enum class Screen { Entry, Workspace, Diagnostics, Compare }

private class WorkspaceViewModelFactory(
    private val catalogue: CataloguePort,
    private val evaluator: CompatibilityEvaluator,
    private val advisor: ReplacementAdvisorPort,
    private val recommender: BudgetRecommenderPort,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            "Unknown ViewModel: ${modelClass.name}"
        }
        return WorkspaceViewModel(catalogue, evaluator, advisor, recommender) as T
    }
}
