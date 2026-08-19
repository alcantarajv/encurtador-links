package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.Link;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementacao de LinkRepository em memoria, usada apenas nos testes.
 *
 * Ate a Etapa 2 esta classe vivia em src/main e era o armazenamento de verdade.
 * Com a entrada do PostgreSQL ela mudou de papel: virou um dublê de teste, e por
 * isso mudou tambem de lugar (src/test) e perdeu o @Repository -- se
 * continuasse anotada, o Spring teria dois candidatos a LinkRepository e nao
 * saberia qual injetar.
 *
 * O ganho de manter a porta LinkRepository aparece aqui: o LinkServiceTest testa
 * a regra de negocio inteira sem banco, sem Docker e em milissegundos.
 */
public class InMemoryLinkRepository implements LinkRepository {

    private final Map<String, Link> linksByCode = new ConcurrentHashMap<>();

    @Override
    public boolean existsByCode(String code) {
        return linksByCode.containsKey(code);
    }

    @Override
    public Optional<Link> findByCode(String code) {
        return Optional.ofNullable(linksByCode.get(code));
    }

    @Override
    public Link save(Link link) {
        linksByCode.put(link.getCode(), link);
        return link;
    }
}
