package com.joaoalcantara.encurtador.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de requisicao por IP (prefixo "shortener.rate-limit").
 *
 * Sao duas politicas com numeros bem diferentes de proposito. Criar link e uma
 * operacao cara -- grava no banco e consome espaco de codigos -- e nenhuma
 * pessoa real cria dez links por minuto. Ja o redirecionamento e o uso normal
 * do servico: uma pessoa navegando pode abrir varios links seguidos, e um limite
 * apertado ali quebraria o produto em vez de proteger.
 *
 * @param enabled  permite desligar o limite (util em teste e em ambiente local)
 * @param creation politica de POST /api/v1/links
 * @param redirect politica de GET /{code}
 * @param stats    politica de GET /api/v1/links/{code}/stats -- consulta de
 *                agregacao, mais cara que um redirecionamento e sem cache
 */
@ConfigurationProperties(prefix = "shortener.rate-limit")
public record RateLimitProperties(boolean enabled, Policy creation, Policy redirect, Policy stats) {

    /**
     * @param limit  quantas requisicoes sao permitidas dentro da janela
     * @param window duracao da janela
     */
    public record Policy(int limit, Duration window) {
    }
}
