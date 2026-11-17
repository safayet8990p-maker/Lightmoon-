package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.LightmoonRepository
import com.example.ui.develop.DevelopScreen
import com.example.ui.develop.DevelopViewModel
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.theme.LightmoonTheme

object LightmoonRoutes {
    const val LIBRARY = "library"
    const val DEVELOP = "develop/{photoId}"
    fun develop(photoId: String) = "develop/$photoId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            LightmoonTheme {
                val context = LocalContext.current.applicationContext
                val repository = remember { LightmoonRepository(context) }
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = LightmoonRoutes.LIBRARY
                ) {
                    composable(LightmoonRoutes.LIBRARY) {
                        val libraryVm: LibraryViewModel = viewModel(
                            factory = LibraryViewModel.Factory(repository)
                        )
                        LibraryScreen(
                            viewModel = libraryVm,
                            onOpenPhoto = { photoId ->
                                navController.navigate(LightmoonRoutes.develop(photoId))
                            }
                        )
                    }

                    composable(
                        route = LightmoonRoutes.DEVELOP,
                        arguments = listOf(navArgument("photoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val photoId = backStackEntry.arguments?.getString("photoId") ?: return@composable
                        val developVm: DevelopViewModel = viewModel(
                            key = "develop_$photoId",
                            factory = DevelopViewModel.Factory(repository, photoId)
                        )
                        DevelopScreen(
                            viewModel = developVm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
