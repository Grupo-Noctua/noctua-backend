package com.noctua.api.models

/**
 * Situacao do projeto. O rotulo gravado e exatamente o valor consumido pelo
 * frontend (`mock.tsx` declara a union `'Em dia' | 'Em risco' | 'Atrasado' |
 * 'Concluido'` com acentos e espacos), portanto a serializacao usa [rotulo].
 */
enum class StatusProjeto(val rotulo: String) {
    EM_DIA("Em dia"),
    EM_RISCO("Em risco"),
    ATRASADO("Atrasado"),
    CONCLUIDO("Concluído");

    companion object {
        fun fromRotulo(rotulo: String): StatusProjeto? = entries.firstOrNull { it.rotulo == rotulo }
    }
}
