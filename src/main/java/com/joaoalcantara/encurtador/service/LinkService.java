package com.joaoalcantara.encurtador.service;

import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.domain.LinkTarget;
import com.joaoalcantara.encurtador.exception.CodeGenerationException;
import com.joaoalcantara.encurtador.exception.InvalidUrlException;
import com.joaoalcantara.encurtador.exception.LinkNotFoundException;
import com.joaoalcantara.encurtador.repository.LinkRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra de negocio do encurtamento.
 *
 * O controller nao sabe gerar codigo nem validar URL, e o repositorio nao sabe
 * o que e uma URL valida. Toda decisao mora aqui, e por isso esta classe e a
 * que tem teste unitario de verdade.
 */
@Service
public class LinkService {

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);

    /** Protocolos aceitos. Sem essa checagem, "javascript:alert(1)" viraria um link valido. */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static final int MAX_CODE_ATTEMPTS = 5;

    private final LinkRepository linkRepository;
    private final LinkLookup linkLookup;
    private final ShortCodeGenerator codeGenerator;
    private final Clock clock;

    /**
     * Injecao pelo construtor: as dependencias sao obrigatorias e finais, e a
     * classe pode ser instanciada num teste sem subir o Spring. Como existe um
     * unico construtor, o @Autowired e dispensavel.
     */
    public LinkService(LinkRepository linkRepository,
                       LinkLookup linkLookup,
                       ShortCodeGenerator codeGenerator,
                       Clock clock) {
        this.linkRepository = linkRepository;
        this.linkLookup = linkLookup;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    /**
     * O @Transactional envolve a checagem de colisao e a gravacao numa
     * transacao so. Sem ele, cada chamada ao repositorio abriria a sua propria
     * transacao -- e a partir do momento em que criar um link significar mais de
     * uma escrita (a Etapa 6 grava estatisticas), metade da operacao poderia
     * ser efetivada e a outra metade falhar.
     */
    @Transactional
    public Link create(String originalUrl, Instant expiresAt) {
        String url = validateAndNormalize(originalUrl);
        String code = generateAvailableCode();

        Link link = new Link(code, url, clock.instant(), expiresAt);
        log.debug("Link criado: code={} url={}", code, url);

        return linkRepository.save(link);
    }

    /**
     * Resolve um codigo curto no destino do redirecionamento.
     *
     * A leitura vem do LinkLookup, que passa pelo cache do Redis. A checagem de
     * expiracao fica aqui, fora do cache, e a entrada cacheada carrega o
     * expiresAt junto -- assim um link vencido e recusado mesmo que a copia no
     * Redis continue viva. Se o cache guardasse so a URL, a expiracao passaria a
     * depender do TTL do Redis: o link seguiria funcionando por ate uma hora
     * depois de vencer.
     *
     * readOnly = true avisa ao Hibernate que nao havera escrita, o que dispensa
     * o rastreamento de alteracoes das entidades carregadas.
     */
    @Transactional(readOnly = true)
    public String resolve(String code) {
        LinkTarget target = linkLookup.findTarget(code);

        if (target == null || target.isExpired(clock.instant())) {
            throw new LinkNotFoundException(code);
        }

        return target.originalUrl();
    }

    /**
     * Revalida a URL mesmo o DTO ja tendo @Pattern.
     *
     * A anotacao protege a porta HTTP; esta checagem protege a regra de negocio
     * de qualquer outro caminho de entrada (uma fila, um job, um teste). O
     * @Pattern confere o formato do texto -- este metodo confere se a URL e
     * realmente resolvivel: tem protocolo aceito e tem host.
     */
    private String validateAndNormalize(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new InvalidUrlException("a URL e obrigatoria");
        }

        String trimmed = originalUrl.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("a URL informada e malformada");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("a URL precisa comecar com http:// ou https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("a URL precisa conter um dominio valido");
        }

        return trimmed;
    }

    /**
     * Sorteia codigos ate achar um livre.
     *
     * Com 62^7 combinacoes a chance de colidir e minima, mas "minima" nao e
     * "zero" -- e uma colisao silenciosa sobrescreveria o link de outra pessoa.
     *
     * Detalhe conhecido: entre o existsByCode e o save existe uma janela em que
     * outra requisicao pode gravar o mesmo codigo. Quem fecha essa janela de vez
     * nao e este laco, e sim o indice UNIQUE de "code" no banco: na pior das
     * hipoteses o insert falha em vez de sobrescrever o link de outra pessoa.
     */
    private String generateAvailableCode() {
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (!linkRepository.existsByCode(code)) {
                return code;
            }
            log.warn("Colisao de codigo curto na tentativa {}: {}", attempt, code);
        }
        throw new CodeGenerationException(
                "nao foi possivel gerar um codigo curto apos " + MAX_CODE_ATTEMPTS + " tentativas");
    }
}
