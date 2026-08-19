package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.Link;

/**
 * Contrato de armazenamento de links.
 *
 * O servico depende desta interface, nao da implementacao. Quem responde em
 * producao e o adaptador JpaLinkRepository, sobre PostgreSQL; a troca do mapa em
 * memoria pelo banco na Etapa 3 nao alterou uma linha do servico nem do
 * controller.
 *
 * Nos testes o dublê em memoria ocupa o mesmo lugar -- e por isso a regra de
 * negocio roda sem banco e sem Docker.
 *
 * Os metodos aparecem conforme a necessidade real: findByCode so entra na etapa
 * do redirecionamento. Interface com metodo que ninguem chama e codigo morto.
 */
public interface LinkRepository {

    boolean existsByCode(String code);

    Link save(Link link);
}
