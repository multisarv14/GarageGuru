package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.database.GarageDatabase
import com.example.data.repository.GarageRepository
import com.example.ui.GarageViewModel
import com.example.ui.GarageViewModelFactory
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.data.api.GarageLogoStore
import com.example.data.api.GarageSession
import com.example.data.api.GarageSettings

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // 1. Restore the workshop identity so Firestore writes are tagged from the first frame
    GarageSession.init(applicationContext)
    GarageSettings.init(applicationContext)
    GarageLogoStore.init(applicationContext)

    // 2. Initialize Room Database & Repositories
    val database = GarageDatabase.getDatabase(applicationContext)
    val repository = GarageRepository(database.garageDao)
    val viewModel: GarageViewModel by viewModels {
        GarageViewModelFactory(repository)
    }

    setContent {
      MyApplicationTheme {
        MainAppScreen(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}
