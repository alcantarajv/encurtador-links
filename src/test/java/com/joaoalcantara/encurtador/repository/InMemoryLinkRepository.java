package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.Link;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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

    private final AtomicLong sequence = new AtomicLong();

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
        assignId(link);
        linksByCode.put(link.getCode(), link);
        return link;
    }

    /**
     * Atribui a chave primaria, que e o que o banco faz no insert.
     *
     * Por reflexao porque o id nao tem setter -- de proposito: nada na aplicacao
     * deveria escolher a chave, ela e do banco. O JPA preenche esse mesmo campo
     * exatamente do mesmo jeito. Sem isto, todo link salvo em teste ficaria com
     * id nulo e qualquer coisa que dependa da chave estrangeira quebraria.
     */
    private void assignId(Link link) {
        if (link.getId() != null) {
            return;
        }
        try {
            Field id = Link.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(link, sequence.incrementAndGet());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("nao foi possivel atribuir o id no dublê de teste", e);
        }
    }
}
