package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.DailyClicks;
import com.joaoalcantara.encurtador.domain.ReferrerClicks;
import java.time.Instant;
import java.util.List;

/**
 * Contrato de gravacao e agregacao de cliques.
 *
 * O metodo de gravacao recebe dados soltos em vez da entidade Click: assim a
 * entidade JPA fica sendo detalhe do adaptador, e o dublê de teste nao precisa
 * saber nada de JPA para funcionar.
 *
 * Repare que os metodos de leitura devolvem numeros ja agregados, e nao listas
 * de cliques. E de proposito: contar mil cliques em SQL custa uma consulta;
 * trazer os mil para a memoria da aplicacao e contar em Java custa mil linhas
 * atravessando a rede. Banco de dados existe para isso.
 */
public interface ClickRepository {

    /**
     * @param linkId    link acessado
     * @param clickedAt instante do acesso
     * @param referrer  origem declarada; pode ser nulo
     * @param userAgent navegador declarado; pode ser nulo
     * @param ipHash    hash do IP de origem; pode ser nulo
     */
    void save(Long linkId, Instant clickedAt, String referrer, String userAgent, String ipHash);

    long countByLinkId(Long linkId);

    /** Visitantes distintos, pelo hash do IP. */
    long countDistinctVisitorsByLinkId(Long linkId);

    /** Instante do ultimo acesso, ou nulo se o link nunca foi acessado. */
    Instant findLastClickAt(Long linkId);

    /** Total de cliques por dia, a partir da data informada, do mais antigo ao mais recente. */
    List<DailyClicks> countByDaySince(Long linkId, Instant since);

    /** Origens mais frequentes, da maior para a menor. */
    List<ReferrerClicks> findTopReferrers(Long linkId, int limit);
}
