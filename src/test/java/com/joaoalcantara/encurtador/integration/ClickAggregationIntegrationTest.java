package com.joaoalcantara.encurtador.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.joaoalcantara.encurtador.IntegrationTest;
import com.joaoalcantara.encurtador.domain.DailyClicks;
import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.repository.ClickRepository;
import com.joaoalcantara.encurtador.repository.SpringDataClickRepository;
import com.joaoalcantara.encurtador.repository.SpringDataLinkRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * As consultas de agregacao contra um PostgreSQL de verdade.
 *
 * POR QUE ESTE E O TESTE MAIS IMPORTANTE DA ETAPA
 *
 * Ate aqui, tudo o que envolvia SQL estava verificado apenas na mao. O
 * InMemoryClickRepository dos testes de unidade reescreve as agregacoes em Java
 * -- e nada garantia que as duas implementacoes concordassem. O date_trunc, o
 * count distinto, a ordenacao e o limite do top de origens nunca tinham sido
 * executados por um teste.
 *
 * Nao usa @Transactional de proposito: a anotacao faria cada teste rodar dentro
 * de uma transacao revertida no fim, e as consultas nativas poderiam nao
 * enxergar dados ainda nao gravados. Aqui os dados sao gravados de verdade e a
 * limpeza e explicita.
 */
@SpringBootTest
@DisplayName("Agregacoes de clique no PostgreSQL")
class ClickAggregationIntegrationTest extends IntegrationTest {

    @Autowired
    private ClickRepository clickRepository;

    @Autowired
    private SpringDataClickRepository springDataClickRepository;

    @Autowired
    private SpringDataLinkRepository springDataLinkRepository;

    private Long linkId;

    @BeforeEach
    void limpaEPreparaUmLink() {
        springDataClickRepository.deleteAll();
        springDataLinkRepository.deleteAll();

        linkId = springDataLinkRepository.save(
                new Link("abc1234", "https://exemplo.com", Instant.parse("2026-01-01T00:00:00Z"), null)).getId();
    }

    private void clique(Instant quando, String referrer, String ipHash) {
        clickRepository.save(linkId, quando, referrer, "Mozilla/5.0", ipHash);
    }

    @Test
    @DisplayName("conta os cliques do link")
    void contaCliques() {
        clique(Instant.parse("2026-01-15T10:00:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T11:00:00Z"), null, "hash-b");

        assertThat(clickRepository.countByLinkId(linkId)).isEqualTo(2);
    }

    @Test
    @DisplayName("conta visitantes distintos pelo hash do IP")
    void contaVisitantesDistintos() {
        clique(Instant.parse("2026-01-15T10:00:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T10:05:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T10:10:00Z"), null, "hash-b");

        assertThat(clickRepository.countByLinkId(linkId)).isEqualTo(3);
        assertThat(clickRepository.countDistinctVisitorsByLinkId(linkId)).isEqualTo(2);
    }

    @Test
    @DisplayName("ignora hash nulo na contagem de visitantes")
    void ignoraHashNuloNosVisitantes() {
        clique(Instant.parse("2026-01-15T10:00:00Z"), null, null);
        clique(Instant.parse("2026-01-15T10:05:00Z"), null, "hash-a");

        assertThat(clickRepository.countDistinctVisitorsByLinkId(linkId)).isEqualTo(1);
    }

    @Test
    @DisplayName("devolve nulo como ultimo acesso quando nao ha cliques")
    void ultimoAcessoNuloSemCliques() {
        assertThat(clickRepository.findLastClickAt(linkId)).isNull();
    }

    @Test
    @DisplayName("devolve o instante do acesso mais recente")
    void ultimoAcesso() {
        clique(Instant.parse("2026-01-15T10:00:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T18:30:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T12:00:00Z"), null, "hash-a");

        assertThat(clickRepository.findLastClickAt(linkId))
                .isEqualTo(Instant.parse("2026-01-15T18:30:00Z"));
    }

    @Test
    @DisplayName("agrupa por dia, do mais antigo ao mais recente")
    void agrupaPorDia() {
        clique(Instant.parse("2026-01-14T10:00:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T09:00:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T20:00:00Z"), null, "hash-b");

        assertThat(clickRepository.countByDaySince(linkId, Instant.parse("2026-01-01T00:00:00Z")))
                .containsExactly(
                        new DailyClicks(LocalDate.of(2026, 1, 14), 1),
                        new DailyClicks(LocalDate.of(2026, 1, 15), 2));
    }

    /**
     * O TESTE QUE JUSTIFICA A ETAPA INTEIRA.
     *
     * O date_trunc sobre timestamptz converte para o fuso da SESSAO do banco
     * antes de truncar. Se a sessao nao estiver em UTC, um clique as 23:30Z e
     * outro as 00:30Z do dia seguinte podem cair no mesmo dia -- ou em dias
     * trocados. Nenhum teste de unidade pega isso, porque o dublê em memoria
     * agrupa em Java, sempre em UTC.
     */
    @Test
    @DisplayName("respeita a virada do dia em UTC")
    void respeitaViradaDoDiaEmUtc() {
        clique(Instant.parse("2026-01-14T23:30:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T00:30:00Z"), null, "hash-b");

        assertThat(clickRepository.countByDaySince(linkId, Instant.parse("2026-01-01T00:00:00Z")))
                .containsExactly(
                        new DailyClicks(LocalDate.of(2026, 1, 14), 1),
                        new DailyClicks(LocalDate.of(2026, 1, 15), 1));
    }

    @Test
    @DisplayName("ignora cliques anteriores a data de corte")
    void ignoraCliquesAnterioresAoCorte() {
        clique(Instant.parse("2026-01-01T10:00:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T10:00:00Z"), null, "hash-a");

        assertThat(clickRepository.countByDaySince(linkId, Instant.parse("2026-01-10T00:00:00Z")))
                .containsExactly(new DailyClicks(LocalDate.of(2026, 1, 15), 1));
    }

    @Test
    @DisplayName("ordena origens da mais frequente para a menos e respeita o limite")
    void ordenaOrigensERespeitaLimite() {
        clique(Instant.parse("2026-01-15T10:00:00Z"), "https://google.com", "hash-a");
        clique(Instant.parse("2026-01-15T10:01:00Z"), "https://google.com", "hash-b");
        clique(Instant.parse("2026-01-15T10:02:00Z"), "https://google.com", "hash-c");
        clique(Instant.parse("2026-01-15T10:03:00Z"), "https://twitter.com", "hash-a");
        clique(Instant.parse("2026-01-15T10:04:00Z"), "https://twitter.com", "hash-b");
        clique(Instant.parse("2026-01-15T10:05:00Z"), "https://reddit.com", "hash-a");

        assertThat(clickRepository.findTopReferrers(linkId, 5)).extracting("referrer")
                .containsExactly("https://google.com", "https://twitter.com", "https://reddit.com");

        assertThat(clickRepository.findTopReferrers(linkId, 2)).extracting("referrer")
                .containsExactly("https://google.com", "https://twitter.com");
    }

    @Test
    @DisplayName("deixa de fora os acessos sem origem declarada")
    void ignoraAcessosSemOrigem() {
        clique(Instant.parse("2026-01-15T10:00:00Z"), null, "hash-a");
        clique(Instant.parse("2026-01-15T10:01:00Z"), "https://google.com", "hash-b");

        assertThat(clickRepository.findTopReferrers(linkId, 5)).hasSize(1);
    }

    /**
     * A chave estrangeira tem ON DELETE CASCADE: apagar o link leva os cliques
     * junto, em vez de deixar linha orfa ou estourar erro de integridade.
     */
    @Test
    @DisplayName("apaga os cliques junto com o link")
    void apagaCliquesComOLink() {
        clique(Instant.parse("2026-01-15T10:00:00Z"), null, "hash-a");
        assertThat(springDataClickRepository.count()).isEqualTo(1);

        springDataLinkRepository.deleteById(linkId);

        assertThat(springDataClickRepository.count()).isZero();
    }
}
