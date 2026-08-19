package com.joaoalcantara.encurtador.controller;

import com.joaoalcantara.encurtador.config.ShortenerProperties;
import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.dto.CreateLinkRequest;
import com.joaoalcantara.encurtador.dto.LinkResponse;
import com.joaoalcantara.encurtador.service.LinkService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Porta HTTP da criacao de links.
 *
 * O controller so traduz: recebe JSON, chama o servico, devolve JSON. Nenhuma
 * regra de negocio mora aqui -- se morasse, so daria para testa-la subindo a
 * camada web inteira.
 *
 * O prefixo /api/v1 separa a API do caminho raiz, que na Etapa 4 sera usado
 * pelo redirecionamento (GET /{code}).
 */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;
    private final ShortenerProperties properties;

    public LinkController(LinkService linkService, ShortenerProperties properties) {
        this.linkService = linkService;
        this.properties = properties;
    }

    /**
     * O @Valid e o que dispara as anotacoes do CreateLinkRequest. Sem ele as
     * anotacoes ficam no codigo sem efeito nenhum -- erro classico e silencioso.
     *
     * Resposta 201 Created com header Location apontando para o recurso criado:
     * e o que o HTTP espera de uma criacao, e evita ter que documentar "o campo
     * shortUrl e o link".
     */
    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        Link link = linkService.create(request.originalUrl(), request.expiresAt());
        LinkResponse response = LinkResponse.from(link, properties.baseUrl());

        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }
}
