/*
 * MainActivity.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo

import ai.metabind.finance.demo.ui.HomeScreen
import ai.metabind.finance.demo.ui.KeyEntryScreen
import ai.metabind.finance.demo.ui.theme.MetabindFinanceDemoTheme
import ai.metabind.finance.demo.ui.theme.palette
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetabindFinanceDemoTheme {
                FinanceApp()
            }
        }
    }
}

/**
 * Two surfaces and nothing between them: the key-entry screen until there is a key,
 * then the home screen for the rest of the session. There is no navigation stack —
 * every answer lands on the home screen or in the sheet over it.
 */
@Composable
private fun FinanceApp(viewModel: FinanceViewModel = hiltViewModel()) {
    when (val state = viewModel.state) {
        FinanceViewModel.State.Loading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.page),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        FinanceViewModel.State.NeedsKey -> KeyEntryScreen(onStart = viewModel::start)

        is FinanceViewModel.State.Ready -> HomeScreen(
            router = state.router,
            onResetApiKey = viewModel::resetApiKey,
        )
    }
}
