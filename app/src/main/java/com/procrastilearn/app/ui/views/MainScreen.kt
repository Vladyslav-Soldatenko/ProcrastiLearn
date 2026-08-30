package com.procrastilearn.app.ui.views

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.procrastilearn.app.navigation.BottomNavigationBar
import com.procrastilearn.app.navigation.NavigationRailBar
import com.procrastilearn.app.navigation.Screen
import com.procrastilearn.app.ui.dojo.DojoScreen
import com.procrastilearn.app.ui.screens.AddWordScreen
import com.procrastilearn.app.ui.screens.AppsListScreen
import com.procrastilearn.app.ui.screens.WordListScreen
import com.procrastilearn.app.ui.screens.settings.SettingsScreen
import com.procrastilearn.app.utils.isLandscapeOrientation

@Suppress("DEPRECATION") // replacement androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel is not yet published
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()

    val processTextEvent by mainViewModel.processTextEvents.collectAsStateWithLifecycle()
    LaunchedEffect(processTextEvent) {
        if (processTextEvent != null) {
            navController.navigate(Screen.AddWord.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val isLandscape = isLandscapeOrientation()
    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRailBar(navController = navController)
            Scaffold(modifier = Modifier.weight(1f)) { paddingValues ->
                MainNavHost(
                    navController = navController,
                    modifier =
                        Modifier
                            .padding(paddingValues)
                            .consumeWindowInsets(paddingValues),
                )
            }
        }
    } else {
        Scaffold(
            modifier = modifier,
            bottomBar = {
                BottomNavigationBar(navController = navController)
            },
        ) { paddingValues ->
            MainNavHost(
                navController = navController,
                modifier =
                    Modifier
                        .padding(paddingValues)
                        .consumeWindowInsets(paddingValues),
            )
        }
    }
}

@Composable
private fun MainNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.AddWord.route,
        modifier = modifier,
    ) {
        composable(Screen.Apps.route) {
            AppsListScreen()
        }
        composable(Screen.AddWord.route) {
            AddWordScreen(onNavigateToList = { navController.navigate(Screen.WordList.route) })
        }
        composable(Screen.WordList.route) {
            WordListScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Dojo.route) {
            DojoScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
