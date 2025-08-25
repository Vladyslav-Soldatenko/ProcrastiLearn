package com.example.myapplication.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.myapplication.R

private data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
)

private val bottomNavItems =
    listOf(
        BottomNavItem(
            screen = Screen.Apps,
            icon = Icons.Default.Edit,
            label = "nav_apps",
        ),
        BottomNavItem(
            screen = Screen.AddWord,
            icon = Icons.Default.Add,
            label = "nav_add_word",
        ),
        BottomNavItem(
            screen = Screen.Settings,
            icon = Icons.Default.Settings,
            label = "nav_settings",
        ),
    )

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription =
                            stringResource(
                                id =
                                    when (item.label) {
                                        "nav_apps" -> R.string.nav_apps
                                        "nav_add_word" -> R.string.nav_add_word
                                        "nav_settings" -> R.string.nav_settings
                                        else -> R.string.nav_apps
                                    },
                            ),
                    )
                },
                label = {
                    Text(
                        text =
                            stringResource(
                                id =
                                    when (item.label) {
                                        "nav_apps" -> R.string.nav_apps
                                        "nav_add_word" -> R.string.nav_add_word
                                        "nav_settings" -> R.string.nav_settings
                                        else -> R.string.nav_apps
                                    },
                            ),
                    )
                },
            )
        }
    }
}
