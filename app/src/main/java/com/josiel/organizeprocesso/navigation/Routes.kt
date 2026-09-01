package com.josiel.organizeprocesso.navigation

import kotlinx.serialization.Serializable

// As 4 abas do bottom nav (REQUISITOS.md, seção 8).
@Serializable
object Inicio

@Serializable
object Processos

@Serializable
object Agenda

@Serializable
object Mais

@Serializable
object Login

// Rotas empilhadas a partir da aba Processos.
@Serializable
data class ProcessoDetalhe(val processoId: String)

@Serializable
data class AvancarFase(val processoId: String)

// Criação/edição de processo (processoId nulo = criação).
@Serializable
data class ProcessoForm(val processoId: String? = null)

// Rotas empilhadas a partir da aba Mais.
@Serializable
object CadastroFases

@Serializable
object CadastroTiposProcesso
