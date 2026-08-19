package com.joaoalcantara.encurtador.service;

import com.joaoalcantara.encurtador.config.ClickTrackingProperties;
import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.domain.LinkStats;
import com.joaoalcantara.encurtador.exception.LinkNotFoundException;
import com.joaoalcantara.encurtador.repository.ClickRepository;
import com.joaoalcantara.encurtador.repository.LinkRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta o retrato de uso de um link.
 *
 * Classe separada do LinkService porque o proposito e outro: o LinkService cuida
 * do fluxo quente -- criar e resolver. Estatistica e consulta fria, pesada e de
 * baixo volume.
 */
@Service
public class LinkStatsService {

    private final LinkRepository linkRepository;
    private final ClickRepository clickRepository;
    private final ClickTrackingProperties properties;
    private final Clock clock;

    public LinkStatsService(LinkRepository linkRepository,
                            ClickRepository clickRepository,
                            ClickTrackingProperties properties,
                            Clock clock) {
        this.linkRepository = linkRepository;
        this.clickRepository = clickRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Le direto do banco, sem passar pelo cache do redirecionamento.
     *
     * O cache guarda LinkTarget, que nao tem data de criacao; e, mais importante,
     * a estatistica precisa do numero de agora, nao de uma copia de ate uma hora
     * atras. Este endpoint e chamado de vez em quando, nao milhares de vezes por
     * minuto -- nao e onde o cache faz falta.
     *
     * Um link expirado continua tendo estatistica: ele parou de redirecionar,
     * mas o historico de quem clicou nele nao deixou de existir.
     */
    @Transactional(readOnly = true)
    public LinkStats statsFor(String code) {
        Link link = linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));

        Long linkId = link.getId();
        Instant since = clock.instant()
                .minus(Duration.ofDays(properties.historyDays()))
                .truncatedTo(ChronoUnit.DAYS);

        return new LinkStats(
                link,
                clickRepository.countByLinkId(linkId),
                clickRepository.countDistinctVisitorsByLinkId(linkId),
                clickRepository.findLastClickAt(linkId),
                clickRepository.countByDaySince(linkId, since),
                clickRepository.findTopReferrers(linkId, properties.topReferrersLimit()));
    }
}
