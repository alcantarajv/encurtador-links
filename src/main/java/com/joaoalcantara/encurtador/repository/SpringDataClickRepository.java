package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.Click;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas de agregacao sobre a tabela de cliques.
 *
 * As duas primeiras nascem do nome do metodo; as demais precisam de JPQL ou SQL
 * porque agrupam e ordenam -- coisa que a convencao de nomes nao expressa.
 */
public interface SpringDataClickRepository extends JpaRepository<Click, Long> {

    long countByLinkId(Long linkId);

    @Query("select count(distinct c.ipHash) from Click c where c.link.id = :linkId")
    long countDistinctVisitors(@Param("linkId") Long linkId);

    @Query("select max(c.clickedAt) from Click c where c.link.id = :linkId")
    Instant findLastClickAt(@Param("linkId") Long linkId);

    /**
     * Consulta nativa porque date_trunc e do PostgreSQL: JPQL nao tem como
     * expressar "agrupe por dia". O projeto ja assumiu o PostgreSQL na Etapa 3,
     * entao o custo dessa amarracao e conhecido e aceito.
     *
     * Devolve Object[] com (java.sql.Date, Long) -- o adaptador converte.
     */
    @Query(value = """
            SELECT date_trunc('day', clicked_at)::date AS day, COUNT(*) AS clicks
            FROM clicks
            WHERE link_id = :linkId AND clicked_at >= :since
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> countByDaySince(@Param("linkId") Long linkId, @Param("since") Instant since);

    /**
     * Limit e o jeito do Spring Data 3.2+ de pedir "so os N primeiros" sem
     * montar um Pageable inteiro.
     */
    @Query("""
            select c.referrer as referrer, count(c) as clicks
            from Click c
            where c.link.id = :linkId and c.referrer is not null
            group by c.referrer
            order by count(c) desc
            """)
    List<Object[]> findTopReferrers(@Param("linkId") Long linkId, Limit limit);
}
