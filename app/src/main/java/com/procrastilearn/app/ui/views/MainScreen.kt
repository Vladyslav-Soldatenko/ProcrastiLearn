package com.procrastilearn.app.ui.views

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.procrastilearn.app.navigation.BottomNavigationBar
import com.procrastilearn.app.navigation.Screen
import com.procrastilearn.app.ui.dojo.DojoScreen
import com.procrastilearn.app.ui.screens.AddWordScreen
import com.procrastilearn.app.ui.screens.AppsListScreen
import com.procrastilearn.app.ui.screens.WordListScreen
import com.procrastilearn.app.ui.screens.settings.SettingsScreen

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
            startDestination = Screen.AddWord.route,
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
            composable(Screen.Dojo.route) {
                DojoScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
