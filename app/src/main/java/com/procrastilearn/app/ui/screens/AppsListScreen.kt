package com.procrastilearn.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.procrastilearn.app.ui.AppsViewModel
import com.procrastilearn.app.ui.views.AppsList

@Composable
fun AppsListScreen() {
    val vm: AppsViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    when {
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Error: ${state.error}")
            }
        }
        else -> {
            AppsList(
                apps = state.apps,
                selectedKeys = state.selected,
                onToggle = vm::toggle,
            )
        }
    }
}
