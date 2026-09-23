package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.MainAppScreen
import com.example.ui.MangaViewModel
import com.example.ui.theme.IrumaMangaTheme

class MainActivity : ComponentActivity() {

    private val mangaViewModel: MangaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IrumaMangaTheme {
                MainAppScreen(
                    viewModel = mangaViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
