package mx.dev1.naturequest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import mx.dev1.naturequest.ui.navigation.NatureQuestNavHost
import mx.dev1.naturequest.ui.theme.NatureQuestTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NatureQuestTheme {
                NatureQuestNavHost()
            }
        }
    }
}
