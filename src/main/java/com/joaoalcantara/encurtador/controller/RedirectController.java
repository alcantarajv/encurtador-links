package com.joaoalcantara.encurtador.controller;

import com.joaoalcantara.encurtador.domain.ClickEvent;
import com.joaoalcantara.encurtador.domain.LinkTarget;
import com.joaoalcantara.encurtador.service.ClickRecorder;
import com.joaoalcantara.encurtador.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
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
    private final ClickRecorder clickRecorder;
    private final Clock clock;

    public RedirectController(LinkService linkService, ClickRecorder clickRecorder, Clock clock) {
        this.linkService = linkService;
        this.clickRecorder = clickRecorder;
        this.clock = clock;
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
     * serve aqui -- se o navegador nao chama, nao ha clique para contar. O 301
     * tambem e dificil de desfazer: um link publicado com destino errado fica
     * cacheado no navegador de quem clicou, fora do alcance do servidor.
     *
     * O Cache-Control reforca a mesma intencao para proxies no meio do caminho.
     */
    @GetMapping("/{code:[A-Za-z0-9]{4,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        LinkTarget target = linkService.resolve(code);

        clickRecorder.record(clickEventOf(code, target, request));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target.originalUrl()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    /**
     * Copia da requisicao tudo o que a gravacao vai precisar.
     *
     * Isto acontece AQUI, na thread da requisicao, e nao la dentro do
     * ClickRecorder. O motivo e concreto: assim que a resposta e enviada, o
     * Tomcat devolve o HttpServletRequest ao pool e o reaproveita em outra
     * requisicao. Um acesso a request.getHeader(...) na thread de gravacao
     * leria o cabecalho de OUTRO visitante, ou estouraria.
     *
     * Vale notar a grafia: o cabecalho HTTP e "Referer", com um R so -- erro de
     * digitacao da especificacao original que ficou para sempre. O campo da API
     * usa "referrer", correto.
     */
    private ClickEvent clickEventOf(String code, LinkTarget target, HttpServletRequest request) {
        return new ClickEvent(
                code,
                target.id(),
                clock.instant(),
                request.getHeader(HttpHeaders.REFERER),
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getRemoteAddr());
    }
}
