package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MealRepository
import com.example.ui.MealRaterScreen
import com.example.ui.MealViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current.applicationContext
                    val repository = remember(context) { MealRepository(context) }
                    val mealViewModel: MealViewModel = viewModel(
                        factory = MealViewModel.provideFactory(repository)
                    )
                    MealRaterScreen(viewModel = mealViewModel)
                }
            }
        }
    }
}
