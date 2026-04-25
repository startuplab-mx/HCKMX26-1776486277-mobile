package com.richi_mc.kipisafe.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.richi_mc.kipisafe.R
import com.richi_mc.kipisafe.data.local.AuthManager
import com.richi_mc.kipisafe.ui.presentation.home.HomeScreen
import com.richi_mc.kipisafe.ui.presentation.metrics.MetricsScreen
import com.richi_mc.kipisafe.ui.presentation.pairing.PairingScreen
import org.koin.compose.koinInject

data class NavigationItem<T : Any>(
    val title: String,
    val route: T,
    @DrawableRes val icon: Int
)

@Composable
fun KipiSafeNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val authManager: AuthManager = koinInject()
    val startDest = if (authManager.isDevicePaired()) Home else Pairing

    val navigationItems = listOf(
        NavigationItem("Inicio", Home, R.drawable.inicio),
        NavigationItem("Estadísticas", Stats, R.drawable.metrica)
    )

    // Solo mostrar bottom bar en Home o Stats
    val showBottomBar = currentDestination?.hasRoute(Home::class) == true || 
                       currentDestination?.hasRoute(Stats::class) == true

    Scaffold(
        contentWindowInsets = WindowInsets(top = 0.dp, bottom = 0.dp),
        bottomBar = {
            if (showBottomBar) {
                // Contenedor de la barra para aplicar el diseño flotante y redondeado
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp) // Espaciado contra los bordes
                        .navigationBarsPadding() // Respeta el área del sistema si es necesario
                        .clip(CircleShape) // Redondeo al 50%
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), // Borde según el tema
                            shape = CircleShape
                        ),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent, // El fondo lo da el Surface
                        tonalElevation = 8.dp,
                        modifier = Modifier.height(56.dp)
                    ) {
                        navigationItems.forEach { item ->
                            val isSelected = currentDestination?.hasRoute(item.route::class) == true
                            val backColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

                            NavigationBarItem(
                                selected = isSelected,
                                modifier = Modifier.background(backColor),
                                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent // Esto elimina el óvalo de fondo de M3
                                ),
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(id = item.icon),
                                        contentDescription = item.title,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier
                .padding(if (showBottomBar) innerPadding else PaddingValues(0.dp))
        ) {
            composable<Pairing> {
                PairingScreen(
                    onPairingSuccess = {
                        navController.navigate(Home) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable<Home> {
                HomeScreen()
            }

            composable<Stats> {
                MetricsScreen()
            }
        }
    }
}
