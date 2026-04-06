package com.cris.sumptus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cris.sumptus.ui.SumptusApp
import com.cris.sumptus.ui.theme.SumptusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SumptusTheme {
                SumptusApp()
            }
        }
    }
}
