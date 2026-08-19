package com.joaoalcantara.encurtador.domain;

/**
 * Quantos cliques vieram de uma mesma origem.
 *
 * @param referrer valor do cabecalho Referer
 * @param clicks   total de acessos vindos dali
 */
public record ReferrerClicks(String referrer, long clicks) {
}
