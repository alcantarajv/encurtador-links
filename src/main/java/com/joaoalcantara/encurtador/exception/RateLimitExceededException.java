package com.joaoalcantara.encurtador.exception;

import java.time.Duration;

/**
 * O IP estourou o limite de requisicoes da janela.
 */
public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("limite de requisicoes excedido");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
