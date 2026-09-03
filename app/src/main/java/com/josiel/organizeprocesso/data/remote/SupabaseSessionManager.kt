package com.josiel.organizeprocesso.data.remote

import com.josiel.organizeprocesso.BuildConfig
import com.josiel.organizeprocesso.data.remote.dto.PerfilDto
import com.josiel.organizeprocesso.domain.model.Papel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Espelha uma linha de `public.perfis`, já convertida para os tipos do domínio do app. */
data class PerfilSessao(
    val id: String,
    val organizacaoId: String?,
    val papel: Papel
)

/**
 * Singleton manual (sem DI framework, mesmo padrão de `AppDatabase.getInstance`)
 * que concentra o cliente Supabase e o estado de quem está logado. Ver spec
 * do Plano 2A, seção 2.1.
 */
object SupabaseSessionManager {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Functions)
        }
    }

    val sessionStatus: StateFlow<SessionStatus>
        get() = client.auth.sessionStatus

    private val _perfilAtual = MutableStateFlow<PerfilSessao?>(null)

    /**
     * Perfil de quem está logado. É um StateFlow (e não um `var`) porque o
     * plugin Auth restaura a sessão persistida sozinho num cold start — sem
     * passar por `login()` —, e os gates de permissão da UI precisam ver o
     * perfil recarregado nesse caminho também (senão todo admin que reabre o
     * app vira espectador somente-leitura). Quem recarrega — tanto no cold
     * start quanto após um login interativo — é o único
     * `LaunchedEffect(sessionStatus)` de `AppNavHost`, antes de navegar para
     * fora do Login (guardado por `perfilAtual.value == null`, então roda
     * exatamente uma vez por sessão).
     */
    val perfilAtual: StateFlow<PerfilSessao?> = _perfilAtual.asStateFlow()

    /**
     * Só autentica — não recarrega o perfil aqui. `login()` e a restauração
     * automática de sessão do cold start convergem para o mesmo
     * `sessionStatus`, e `AppNavHost` é a única fonte de `carregarPerfilAtual()`
     * para os dois casos; chamar aqui também criava uma corrida entre essa
     * chamada e a de `AppNavHost` — se esta falhasse por rede instável
     * enquanto a outra tinha sucesso em paralelo, o usuário via "login
     * inválido" mesmo autenticado com sucesso.
     */
    suspend fun login(email: String, senha: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = senha
        }
    }

    suspend fun logout() {
        client.auth.signOut()
        _perfilAtual.value = null
    }

    /** Lê `public.perfis` do usuário autenticado e publica em [perfilAtual]. */
    suspend fun carregarPerfilAtual() {
        val userId = client.auth.currentUserOrNull()?.id ?: return
        val dto = client.postgrest["perfis"]
            .select(columns = Columns.ALL) {
                filter { eq("id", userId) }
            }
            .decodeSingle<PerfilDto>()
        _perfilAtual.value = PerfilSessao(
            id = dto.id,
            organizacaoId = dto.organizacaoId,
            papel = papelDoTexto(dto.papel)
        )
    }
}

/** Mapeia o texto do enum `papel_usuario` do backend para o enum de domínio do app. */
fun papelDoTexto(valor: String): Papel = when (valor) {
    "super_admin" -> Papel.SUPER_ADMIN
    "admin" -> Papel.ADMIN
    "usuario" -> Papel.USUARIO
    else -> throw IllegalArgumentException("Papel desconhecido: $valor")
}
