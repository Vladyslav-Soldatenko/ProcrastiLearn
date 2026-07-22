package com.procrastilearn.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScreenTest {
    private val allScreens =
        listOf(
            Screen.Apps,
            Screen.AddWord,
            Screen.WordList,
            Screen.Dojo,
            Screen.Settings,
        )

    @Test
    fun `each screen exposes its expected route`() {
        assertThat(Screen.Apps.route).isEqualTo("apps")
        assertThat(Screen.AddWord.route).isEqualTo("add_word")
        assertThat(Screen.WordList.route).isEqualTo("word_list")
        assertThat(Screen.Dojo.route).isEqualTo("dojo")
        assertThat(Screen.Settings.route).isEqualTo("settings")
    }

    @Test
    fun `all routes are unique`() {
        val routes = allScreens.map { it.route }

        assertThat(routes.toSet()).hasSize(routes.size)
    }

    @Test
    fun `no route is blank`() {
        allScreens.forEach { screen ->
            assertThat(screen.route).isNotEmpty()
        }
    }
}
