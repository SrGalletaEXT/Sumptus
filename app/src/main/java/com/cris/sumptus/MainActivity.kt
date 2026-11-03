package com.cris.sumptus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cris.sumptus.i18n.LocalStrings
import com.cris.sumptus.i18n.Strings
import com.cris.sumptus.ui.SumptusApp
import com.cris.sumptus.ui.theme.SumptusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalStrings provides Strings(uiState.language)) {
                SumptusTheme(darkTheme = uiState.isDarkTheme) {
                    SumptusApp(viewModel)
                }
            }
        }
    }
}
