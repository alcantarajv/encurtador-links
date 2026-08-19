package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.Click;
import com.joaoalcantara.encurtador.domain.DailyClicks;
import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.domain.ReferrerClicks;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/**
 * Adaptador entre a porta ClickRepository e o Spring Data JPA.
 *
 * Alem de repassar, monta a entidade Click e converte os Object[] das consultas
 * de agregacao para os records da porta. Esse trabalho fica aqui para que nem a
 * entidade nem o tipo cru do JDBC vazem para a regra de negocio.
 */
@Repository
public class JpaClickRepository implements ClickRepository {

    private final SpringDataClickRepository clickRepository;
    private final SpringDataLinkRepository linkRepository;

    public JpaClickRepository(SpringDataClickRepository clickRepository,
                              SpringDataLinkRepository linkRepository) {
        this.clickRepository = clickRepository;
        this.linkRepository = linkRepository;
    }

    @Override
    public void save(Long linkId, Instant clickedAt, String referrer, String userAgent, String ipHash) {
        // getReferenceById devolve um proxy preguicoso: o Hibernate NAO faz
        // SELECT na tabela links, so usa o id para preencher a chave
        // estrangeira. Com findById seriam duas idas ao banco por clique em vez
        // de uma -- e o dado carregado seria jogado fora em seguida.
        Link link = linkRepository.getReferenceById(linkId);

        clickRepository.save(new Click(link, clickedAt, referrer, userAgent, ipHash));
    }

    @Override
    public long countByLinkId(Long linkId) {
        return clickRepository.countByLinkId(linkId);
    }

    @Override
    public long countDistinctVisitorsByLinkId(Long linkId) {
        return clickRepository.countDistinctVisitors(linkId);
    }

    @Override
    public Instant findLastClickAt(Long linkId) {
        return clickRepository.findLastClickAt(linkId);
    }

    @Override
    public List<DailyClicks> countByDaySince(Long linkId, Instant since) {
        return clickRepository.countByDaySince(linkId, since).stream()
                .map(row -> new DailyClicks(toLocalDate(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }

    @Override
    public List<ReferrerClicks> findTopReferrers(Long linkId, int limit) {
        return clickRepository.findTopReferrers(linkId, Limit.of(limit)).stream()
                .map(row -> new ReferrerClicks((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    /**
     * O driver do PostgreSQL devolve a coluna date como java.sql.Date. O cast
     * direto para LocalDate falharia; o caminho e pelo toLocalDate() do proprio
     * java.sql.Date.
     */
    private LocalDate toLocalDate(Object value) {
        return value instanceof Date date ? date.toLocalDate() : LocalDate.parse(value.toString());
    }
}
