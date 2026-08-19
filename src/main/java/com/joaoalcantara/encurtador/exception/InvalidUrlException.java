package com.joaoalcantara.encurtador.exception;

/**
 * URL de origem rejeitada pela regra de negocio.
 *
 * Existe uma excecao propria (em vez de IllegalArgumentException) porque o
 * tratador global precisa distinguir "o cliente mandou uma URL invalida" de
 * qualquer outro argumento ilegal que apareca por bug interno: o primeiro caso
 * e 400, o segundo e 500.
 */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
