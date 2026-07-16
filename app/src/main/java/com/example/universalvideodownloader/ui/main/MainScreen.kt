package com.example.universalvideodownloader.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Ana Sayfa", Icons.Default.Home)
    object Browser : Screen("browser", "Tarayıcı", Icons.Default.Language)
    object Downloads : Screen("downloads", "İndirilenler", Icons.Default.List)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Browser, Screen.Downloads)
    
    // Uygulamanın en son hangi tarayıcı URL'sinde kaldığını tutabiliriz
    var lastBrowserUrl by remember { mutableStateOf("https://google.com") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
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
                // Burada BrowserScreen, URL değişikliğini dinleyebilir.
                // Basitlik açısından BrowserScreen kendi view model'ını (Hilt ViewModel) kullanır.
                // Eğer lastBrowserUrl değiştiyse onu yüklemeyi BrowserScreen içinde trigger edebiliriz.
                // Bunu viewModel üzerinden veya LaunchedEffect ile yapabiliriz.
                BrowserScreen(initialUrl = lastBrowserUrl)
            }
            composable(Screen.Downloads.route) {
                DownloadsScreen()
            }
        }
    }
}
