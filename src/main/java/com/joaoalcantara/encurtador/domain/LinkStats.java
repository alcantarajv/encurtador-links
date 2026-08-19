package com.joaoalcantara.encurtador.domain;

import java.time.Instant;
import java.util.List;

/**
 * Retrato de uso de um link.
 *
 * @param link           o link medido
 * @param totalClicks    total de acessos desde a criacao
 * @param uniqueVisitors visitantes distintos, contados por hash de IP
 * @param lastClickAt    ultimo acesso; nulo se o link nunca foi acessado
 * @param clicksByDay    historico diario recente, do mais antigo ao mais novo
 * @param topReferrers   origens mais frequentes
 */
public record LinkStats(
        Link link,
        long totalClicks,
        long uniqueVisitors,
        Instant lastClickAt,
        List<DailyClicks> clicksByDay,
        List<ReferrerClicks> topReferrers
) {
}
