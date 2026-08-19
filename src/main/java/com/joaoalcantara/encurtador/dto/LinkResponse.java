package com.joaoalcantara.encurtador.dto;

import com.joaoalcantara.encurtador.domain.Link;
import java.time.Instant;

/**
 * Corpo da resposta de criacao de link.
 *
 * DTO separado do dominio de proposito: a classe Link pode ganhar campos
 * internos (id do banco, contador de cliques) sem que isso vaze automaticamente
 * para o contrato publico da API.
 */
public record LinkResponse(
        String code,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt
) {

    public static LinkResponse from(Link link, String baseUrl) {
        return new LinkResponse(
                link.getCode(),
                baseUrl + "/" + link.getCode(),
                link.getOriginalUrl(),
                link.getCreatedAt(),
                link.getExpiresAt()
        );
    }
}
