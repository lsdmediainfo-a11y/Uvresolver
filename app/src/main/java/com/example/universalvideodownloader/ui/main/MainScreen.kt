package com.example.universalvideodownloader.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.universalvideodownloader.ui.browser.BrowserScreen
import com.example.universalvideodownloader.ui.downloads.DownloadsScreen
import com.example.universalvideodownloader.ui.home.HomeScreen
import com.example.universalvideodownloader.ui.player.PlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Ana Sayfa", Icons.Default.Home)
    object Browser : Screen("browser", "Tarayıcı", Icons.Default.Search)
    object Downloads : Screen("downloads", "İndirilenler", Icons.Default.List)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Browser, Screen.Downloads)
    
    var lastBrowserUrl by remember { mutableStateOf("https://google.com") }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route?.startsWith("player") != true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    for (screen in items) {
                        val isSelected = currentDestination?.hierarchy?.any { 
                            it.route?.startsWith(screen.route) == true 
                        } == true
                        
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToBrowser = { url ->
                        lastBrowserUrl = url
                        navController.navigate(Screen.Browser.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Browser.route) {
                BrowserScreen(initialUrl = lastBrowserUrl)
            }
            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onPlayVideo = { path ->
                        val encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8.name())
                        navController.navigate("player/$encodedPath")
                    }
                )
            }
            composable("player/{videoUri}") { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("videoUri")
                if (encodedUri != null) {
                    val decodedUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.name())
                    PlayerScreen(
                        videoUri = decodedUri,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
