package com.joaoalcantara.encurtador.service;

import com.joaoalcantara.encurtador.domain.LinkTarget;
import com.joaoalcantara.encurtador.repository.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Busca o destino de um codigo curto, passando pelo cache do Redis.
 *
 * POR QUE ISTO E UMA CLASSE SEPARADA DO LinkService
 *
 * O @Cacheable so funciona quando a chamada atravessa o proxy que o Spring cria
 * em volta do bean. Se este metodo morasse no LinkService e fosse chamado de
 * outro metodo do proprio LinkService, a chamada seria um "this.findTarget(...)"
 * direto -- sem proxy, sem cache, e sem nenhum erro para avisar. O codigo
 * pareceria certo e o Redis ficaria vazio.
 *
 * Essa armadilha se chama auto-invocacao e vale para @Transactional, @Async e
 * qualquer anotacao baseada em AOP.
 */
@Component
public class LinkLookup {

    /** Nome do cache. Vira parte da chave no Redis. */
    public static final String CACHE_NAME = "links";

    private static final Logger log = LoggerFactory.getLogger(LinkLookup.class);

    private final LinkRepository linkRepository;

    public LinkLookup(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    /**
     * Retorna o destino do codigo, ou null se ele nao existir.
     *
     * Retorna null, e nao Optional, porque o "unless" abaixo precisa distinguir
     * achou de nao achou -- e um Optional.empty() e um objeto tao gravavel
     * quanto qualquer outro, ou seja, seria cacheado normalmente.
     *
     * unless = "#result == null": codigo inexistente nao vai para o cache.
     * A alternativa (cachear a ausencia) protegeria o PostgreSQL de uma varredura
     * de codigos aleatorios, mas abriria a possibilidade de um codigo ficar
     * marcado como inexistente no cache e ser criado logo depois. Com o volume
     * deste projeto, proteger a correcao vale mais; o freio contra varredura e o
     * rate limiting da Etapa 5.
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "#code", unless = "#result == null")
    public LinkTarget findTarget(String code) {
        log.debug("Cache miss para o codigo {}: consultando o banco", code);

        return linkRepository.findByCode(code)
                .map(link -> link.toTarget())
                .orElse(null);
    }
}
