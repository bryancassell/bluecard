package io.github.bryancassell.bluecard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.github.bryancassell.bluecard.ui.navigation.BlueCardNavDisplay
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueCardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BlueCardNavDisplay(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
