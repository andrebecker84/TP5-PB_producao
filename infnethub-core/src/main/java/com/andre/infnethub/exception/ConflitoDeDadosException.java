package com.andre.infnethub.exception;

/**
 * Operação válida em forma, mas que conflita com o estado atual dos dados —
 * e-mail já cadastrado, curtida repetida, remoção de registro ainda referenciado.
 *
 * <p>Distinta de {@link ResourceNotFoundException} (404) e da falha de validação
 * (400): aqui a requisição está correta, quem recusa é o estado do banco. Vira
 * 409 Conflict.
 */
public class ConflitoDeDadosException extends RuntimeException {

    public ConflitoDeDadosException(String message) {
        super(message);
    }
}
