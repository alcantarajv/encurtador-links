package com.joaoalcantara.encurtador.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joaoalcantara.encurtador.config.RateLimitProperties.Policy;
import com.joaoalcantara.encurtador.exception.RateLimitExceededException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("RateLimitInterceptor")
class RateLimitInterceptorTest {

    private static final Policy POLICY = new Policy(3, Duration.ofMinutes(1));

    private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        response = new MockHttpServletResponse();
    }

    private RateLimitInterceptor interceptor(boolean enabled) {
        return new RateLimitInterceptor(rateLimiter, POLICY, "creation", enabled);
    }

    @Test
    @DisplayName("deixa passar enquanto o IP esta dentro do limite")
    void deixaPassarDentroDoLimite() {
        RateLimitInterceptor interceptor = interceptor(true);

        assertThat(interceptor.preHandle(request, response, null)).isTrue();
        assertThat(interceptor.preHandle(request, response, null)).isTrue();
        assertThat(interceptor.preHandle(request, response, null)).isTrue();
    }

    @Test
    @DisplayName("bloqueia a requisicao seguinte ao teto")
    void bloqueiaAcimaDoLimite() {
        RateLimitInterceptor interceptor = interceptor(true);
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, null);
        }

        assertThatThrownBy(() -> interceptor.preHandle(request, response, null))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("informa o teto e o saldo nos cabecalhos da resposta")
    void informaSaldoNosCabecalhos() {
        RateLimitInterceptor interceptor = interceptor(true);

        interceptor.preHandle(request, response, null);

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("3");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("2");
    }

    @Test
    @DisplayName("zera o saldo quando bloqueia")
    void zeraSaldoQuandoBloqueia() {
        RateLimitInterceptor interceptor = interceptor(true);
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, null);
        }

        assertThatThrownBy(() -> interceptor.preHandle(request, response, null))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    /**
     * O contador e por IP: o abuso de um visitante nao pode derrubar os outros.
     */
    @Test
    @DisplayName("conta cada IP separadamente")
    void contaCadaIpSeparadamente() {
        RateLimitInterceptor interceptor = interceptor(true);
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, null);
        }

        MockHttpServletRequest outroIp = new MockHttpServletRequest();
        outroIp.setRemoteAddr("198.51.100.7");

        assertThat(interceptor.preHandle(outroIp, new MockHttpServletResponse(), null)).isTrue();
    }

    @Test
    @DisplayName("nao conta nada quando o limite esta desligado")
    void naoContaQuandoDesligado() {
        RateLimitInterceptor interceptor = interceptor(false);

        for (int i = 0; i < 50; i++) {
            assertThat(interceptor.preHandle(request, response, null)).isTrue();
        }
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }
}
