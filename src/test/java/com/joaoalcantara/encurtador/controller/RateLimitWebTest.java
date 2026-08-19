package com.joaoalcantara.encurtador.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joaoalcantara.encurtador.config.RateLimitProperties;
import com.joaoalcantara.encurtador.config.ShortenerProperties;
import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.domain.LinkTarget;
import com.joaoalcantara.encurtador.ratelimit.InMemoryRateLimiter;
import com.joaoalcantara.encurtador.config.ClockConfig;
import com.joaoalcantara.encurtador.service.ClickRecorder;
import com.joaoalcantara.encurtador.service.LinkService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testa o rate limiting montado de verdade: interceptor registrado nas rotas,
 * excecao virando resposta 429.
 *
 * Os limites sao apertados por propriedade (2 e 3) para o teste nao precisar
 * disparar centenas de requisicoes.
 */
@WebMvcTest({LinkController.class, RedirectController.class})
@EnableConfigurationProperties({ShortenerProperties.class, RateLimitProperties.class})
@Import({RateLimitWebTest.LimiterEmMemoria.class, ClockConfig.class})
@TestPropertySource(properties = {
        "shortener.base-url=http://localhost:8080",
        "shortener.rate-limit.enabled=true",
        "shortener.rate-limit.creation.limit=2",
        "shortener.rate-limit.creation.window=1m",
        "shortener.rate-limit.redirect.limit=3",
        "shortener.rate-limit.redirect.window=1m"
})
@DisplayName("Rate limiting")
class RateLimitWebTest {

    @TestConfiguration
    static class LimiterEmMemoria {
        // Retorna o tipo concreto para que o teste possa injeta-lo e zerar a
        // contagem entre um metodo e outro.
        @Bean
        InMemoryRateLimiter rateLimiter() {
            return new InMemoryRateLimiter();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LinkService linkService;

    @MockitoBean
    private ClickRecorder clickRecorder;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    /**
     * O Spring reaproveita o mesmo contexto entre os metodos da classe, entao o
     * limitador e o MESMO objeto em todos eles. Sem zerar aqui, a contagem de um
     * teste estoura o limite do proximo -- e a suite passa ou falha conforme a
     * ordem em que os metodos rodam.
     */
    @BeforeEach
    void zeraContadores() {
        rateLimiter.reset();
    }

    private void criaLink() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"originalUrl": "https://exemplo.com"}
                        """));
    }

    @Test
    @DisplayName("responde 429 com Retry-After ao estourar o limite de criacao")
    void bloqueiaCriacaoAcimaDoLimite() throws Exception {
        given(linkService.create(any(), any()))
                .willReturn(new Link("abc1234", "https://exemplo.com", Instant.now(), null));

        criaLink();
        criaLink();

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl": "https://exemplo.com"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.title").value("Muitas requisicoes"))
                .andExpect(jsonPath("$.retryAfterSeconds").exists());
    }

    @Test
    @DisplayName("responde 429 ao estourar o limite de redirecionamento")
    void bloqueiaRedirecionamentoAcimaDoLimite() throws Exception {
        given(linkService.resolve(any())).willReturn(new LinkTarget(1L, "https://exemplo.com", null));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/abc1234")).andExpect(status().isFound());
        }

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Os dois limites sao contados em chaves diferentes: gastar o de criacao nao
     * pode consumir o de redirecionamento.
     */
    @Test
    @DisplayName("conta criacao e redirecionamento em contadores separados")
    void contadoresIndependentes() throws Exception {
        given(linkService.create(any(), any()))
                .willReturn(new Link("abc1234", "https://exemplo.com", Instant.now(), null));
        given(linkService.resolve(any())).willReturn(new LinkTarget(1L, "https://exemplo.com", null));

        criaLink();
        criaLink();
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl": "https://exemplo.com"}
                                """))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/abc1234")).andExpect(status().isFound());
    }

    @Test
    @DisplayName("informa o saldo restante nas respostas bem-sucedidas")
    void informaSaldoRestante() throws Exception {
        given(linkService.resolve(any())).willReturn(new LinkTarget(1L, "https://exemplo.com", null));

        mockMvc.perform(get("/abc1234"))
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "2"));
    }
}
