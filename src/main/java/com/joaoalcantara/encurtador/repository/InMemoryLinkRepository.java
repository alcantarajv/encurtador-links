package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.Link;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Armazenamento temporario em memoria, valido ate a Etapa 3.
 *
 * ConcurrentHashMap e nao HashMap porque o Tomcat atende cada requisicao numa
 * thread diferente: duas criacoes simultaneas escreveriam no mesmo mapa.
 *
 * Limitacao conhecida e aceita nesta etapa: os dados somem quando a aplicacao
 * reinicia.
 */
@Repository
public class InMemoryLinkRepository implements LinkRepository {

    private final Map<String, Link> linksByCode = new ConcurrentHashMap<>();

    @Override
    public boolean existsByCode(String code) {
        return linksByCode.containsKey(code);
    }

    @Override
    public Link save(Link link) {
        linksByCode.put(link.getCode(), link);
        return link;
    }
}
