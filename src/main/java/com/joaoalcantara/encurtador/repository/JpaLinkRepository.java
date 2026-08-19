package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.Link;
import org.springframework.stereotype.Repository;

/**
 * Adaptador que liga a porta LinkRepository ao Spring Data JPA.
 *
 * Por que nao fazer LinkRepository estender JpaRepository direto, que e o que a
 * maioria dos projetos faz: porque isso obrigaria o LinkService a conhecer o
 * Spring Data. Com o adaptador, a regra de negocio depende de tres metodos
 * proprios e nao dos mais de vinte que o JpaRepository traz -- e trocar a
 * persistencia por JDBC puro, Redis ou um mock nao encosta no servico.
 *
 * O preco e esta classe de repasse. E o preco que paga por manter a Etapa 2
 * intacta: nenhuma linha do LinkService mudou por causa do PostgreSQL.
 */
@Repository
public class JpaLinkRepository implements LinkRepository {

    private final SpringDataLinkRepository repository;

    public JpaLinkRepository(SpringDataLinkRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    public Link save(Link link) {
        return repository.save(link);
    }
}
