package com.joaoalcantara.encurtador.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.joaoalcantara.encurtador.IntegrationTest;
import com.joaoalcantara.encurtador.config.RateLimitProperties.Policy;
import com.joaoalcantara.encurtador.ratelimit.RateLimitDecision;
import com.joaoalcantara.encurtador.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * O contador de janela fixa contra um Redis de verdade.
 *
 * O teste de unidade do interceptor usa um limitador em memoria: ele verifica a
 * decisao de bloquear, mas nao encosta no script Lua, no INCR nem no prazo de
 * validade da chave. Se o script tivesse um erro de sintaxe, nada antes desta
 * etapa acusaria.
 */
@SpringBootTest
@DisplayName("Rate limiter no Redis")
class RateLimiterIntegrationTest extends IntegrationTest {

    private static final Policy POLICY = new Policy(3, Duration.ofMinutes(1));

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redis;

    /** Chave nova a cada teste: nenhum teste herda a contagem do anterior. */
    private String chave;

    @BeforeEach
    void novaChave() {
        chave = "teste:" + UUID.randomUUID();
    }

    @Test
    @DisplayName("permite as requisicoes ate o teto")
    void permiteAteOTeto() {
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryConsume(chave, POLICY).allowed()).isTrue();
        }
    }

    @Test
    @DisplayName("bloqueia a requisicao seguinte ao teto")
    void bloqueiaAcimaDoTeto() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.tryConsume(chave, POLICY);
        }

        assertThat(rateLimiter.tryConsume(chave, POLICY).allowed()).isFalse();
    }

    @Test
    @DisplayName("devolve o saldo decrescente da janela")
    void saldoDecresce() {
        assertThat(rateLimiter.tryConsume(chave, POLICY).remaining()).isEqualTo(2);
        assertThat(rateLimiter.tryConsume(chave, POLICY).remaining()).isEqualTo(1);
        assertThat(rateLimiter.tryConsume(chave, POLICY).remaining()).isZero();
    }

    @Test
    @DisplayName("nunca devolve saldo negativo")
    void saldoNaoFicaNegativo() {
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume(chave, POLICY);
        }

        assertThat(rateLimiter.tryConsume(chave, POLICY).remaining()).isZero();
    }

    /**
     * O PEXPIRE do script Lua e o que impede o bloqueio eterno: sem ele a chave
     * ficaria no Redis para sempre e aquele IP nunca mais passaria.
     */
    @Test
    @DisplayName("define prazo de validade na chave, dentro da janela")
    void chaveTemPrazoDeValidade() {
        rateLimiter.tryConsume(chave, POLICY);

        Long segundos = redis.getExpire("encurtador:ratelimit:" + chave);

        assertThat(segundos).isNotNull().isPositive().isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("informa quanto falta para a janela virar")
    void informaQuantoFaltaParaLiberar() {
        for (int i = 0; i < 4; i++) {
            rateLimiter.tryConsume(chave, POLICY);
        }

        RateLimitDecision decisao = rateLimiter.tryConsume(chave, POLICY);

        assertThat(decisao.allowed()).isFalse();
        assertThat(decisao.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
    }

    /**
     * Contadores por chave sao o que garante que o abuso de um visitante nao
     * derruba os outros.
     */
    @Test
    @DisplayName("conta cada chave separadamente")
    void chavesIndependentes() {
        String outraChave = "teste:" + UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            rateLimiter.tryConsume(chave, POLICY);
        }

        assertThat(rateLimiter.tryConsume(chave, POLICY).allowed()).isFalse();
        assertThat(rateLimiter.tryConsume(outraChave, POLICY).allowed()).isTrue();
    }

    /**
     * A janela nasce na PRIMEIRA requisicao -- e o PEXPIRE so roda quando o
     * contador vale 1. Se ele rodasse a cada incremento, a janela se renovaria a
     * cada acesso e um cliente insistente ficaria bloqueado indefinidamente.
     */
    @Test
    @DisplayName("nao renova a janela a cada requisicao")
    void janelaNaoSeRenova() throws InterruptedException {
        rateLimiter.tryConsume(chave, POLICY);
        Long aposPrimeira = redis.getExpire("encurtador:ratelimit:" + chave);

        Thread.sleep(1100);
        rateLimiter.tryConsume(chave, POLICY);
        Long aposSegunda = redis.getExpire("encurtador:ratelimit:" + chave);

        assertThat(aposSegunda).isLessThan(aposPrimeira);
    }

    @Test
    @DisplayName("usa o prefixo da aplicacao nas chaves")
    void usaPrefixoDaAplicacao() {
        rateLimiter.tryConsume(chave, POLICY);

        Set<String> chaves = redis.keys("encurtador:ratelimit:" + chave);

        assertThat(chaves).hasSize(1);
    }
}
