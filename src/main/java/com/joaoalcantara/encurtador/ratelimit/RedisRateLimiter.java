package com.joaoalcantara.encurtador.ratelimit;

import com.joaoalcantara.encurtador.config.RateLimitProperties.Policy;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Contador de janela fixa mantido no Redis.
 *
 * POR QUE NO REDIS E NAO NUM MAPA NA MEMORIA
 *
 * Um contador local funciona enquanto existe uma instancia da aplicacao. Com
 * duas instancias atras de um balanceador, cada uma contaria a metade das
 * requisicoes e o limite real viraria o dobro do configurado. O Redis e o lugar
 * onde as instancias combinam a contagem.
 *
 * ALGORITMO E SUA LIMITACAO CONHECIDA
 *
 * Janela fixa: a primeira requisicao de um IP cria a chave com prazo de validade
 * igual a janela; as seguintes so incrementam. Quando a chave expira, a contagem
 * recomeca.
 *
 * O ponto fraco e a virada da janela. Com limite de 10 por minuto, um cliente
 * pode fazer 10 requisicoes no fim de uma janela e outras 10 no comeco da
 * seguinte -- 20 em poucos segundos. Algoritmos como janela deslizante ou token
 * bucket resolvem isso ao custo de bem mais complexidade. Para conter abuso
 * grosseiro na criacao de links, a janela fixa entrega o que precisa.
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String KEY_PREFIX = "encurtador:ratelimit:";

    /**
     * INCR e PEXPIRE numa operacao so.
     *
     * Feito em dois comandos separados, existiria uma janela em que o INCR
     * acontece e o PEXPIRE nao -- por queda da aplicacao ou da rede no meio. A
     * chave ficaria sem prazo de validade e aquele IP seria bloqueado para
     * sempre. O Redis executa um script Lua de forma atomica: ou os dois
     * comandos rodam, ou nenhum.
     *
     * Devolve a contagem e quanto falta para a chave expirar, em milissegundos,
     * poupando uma segunda ida ao Redis so para descobrir o TTL.
     */
    private static final RedisScript<List> INCREMENT_AND_EXPIRE = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return { count, redis.call('PTTL', KEYS[1]) }
            """, List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitDecision tryConsume(String key, Policy policy) {
        try {
            List<Long> result = redis.execute(
                    INCREMENT_AND_EXPIRE,
                    List.of(KEY_PREFIX + key),
                    String.valueOf(policy.window().toMillis()));

            long count = result.get(0);
            Duration retryAfter = Duration.ofMillis(Math.max(result.get(1), 0));

            if (count > policy.limit()) {
                return RateLimitDecision.blocked(policy.limit(), retryAfter);
            }
            return RateLimitDecision.allowed(policy.limit(), policy.limit() - count, retryAfter);

        } catch (RuntimeException e) {
            // DECISAO: falha aberta.
            //
            // Com o Redis fora do ar nao da para saber quantas requisicoes o IP
            // ja fez. Sao duas escolhas ruins: bloquear todo mundo (o servico
            // inteiro cai junto com o Redis) ou deixar passar (fica sem protecao
            // contra abuso ate o Redis voltar). Para um encurtador de links,
            // ficar no ar vale mais -- o limite e protecao contra abuso, nao
            // barreira de seguranca. Num fluxo de login ou pagamento a escolha
            // seria a oposta.
            log.warn("Rate limiter indisponivel, liberando a requisicao: {}", e.getMessage());
            return RateLimitDecision.allowed(policy.limit(), policy.limit(), policy.window());
        }
    }
}
