package com.joaoalcantara.encurtador.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joaoalcantara.encurtador.config.ClickTrackingProperties;
import com.joaoalcantara.encurtador.domain.ClickEvent;
import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.domain.LinkStats;
import com.joaoalcantara.encurtador.exception.LinkNotFoundException;
import com.joaoalcantara.encurtador.repository.InMemoryClickRepository;
import com.joaoalcantara.encurtador.repository.InMemoryLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LinkStatsService")
class LinkStatsServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryLinkRepository linkRepository = new InMemoryLinkRepository();
    private final InMemoryClickRepository clickRepository = new InMemoryClickRepository();
    private final ClickTrackingProperties properties = new ClickTrackingProperties(true, "sal", 5, 7);

    private final LinkStatsService service =
            new LinkStatsService(linkRepository, clickRepository, properties, clock);

    private final ClickRecorder recorder = new ClickRecorder(clickRepository, properties);

    /** Devolve o id que o dublê atribuiu, como o banco faria. */
    private Long umLinkChamado(String code) {
        return linkRepository.save(
                new Link(code, "https://exemplo.com", NOW.minusSeconds(86_400), null)).getId();
    }

    private void umCliqueEm(Long linkId, Instant quando, String referrer, String ip) {
        recorder.record(new ClickEvent("abc1234", linkId, quando, referrer, "Mozilla/5.0", ip));
    }

    @Test
    @DisplayName("falha quando o codigo nao existe")
    void falhaQuandoLinkNaoExiste() {
        assertThatThrownBy(() -> service.statsFor("naoexiste"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    @DisplayName("devolve zeros para link que nunca foi acessado")
    void linkSemCliques() {
        umLinkChamado("abc1234");

        LinkStats stats = service.statsFor("abc1234");

        assertThat(stats.totalClicks()).isZero();
        assertThat(stats.uniqueVisitors()).isZero();
        assertThat(stats.lastClickAt()).isNull();
        assertThat(stats.clicksByDay()).isEmpty();
        assertThat(stats.topReferrers()).isEmpty();
    }

    @Test
    @DisplayName("conta total de cliques e visitantes distintos")
    void contaCliquesEVisitantes() {
        Long linkId = umLinkChamado("abc1234");
        umCliqueEm(linkId, NOW, null, "203.0.113.10");
        umCliqueEm(linkId, NOW, null, "203.0.113.10");
        umCliqueEm(linkId, NOW, null, "198.51.100.7");

        LinkStats stats = service.statsFor("abc1234");

        assertThat(stats.totalClicks()).isEqualTo(3);
        assertThat(stats.uniqueVisitors()).isEqualTo(2);
        assertThat(stats.lastClickAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("agrupa cliques por dia, do mais antigo ao mais recente")
    void agrupaPorDia() {
        Long linkId = umLinkChamado("abc1234");
        umCliqueEm(linkId, NOW.minusSeconds(86_400), null, "203.0.113.10");
        umCliqueEm(linkId, NOW, null, "203.0.113.10");
        umCliqueEm(linkId, NOW, null, "198.51.100.7");

        LinkStats stats = service.statsFor("abc1234");

        assertThat(stats.clicksByDay()).containsExactly(
                new com.joaoalcantara.encurtador.domain.DailyClicks(LocalDate.of(2026, 1, 14), 1),
                new com.joaoalcantara.encurtador.domain.DailyClicks(LocalDate.of(2026, 1, 15), 2));
    }

    /**
     * O historico e de 7 dias: um clique de 30 dias atras entra no total, mas
     * nao na serie diaria.
     */
    @Test
    @DisplayName("ignora no historico diario os cliques anteriores a janela")
    void ignoraCliquesForaDaJanela() {
        Long linkId = umLinkChamado("abc1234");
        umCliqueEm(linkId, NOW.minus(java.time.Duration.ofDays(30)), null, "203.0.113.10");
        umCliqueEm(linkId, NOW, null, "203.0.113.10");

        LinkStats stats = service.statsFor("abc1234");

        assertThat(stats.totalClicks()).isEqualTo(2);
        assertThat(stats.clicksByDay()).hasSize(1);
    }

    @Test
    @DisplayName("ordena as origens da mais frequente para a menos")
    void ordenaOrigens() {
        Long linkId = umLinkChamado("abc1234");
        umCliqueEm(linkId, NOW, "https://twitter.com", "203.0.113.10");
        umCliqueEm(linkId, NOW, "https://google.com", "203.0.113.11");
        umCliqueEm(linkId, NOW, "https://google.com", "203.0.113.12");

        LinkStats stats = service.statsFor("abc1234");

        assertThat(stats.topReferrers()).extracting("referrer")
                .containsExactly("https://google.com", "https://twitter.com");
    }

    @Test
    @DisplayName("mantem a estatistica de um link ja expirado")
    void mantemEstatisticaDeLinkExpirado() {
        Long linkId = linkRepository.save(
                new Link("expirou", "https://exemplo.com", NOW.minusSeconds(86_400), NOW.minusSeconds(60))).getId();
        umCliqueEm(linkId, NOW.minusSeconds(120), null, "203.0.113.10");

        LinkStats stats = service.statsFor("expirou");

        assertThat(stats.totalClicks()).isEqualTo(1);
    }
}
