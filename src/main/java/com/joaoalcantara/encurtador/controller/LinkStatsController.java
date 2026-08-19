package com.joaoalcantara.encurtador.controller;

import com.joaoalcantara.encurtador.config.ShortenerProperties;
import com.joaoalcantara.encurtador.domain.LinkStats;
import com.joaoalcantara.encurtador.dto.LinkStatsResponse;
import com.joaoalcantara.encurtador.service.LinkStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estatisticas de uso de um link.
 *
 * Fica sob /api/v1 -- e nao na raiz como o redirecionamento -- porque e API de
 * consumo programatico, nao um endereco para ser colado num navegador.
 */
@RestController
@RequestMapping("/api/v1/links")
public class LinkStatsController {

    private final LinkStatsService linkStatsService;
    private final ShortenerProperties properties;

    public LinkStatsController(LinkStatsService linkStatsService, ShortenerProperties properties) {
        this.linkStatsService = linkStatsService;
        this.properties = properties;
    }

    @GetMapping("/{code}/stats")
    public LinkStatsResponse stats(@PathVariable String code) {
        LinkStats stats = linkStatsService.statsFor(code);

        return LinkStatsResponse.from(stats, properties.baseUrl());
    }
}
