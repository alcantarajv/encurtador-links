package com.joaoalcantara.encurtador.ratelimit;

import java.time.Duration;

/**
 * Resultado de uma consulta ao limitador.
 *
 * Carrega mais do que um booleano porque a resposta HTTP precisa contar ao
 * cliente onde ele esta: quantas requisicoes cabem na janela, quantas sobraram e
 * -- quando bloqueado -- quanto falta para poder tentar de novo. Sem isso, quem
 * consome a API so descobre o limite apanhando.
 *
 * @param allowed    se a requisicao pode seguir
 * @param limit      teto da janela
 * @param remaining  quantas ainda cabem (nunca negativo)
 * @param retryAfter quanto falta para a janela virar
 */
public record RateLimitDecision(boolean allowed, int limit, long remaining, Duration retryAfter) {

    public static RateLimitDecision allowed(int limit, long remaining, Duration retryAfter) {
        return new RateLimitDecision(true, limit, Math.max(remaining, 0), retryAfter);
    }

    public static RateLimitDecision blocked(int limit, Duration retryAfter) {
        return new RateLimitDecision(false, limit, 0, retryAfter);
    }
}
