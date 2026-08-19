package com.joaoalcantara.encurtador.ratelimit;

import com.joaoalcantara.encurtador.config.RateLimitProperties.Policy;
import com.joaoalcantara.encurtador.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Cobra o limite antes de a requisicao chegar ao controller.
 *
 * Interceptor e nao codigo dentro do controller: contar requisicao nao e regra
 * de encurtamento de link. Se estivesse no LinkController, cada endpoint novo
 * teria que lembrar de chamar o limitador.
 *
 * Uma instancia por politica -- uma para criacao, outra para redirecionamento --
 * registradas em caminhos diferentes no WebConfig. Assim o interceptor nao
 * precisa descobrir em qual rota esta para escolher o limite.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiter rateLimiter;
    private final Policy policy;
    private final String policyName;
    private final boolean enabled;

    public RateLimitInterceptor(RateLimiter rateLimiter, Policy policy, String policyName, boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.policy = policy;
        this.policyName = policyName;
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }

        String clientIp = clientIpOf(request);
        RateLimitDecision decision = rateLimiter.tryConsume(policyName + ":" + clientIp, policy);

        // Cabecalhos informativos em toda resposta, nao so quando bloqueia:
        // quem consome a API consegue se ajustar antes de apanhar.
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            log.warn("Limite de {} excedido para o IP {}", policyName, clientIp);
            throw new RateLimitExceededException(decision.retryAfter());
        }

        return true;
    }

    /**
     * De onde veio a requisicao.
     *
     * ARMADILHA: atras de um proxy ou balanceador -- o que sera o caso no deploy
     * da Etapa 10 -- getRemoteAddr() devolve o IP do proxy, e nao o do visitante.
     * Todo mundo cairia no mesmo contador e o servico inteiro seria bloqueado
     * pelo trafego somado.
     *
     * A correcao NAO e ler o X-Forwarded-For aqui na mao: esse cabecalho e
     * escrito pelo cliente e qualquer um pode forjar um IP diferente a cada
     * requisicao para escapar do limite. Quem resolve e o
     * server.forward-headers-strategy=framework no application.properties: o
     * Spring processa o cabecalho antes, num filtro, e o getRemoteAddr() ja
     * chega corrigido aqui.
     *
     * Isso vale enquanto a aplicacao so for alcancavel atraves do proxy. Exposta
     * direto na internet, o cabecalho volta a ser forjavel.
     */
    private String clientIpOf(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "desconhecido";
    }
}
