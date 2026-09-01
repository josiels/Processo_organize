package com.josiel.organizeprocesso.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.josiel.organizeprocesso.data.local.AppDatabase
import com.josiel.organizeprocesso.data.remote.RealtimeSyncManager
import com.josiel.organizeprocesso.data.remote.RepositoriosSincronizaveis
import com.josiel.organizeprocesso.data.remote.SupabaseSessionManager
import com.josiel.organizeprocesso.data.repository.FaseRepository
import com.josiel.organizeprocesso.data.repository.HistoricoFaseRepository
import com.josiel.organizeprocesso.data.repository.PerfilRepository
import com.josiel.organizeprocesso.data.repository.ProcessoRepository
import com.josiel.organizeprocesso.data.repository.TipoProcessoRepository
import io.github.jan.supabase.auth.status.SessionStatus
import com.josiel.organizeprocesso.ui.agenda.AgendaScreen
import com.josiel.organizeprocesso.ui.auth.LoginScreen
import com.josiel.organizeprocesso.ui.cadastro.FasesScreen
import com.josiel.organizeprocesso.ui.cadastro.MaisScreen
import com.josiel.organizeprocesso.ui.cadastro.TiposProcessoScreen
import com.josiel.organizeprocesso.ui.inicio.InicioScreen
import com.josiel.organizeprocesso.ui.processos.AvancarFaseScreen
import com.josiel.organizeprocesso.ui.processos.ProcessoDetalheScreen
import com.josiel.organizeprocesso.ui.processos.ProcessoFormScreen
import com.josiel.organizeprocesso.ui.processos.ProcessosScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val abasComBottomBar = listOf(Inicio::class, Processos::class, Agenda::class, Mais::class)

/** Grafo de navegação do app: gate de login, depois bottom nav de 4 abas + rotas empilhadas. */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val mostrarBottomBar = abasComBottomBar.any { rota ->
        currentDestination?.hierarchy?.any { it.hasRoute(rota) } == true
    }
    val sessionStatus by SupabaseSessionManager.sessionStatus.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val database = remember { AppDatabase.getInstance(context) }
    val perfilRepository = remember { PerfilRepository(database.perfilDao(), SupabaseSessionManager.client) }
    val tipoProcessoRepository = remember { TipoProcessoRepository(database.tipoProcessoDao(), SupabaseSessionManager.client) }
    val faseRepository = remember { FaseRepository(database.faseDao(), SupabaseSessionManager.client) }
    val processoRepository = remember { ProcessoRepository(database, SupabaseSessionManager.client) }
    val historicoFaseRepository = remember { HistoricoFaseRepository(database, SupabaseSessionManager.client) }
    var sincronizacaoIniciada by remember { mutableStateOf(false) }

    LaunchedEffect(sessionStatus) {
        val autenticado = sessionStatus is SessionStatus.Authenticated
        val emLogin = currentDestination?.hierarchy?.any { it.hasRoute(Login::class) } == true

        // Antes de navegar para fora do Login: num cold start com sessão
        // persistida o Auth restaura sozinho, sem passar por login(), e os
        // gates de permissão leem o perfil de forma síncrona na construção dos
        // ViewModels. Sem isto todo admin que reabre o app vira somente-leitura.
        if (autenticado && SupabaseSessionManager.perfilAtual.value == null) {
            try {
                SupabaseSessionManager.carregarPerfilAtual()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                // Sem rede: segue para o app (em modo restrito); a próxima
                // transição de sessão tenta carregar de novo.
            }
        }

        if (autenticado && emLogin) {
            navController.navigate(Inicio) { popUpTo(Login) { inclusive = true } }
        } else if (!autenticado && !emLogin) {
            navController.navigate(Login) { popUpTo(0) { inclusive = true } }
        }

        if (autenticado && !sincronizacaoIniciada) {
            sincronizacaoIniciada = true
            try {
                perfilRepository.sincronizar()
                tipoProcessoRepository.sincronizar()
                faseRepository.sincronizar()
                processoRepository.sincronizar()
                // Depois de processos/fases/perfis (FKs do Room). O semáforo de
                // fase da lista precisa do histórico ativo de TODOS os processos,
                // então aqui é pull da tabela inteira; as demais tabelas por
                // processo (itens/diligências) sincronizam ao abrir a tela.
                historicoFaseRepository.sincronizarTodos()
                RealtimeSyncManager.iniciar(
                    client = SupabaseSessionManager.client,
                    escopo = coroutineScope,
                    repositorios = RepositoriosSincronizaveis(
                        perfis = perfilRepository::sincronizar,
                        tiposProcesso = tipoProcessoRepository::sincronizar,
                        fases = faseRepository::sincronizar,
                        // Toda mudança de fase também escreve em `processos`
                        // (fase_atual_id), então este canal é o gatilho para
                        // manter o semáforo de fase da lista fresco — a tabela
                        // `processo_fase_historico` não tem canal próprio.
                        processos = {
                            processoRepository.sincronizar()
                            historicoFaseRepository.sincronizarTodos()
                        }
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                sincronizacaoIniciada = false
            }
        } else if (!autenticado) {
            sincronizacaoIniciada = false
        }
    }

    Scaffold(
        bottomBar = { if (mostrarBottomBar) AppBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Login,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Login> { LoginScreen() }

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
                    onCadastroTiposProcessoClick = { navController.navigate(CadastroTiposProcesso) },
                    onSairClick = {
                        coroutineScope.launch {
                            // Ordem importa: encerra sessão/realtime antes de limpar o
                            // cache local, para não deixar dados de uma organização
                            // visíveis a quem logar em seguida no mesmo aparelho
                            // (spec do pivô, seção 2.6).
                            SupabaseSessionManager.logout()
                            RealtimeSyncManager.encerrar()
                            withContext(Dispatchers.IO) {
                                AppDatabase.getInstance(context).clearAllTables()
                            }
                        }
                    }
                )
            }

            composable<CadastroFases> {
                FasesScreen(onBackClick = { navController.navigateUp() })
            }

            composable<CadastroTiposProcesso> {
                TiposProcessoScreen(onBackClick = { navController.navigateUp() })
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
