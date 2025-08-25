package com.procrastilearn.app.navigation

sealed class Screen(
    val route: String,
) {
    object Apps : Screen("apps")

    object AddWord : Screen("add_word")

    object WordList : Screen("word_list")

    data object Settings : Screen("settings")
}
