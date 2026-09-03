package com.josiel.organizeprocesso.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.josiel.organizeprocesso.ui.theme.Indigo600

private data class BottomNavItem(
    val route: Any,
    val routeClass: kotlin.reflect.KClass<out Any>,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Inicio, Inicio::class, "Início", Icons.Filled.Home),
    BottomNavItem(Processos, Processos::class, "Processos", Icons.AutoMirrored.Filled.List),
    BottomNavItem(Agenda, Agenda::class, "Agenda", Icons.Filled.DateRange),
    BottomNavItem(Mais, Mais::class, "Mais", Icons.Filled.MoreVert)
)

/** Bottom nav fixo de 4 abas, item ativo em índigo (DESIGN.md, seção 1). */
@Composable
fun AppBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any {
                it.hasRoute(item.routeClass)
            } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        // `findStartDestination()` sempre resolve pra Login (o
                        // startDestination declarado do grafo) — que é removido
                        // da pilha assim que o usuário autentica
                        // (AppNavHost.kt, popUpTo(Login) { inclusive = true }).
                        // Um popUpTo mirando um id que não está mais na pilha é
                        // um no-op silencioso: cada troca de aba empilhava uma
                        // tela nova em vez de reaproveitar a existente. Início é
                        // a "aba casa" de verdade — sempre presente na pilha
                        // depois do login (achado da revisão final do Dashboard
                        // Início, corrigido aqui).
                        popUpTo(Inicio) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Indigo600,
                    selectedTextColor = Indigo600,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
