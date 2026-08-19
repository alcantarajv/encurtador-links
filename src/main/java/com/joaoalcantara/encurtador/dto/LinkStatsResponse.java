package com.joaoalcantara.encurtador.dto;

import com.joaoalcantara.encurtador.domain.DailyClicks;
import com.joaoalcantara.encurtador.domain.LinkStats;
import com.joaoalcantara.encurtador.domain.ReferrerClicks;
import java.time.Instant;
import java.util.List;

/**
 * Corpo da resposta de estatisticas.
 *
 * DailyClicks e ReferrerClicks aparecem aqui direto, sem DTO espelho. Sao
 * records de dois campos cujo formato publico e identico ao interno: criar uma
 * copia identica so para "seguir o padrao" adicionaria codigo de conversao sem
 * nenhum ganho. Se um dia o formato da API precisar divergir do interno, o DTO
 * proprio entra ai.
 */
public record LinkStatsResponse(
        String code,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        long totalClicks,
        long uniqueVisitors,
        Instant lastClickAt,
        List<DailyClicks> clicksByDay,
        List<ReferrerClicks> topReferrers
) {

    public static LinkStatsResponse from(LinkStats stats, String baseUrl) {
        return new LinkStatsResponse(
                stats.link().getCode(),
                baseUrl + "/" + stats.link().getCode(),
                stats.link().getOriginalUrl(),
                stats.link().getCreatedAt(),
                stats.link().getExpiresAt(),
                stats.totalClicks(),
                stats.uniqueVisitors(),
                stats.lastClickAt(),
                stats.clicksByDay(),
                stats.topReferrers());
    }
}
