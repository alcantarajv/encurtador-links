package com.joaoalcantara.encurtador.domain;

import java.time.LocalDate;

/**
 * Quantos cliques um link recebeu num dia.
 *
 * @param day    dia, em UTC
 * @param clicks total de acessos naquele dia
 */
public record DailyClicks(LocalDate day, long clicks) {
}
