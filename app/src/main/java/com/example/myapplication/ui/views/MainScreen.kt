package com.example.myapplication.ui.views

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.navigation.BottomNavigationBar
import com.example.myapplication.navigation.Screen
import com.example.myapplication.ui.screens.AddWordScreen
import com.example.myapplication.ui.screens.AppsListScreen
import com.example.myapplication.ui.screens.WordListScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Apps.route,
            modifier = Modifier.padding(paddingValues),
        ) {
            composable(Screen.Apps.route) {
                AppsListScreen()
            }
            composable(Screen.AddWord.route) {
                AddWordScreen(onNavigateToList = { navController.navigate(Screen.WordList.route) })
            }
            composable(Screen.WordList.route) {
                WordListScreen()
            }
        }
    }
}
