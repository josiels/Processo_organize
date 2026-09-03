package com.josiel.organizeprocesso.data.repository

/**
 * Lançada quando um `UPDATE` via Postgrest afeta 0 linhas — a RLS bloqueou
 * silenciosamente a escrita (sem erro) ou o registro não existe mais. O
 * Postgrest não distingue esses dois casos de "sucesso sem nenhuma linha
 * afetada" de um erro de verdade, então quem chama `updateVerificado()`
 * precisa levantar essa exceção manualmente a partir da contagem retornada.
 */
class EscritaSemEfeitoException :
    Exception("A escrita não afetou nenhuma linha — bloqueada por permissão ou o registro não existe mais.")
