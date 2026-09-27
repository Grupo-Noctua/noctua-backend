package com.noctua.api.repositories

import com.noctua.api.models.Usuario

interface UsuarioRepository {

    fun buscarPorEmail(email: String): Usuario?

    fun buscarPorId(id: Long): Usuario?

    /** Insere e devolve a linha persistida, com `id` e `criado_em` do banco. */
    fun criar(nome: String, email: String, senhaHash: String, papel: String): Usuario

    fun existePorEmail(email: String): Boolean
}
