package com.joaoalcantara.encurtador.ratelimit;

import com.joaoalcantara.encurtador.config.RateLimitProperties.Policy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RateLimiter em memoria, usado apenas nos testes.
 *
 * Conta na mesma logica do RedisRateLimiter -- janela fixa -- mas sem janela de
 * tempo: a contagem so zera quando o teste manda. Teste que depende de esperar o
 * relogio e teste lento e intermitente.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public RateLimitDecision tryConsume(String key, Policy policy) {
        long count = counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();

        if (count > policy.limit()) {
            return RateLimitDecision.blocked(policy.limit(), policy.window());
        }
        return RateLimitDecision.allowed(policy.limit(), policy.limit() - count, policy.window());
    }

    public void reset() {
        counters.clear();
    }
}
