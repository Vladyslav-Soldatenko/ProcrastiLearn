package com.procrastilearn.app.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.procrastilearn.app.R

private data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    @param:StringRes val labelResId: Int,
)

private val bottomNavItems =
    listOf(
        BottomNavItem(
            screen = Screen.Apps,
            icon = Icons.Default.AppRegistration,
            labelResId = R.string.nav_apps,
        ),
        BottomNavItem(
            screen = Screen.AddWord,
            icon = Icons.Default.Add,
            labelResId = R.string.nav_add_word,
        ),
        BottomNavItem(
            screen = Screen.Dojo,
            icon = Icons.AutoMirrored.Filled.MenuBook,
            labelResId = R.string.nav_dojo,
        ),
        BottomNavItem(
            screen = Screen.Settings,
            icon = Icons.Default.Settings,
            labelResId = R.string.nav_settings,
        ),
    )

private fun navigateToItem(
    navController: NavController,
    item: BottomNavItem,
    currentRoute: String?,
) {
    val poppedToDestination =
        item.screen == Screen.AddWord &&
            currentRoute == Screen.WordList.route &&
            navController.popBackStack(Screen.AddWord.route, inclusive = false)
    if (!poppedToDestination) {
        navController.navigate(item.screen.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(modifier = modifier) {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = { navigateToItem(navController, item, currentRoute) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(id = item.labelResId),
                    )
                },
                label = {
                    Text(text = stringResource(id = item.labelResId))
                },
            )
        }
    }
}

@Composable
fun NavigationRailBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationRail(modifier = modifier) {
        Spacer(modifier = Modifier.weight(1f))
        bottomNavItems.forEach { item ->
            NavigationRailItem(
                selected = currentRoute == item.screen.route,
                onClick = { navigateToItem(navController, item, currentRoute) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(id = item.labelResId),
                    )
                },
                label = {
                    Text(text = stringResource(id = item.labelResId))
                },
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
