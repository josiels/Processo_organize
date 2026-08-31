package com.josiel.organizeprocesso.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.josiel.organizeprocesso.ui.agenda.AgendaScreen
import com.josiel.organizeprocesso.ui.cadastro.FasesScreen
import com.josiel.organizeprocesso.ui.cadastro.MaisScreen
import com.josiel.organizeprocesso.ui.cadastro.PessoasScreen
import com.josiel.organizeprocesso.ui.inicio.InicioScreen
import com.josiel.organizeprocesso.ui.processos.AvancarFaseScreen
import com.josiel.organizeprocesso.ui.processos.ProcessoDetalheScreen
import com.josiel.organizeprocesso.ui.processos.ProcessoFormScreen
import com.josiel.organizeprocesso.ui.processos.ProcessosScreen

private val abasComBottomBar = listOf(Inicio::class, Processos::class, Agenda::class, Mais::class)

/** Grafo de navegação do app: bottom nav de 4 abas + rotas empilhadas de detalhe/avançar fase. */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val mostrarBottomBar = abasComBottomBar.any { rota ->
        currentDestination?.hierarchy?.any { it.hasRoute(rota) } == true
    }

    Scaffold(
        bottomBar = { if (mostrarBottomBar) AppBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Inicio,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Inicio> { InicioScreen() }

            composable<Processos> {
                ProcessosScreen(
                    onProcessoClick = { processoId ->
                        navController.navigate(ProcessoDetalhe(processoId))
                    },
                    onNovoProcessoClick = { navController.navigate(ProcessoForm()) }
                )
            }

            composable<Agenda> { AgendaScreen() }

            composable<Mais> {
                MaisScreen(
                    onCadastroFasesClick = { navController.navigate(CadastroFases) },
                    onCadastroPessoasClick = { navController.navigate(CadastroPessoas) }
                )
            }

            composable<CadastroFases> {
                FasesScreen(onBackClick = { navController.navigateUp() })
            }

            composable<CadastroPessoas> {
                PessoasScreen(onBackClick = { navController.navigateUp() })
            }

            composable<ProcessoDetalhe> { entry ->
                val rota = entry.toRoute<ProcessoDetalhe>()
                ProcessoDetalheScreen(
                    processoId = rota.processoId,
                    onAvancarFaseClick = {
                        navController.navigate(AvancarFase(rota.processoId))
                    },
                    onEditarClick = {
                        navController.navigate(ProcessoForm(rota.processoId))
                    },
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable<AvancarFase> { entry ->
                val rota = entry.toRoute<AvancarFase>()
                AvancarFaseScreen(
                    processoId = rota.processoId,
                    onBackClick = { navController.navigateUp() }
                )
            }

            composable<ProcessoForm> { entry ->
                val rota = entry.toRoute<ProcessoForm>()
                ProcessoFormScreen(
                    processoId = rota.processoId,
                    onBackClick = { navController.navigateUp() },
                    onSalvo = { navController.navigateUp() }
                )
            }
        }
    }
}
