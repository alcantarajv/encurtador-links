package com.joaoalcantara.encurtador.exception;

/**
 * Nao foi possivel gerar um codigo curto livre dentro do numero de tentativas.
 *
 * Na pratica isso so acontece se o espaco de codigos estiver quase esgotado.
 * E um erro do servidor, nao do cliente.
 */
public class CodeGenerationException extends RuntimeException {

    public CodeGenerationException(String message) {
        super(message);
    }
}
