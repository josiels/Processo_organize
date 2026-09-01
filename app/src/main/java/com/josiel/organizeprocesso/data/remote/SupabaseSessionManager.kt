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
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.flow.StateFlow

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
        }
    }

    val sessionStatus: StateFlow<SessionStatus>
        get() = client.auth.sessionStatus

    var perfilAtual: PerfilSessao? = null
        private set

    suspend fun login(email: String, senha: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = senha
        }
        carregarPerfilAtual()
    }

    suspend fun logout() {
        client.auth.signOut()
        perfilAtual = null
    }

    private suspend fun carregarPerfilAtual() {
        val userId = client.auth.currentUserOrNull()?.id ?: return
        val dto = client.postgrest["perfis"]
            .select(columns = Columns.ALL) {
                filter { eq("id", userId) }
            }
            .decodeSingle<PerfilDto>()
        perfilAtual = PerfilSessao(
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
