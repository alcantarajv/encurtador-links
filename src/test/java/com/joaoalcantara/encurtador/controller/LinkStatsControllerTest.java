package com.joaoalcantara.encurtador.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joaoalcantara.encurtador.config.RateLimitProperties;
import com.joaoalcantara.encurtador.config.ShortenerProperties;
import com.joaoalcantara.encurtador.domain.DailyClicks;
import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.domain.LinkStats;
import com.joaoalcantara.encurtador.domain.ReferrerClicks;
import com.joaoalcantara.encurtador.exception.LinkNotFoundException;
import com.joaoalcantara.encurtador.ratelimit.RateLimiter;
import com.joaoalcantara.encurtador.service.LinkStatsService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LinkStatsController.class)
@EnableConfigurationProperties({ShortenerProperties.class, RateLimitProperties.class})
@TestPropertySource(properties = {
        "shortener.base-url=http://localhost:8080",
        "shortener.rate-limit.enabled=false"
})
@DisplayName("GET /api/v1/links/{code}/stats")
class LinkStatsControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-10T08:00:00Z");
    private static final Instant LAST_CLICK = Instant.parse("2026-01-15T09:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LinkStatsService linkStatsService;

    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    @DisplayName("devolve o retrato de uso do link")
    void devolveEstatisticas() throws Exception {
        Link link = new Link("abc1234", "https://exemplo.com/destino", CREATED_AT, null);
        given(linkStatsService.statsFor("abc1234")).willReturn(new LinkStats(
                link,
                42,
                17,
                LAST_CLICK,
                List.of(new DailyClicks(LocalDate.of(2026, 1, 14), 10),
                        new DailyClicks(LocalDate.of(2026, 1, 15), 32)),
                List.of(new ReferrerClicks("https://google.com", 30),
                        new ReferrerClicks("https://twitter.com", 12))));

        mockMvc.perform(get("/api/v1/links/abc1234/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://exemplo.com/destino"))
                .andExpect(jsonPath("$.totalClicks").value(42))
                .andExpect(jsonPath("$.uniqueVisitors").value(17))
                .andExpect(jsonPath("$.lastClickAt").value("2026-01-15T09:30:00Z"))
                .andExpect(jsonPath("$.clicksByDay[0].day").value("2026-01-14"))
                .andExpect(jsonPath("$.clicksByDay[0].clicks").value(10))
                .andExpect(jsonPath("$.topReferrers[0].referrer").value("https://google.com"))
                .andExpect(jsonPath("$.topReferrers[0].clicks").value(30));
    }

    @Test
    @DisplayName("retorna 404 quando o codigo nao existe")
    void retorna404QuandoNaoExiste() throws Exception {
        willThrow(new LinkNotFoundException("sumiu12")).given(linkStatsService).statsFor(any());

        mockMvc.perform(get("/api/v1/links/sumiu12/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Link nao encontrado"));
    }
}
