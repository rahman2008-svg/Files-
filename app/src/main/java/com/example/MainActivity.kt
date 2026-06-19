package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.FilesViewModel
import com.example.ui.FilesViewModelFactory
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.CleanScreen
import com.example.ui.screens.ShareScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable true edge-to-edge rendering
        enableEdgeToEdge()
        
        setContent {
            val app = application as FilesApplication
            val viewModel: FilesViewModel = viewModel(
                factory = FilesViewModelFactory(app.repository)
            )
            
            MyApplicationTheme {
                MainAppLayout(viewModel)
            }
        }
    }
}

@Composable
fun MainAppLayout(viewModel: FilesViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val cleanupCards by viewModel.cleanupCards.collectAsState()
    val isCleaning by viewModel.isCleaning.collectAsState()

    // Offline Share parameters
    val shareState by viewModel.shareState.collectAsState()
    val isSendingRole by viewModel.isSendingRole.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                // CLEAN ANALYZER TAB
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Clean Storage Analysis",
                            tint = if (currentTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = { Text("Clean") },
                    modifier = Modifier.testTag("nav_tab_clean")
                )

                // FILE BROWSER TAB
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == 1) Icons.Default.Folder else Icons.Default.FolderOpen,
                            contentDescription = "Browse index & directory roots",
                            tint = if (currentTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = { Text("Browse") },
                    modifier = Modifier.testTag("nav_tab_browse")
                )

                // OFFLINE TRANSLATOR SHARE TAB
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Transform,
                            contentDescription = "Offline Nearby share",
                            tint = if (currentTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = { Text("Share") },
                    modifier = Modifier.testTag("nav_tab_share")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> CleanScreen(
                    storageInfo = storageInfo,
                    cleanupCards = cleanupCards,
                    isCleaning = isCleaning,
                    onCleanJunk = { size -> viewModel.cleanJunkFiles(size) },
                    onViewCardFiles = { viewModel.selectTab(1) } // Redirect category viewing
                )
                1 -> BrowseScreen(
                    viewModel = viewModel
                )
                2 -> ShareScreen(
                    shareState = shareState,
                    isSendingRole = isSendingRole,
                    onStartShare = { role -> viewModel.initDiscovering(role) },
                    onCancelShare = { viewModel.cancelShareMode() }
                )
            }
        }
    }
}
