package com.josiel.organizeprocesso.domain.model

/** Espelha o enum `papel_usuario` do backend (Postgres) — ver spec do pivô, seção 2. */
enum class Papel {
    SUPER_ADMIN,
    ADMIN,
    USUARIO
}
