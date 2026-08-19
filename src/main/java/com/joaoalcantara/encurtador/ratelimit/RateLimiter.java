package com.joaoalcantara.encurtador.ratelimit;

import com.joaoalcantara.encurtador.config.RateLimitProperties.Policy;

/**
 * Contabiliza uma requisicao e diz se ela pode seguir.
 *
 * Mesma ideia da porta LinkRepository: o interceptor HTTP depende desta
 * interface e nao do Redis. Nos testes entra uma implementacao em memoria, e a
 * regra de "bloqueia ou nao" e exercitada sem infraestrutura.
 *
 * @see RedisRateLimiter
 */
public interface RateLimiter {

    /**
     * @param key    identifica quem esta sendo contado (politica + IP)
     * @param policy teto e janela a aplicar
     */
    RateLimitDecision tryConsume(String key, Policy policy);
}
