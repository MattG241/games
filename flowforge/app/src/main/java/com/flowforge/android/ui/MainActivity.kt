package com.flowforge.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flowforge.android.FlowForgeApp
import com.flowforge.android.ui.theme.FlowForgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // flowforge://run/<scenario id or name> starts a scenario from anywhere on the device.
        val deepLinkTarget = intent?.data
            ?.takeIf { it.scheme == "flowforge" && it.host == "run" }
            ?.lastPathSegment

        setContent {
            FlowForgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LaunchedEffect(deepLinkTarget) {
                        val needle = deepLinkTarget ?: return@LaunchedEffect
                        FlowForgeApp.instance.scenarios.findByNameOrId(needle)?.let {
                            viewModel.runNow(it)
                        }
                    }
                    FlowForgeNav(viewModel)
                }
            }
        }
    }
}

@Composable
private fun FlowForgeNav(viewModel: AppViewModel) {
    val navController = rememberNavController()
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "scenarios") {
            composable("scenarios") { ScenarioListScreen(viewModel, navController) }
            composable("editor/{id}") { entry ->
                EditorScreen(viewModel, navController, entry.arguments?.getString("id").orEmpty())
            }
            composable("history") { HistoryScreen(viewModel, navController, null) }
            composable("history/{scenarioId}") { entry ->
                HistoryScreen(viewModel, navController, entry.arguments?.getString("scenarioId"))
            }
            composable("run/{id}") { entry ->
                RunDetailScreen(viewModel, navController, entry.arguments?.getString("id").orEmpty())
            }
            composable("settings") { SettingsScreen(viewModel, navController) }
            composable("data") { DataStoreScreen(viewModel, navController) }
        }
    }
}
