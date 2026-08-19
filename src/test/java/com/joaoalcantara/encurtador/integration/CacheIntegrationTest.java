package com.joaoalcantara.encurtador.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joaoalcantara.encurtador.IntegrationTest;
import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.exception.LinkNotFoundException;
import com.joaoalcantara.encurtador.repository.SpringDataClickRepository;
import com.joaoalcantara.encurtador.repository.SpringDataLinkRepository;
import com.joaoalcantara.encurtador.service.LinkService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * O cache do redirecionamento contra um Redis de verdade.
 *
 * Os testes de unidade nao alcancam nada disto: fora do Spring, a anotacao
 * @Cacheable e inerte. Se o proxy nao estivesse sendo aplicado -- por
 * auto-invocacao, por falta de @EnableCaching, por bean na classe errada -- todos
 * os testes de unidade continuariam verdes e o Redis ficaria vazio em producao.
 */
@SpringBootTest
@DisplayName("Cache do redirecionamento no Redis")
class CacheIntegrationTest extends IntegrationTest {

    @Autowired
    private LinkService linkService;

    @Autowired
    private SpringDataLinkRepository springDataLinkRepository;

    @Autowired
    private SpringDataClickRepository springDataClickRepository;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void limpaBancoECache() {
        springDataClickRepository.deleteAll();
        springDataLinkRepository.deleteAll();

        Set<String> chaves = redis.keys("*");
        if (chaves != null && !chaves.isEmpty()) {
            redis.delete(chaves);
        }
    }

    private String umLinkPara(String url) {
        return linkService.create(url, null).getCode();
    }

    @Test
    @DisplayName("nao cacheia nada na criacao do link")
    void criacaoNaoPovoaOCache() {
        umLinkPara("https://exemplo.com/novo");

        assertThat(redis.keys("encurtador:links::*")).isEmpty();
    }

    @Test
    @DisplayName("grava o destino no Redis no primeiro acesso")
    void primeiroAcessoPovoaOCache() {
        String code = umLinkPara("https://exemplo.com/destino");

        linkService.resolve(code);

        String valor = redis.opsForValue().get("encurtador:links::" + code);
        assertThat(valor)
                .contains("\"originalUrl\":\"https://exemplo.com/destino\"")
                .contains("\"expiresAt\":null");
    }

    @Test
    @DisplayName("define prazo de validade na entrada cacheada")
    void entradaCacheadaTemValidade() {
        String code = umLinkPara("https://exemplo.com");
        linkService.resolve(code);

        Long segundos = redis.getExpire("encurtador:links::" + code);

        assertThat(segundos).isNotNull().isPositive().isLessThanOrEqualTo(3600);
    }

    /**
     * A prova de que a resposta vem mesmo do cache: o link e apagado do
     * PostgreSQL e o resolve continua respondendo. Se estivesse consultando o
     * banco, aqui daria 404.
     */
    @Test
    @DisplayName("responde pelo cache mesmo com a linha apagada do banco")
    void respondeDoCacheComBancoVazio() {
        String code = umLinkPara("https://exemplo.com/persistente");
        linkService.resolve(code);

        springDataLinkRepository.deleteAll();

        assertThat(linkService.resolve(code).originalUrl()).isEqualTo("https://exemplo.com/persistente");
    }

    @Test
    @DisplayName("nao guarda no cache codigo que nao existe")
    void codigoInexistenteNaoVaiParaOCache() {
        assertThatThrownBy(() -> linkService.resolve("naoexi9"))
                .isInstanceOf(LinkNotFoundException.class);

        assertThat(redis.keys("encurtador:links::naoexi9")).isEmpty();
    }

    /**
     * O expiresAt viaja dentro do valor cacheado justamente para que a expiracao
     * nao dependa do prazo de validade da entrada no Redis.
     */
    @Test
    @DisplayName("recusa link vencido mesmo com a entrada viva no cache")
    void recusaLinkVencidoAindaCacheado() {
        Link vencido = springDataLinkRepository.save(new Link(
                "vencid1",
                "https://exemplo.com/vencido",
                java.time.Instant.now().minusSeconds(7200),
                java.time.Instant.now().minusSeconds(60)));

        assertThatThrownBy(() -> linkService.resolve(vencido.getCode()))
                .isInstanceOf(LinkNotFoundException.class);
    }
}
