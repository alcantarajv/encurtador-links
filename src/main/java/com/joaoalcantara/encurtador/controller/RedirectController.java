package com.joaoalcantara.encurtador.controller;

import com.joaoalcantara.encurtador.service.LinkService;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * O caminho quente da aplicacao: GET /{code} leva o visitante ao destino.
 *
 * Fica na raiz, e nao sob /api/v1, porque a URL curta precisa ser curta -- e
 * "encurtador.com/abc1234" cumpre isso melhor do que
 * "encurtador.com/api/v1/links/abc1234/redirect".
 */
@RestController
public class RedirectController {

    private final LinkService linkService;

    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * A expressao regular no @GetMapping delimita o que este metodo aceita.
     * Sem ela, "/{code}" capturaria qualquer caminho de um segmento --
     * /favicon.ico, /robots.txt, /qualquer-coisa -- e todos virariam consulta ao
     * cache e ao banco. O limite de 16 caracteres acompanha a coluna "code".
     *
     * POR QUE 302 E NAO 301
     *
     * O 301 (permanente) e mais rapido: o navegador memoriza o destino e nas
     * proximas vezes nem chega a chamar o servico. E exatamente por isso ele nao
     * serve aqui -- se o navegador nao chama, nao ha o que contar, e a Etapa 6 e
     * justamente registro de cliques. O 301 tambem e dificil de desfazer: um
     * link publicado com destino errado fica cacheado no navegador de quem
     * clicou, fora do alcance do servidor.
     *
     * O Cache-Control reforca a mesma intencao para proxies no meio do caminho.
     */
    @GetMapping("/{code:[A-Za-z0-9]{4,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = linkService.resolve(code);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
