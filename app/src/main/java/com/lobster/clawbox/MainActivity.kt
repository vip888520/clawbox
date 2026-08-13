package com.lobster.clawbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lobster.clawbox.ui.AppRoot
import com.lobster.clawbox.ui.theme.ClawBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClawBoxTheme {
                AppRoot()
            }
        }
    }
}
