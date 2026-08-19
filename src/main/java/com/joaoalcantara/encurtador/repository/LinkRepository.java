package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.Link;

/**
 * Contrato de armazenamento de links.
 *
 * O servico depende desta interface, nao da implementacao. Hoje quem responde e
 * um mapa em memoria; na Etapa 3 entra o PostgreSQL e nenhuma linha do servico
 * ou do controller precisa mudar.
 *
 * Os metodos aparecem conforme a necessidade real: findByCode so entra na etapa
 * do redirecionamento. Interface com metodo que ninguem chama e codigo morto.
 */
public interface LinkRepository {

    boolean existsByCode(String code);

    Link save(Link link);
}
