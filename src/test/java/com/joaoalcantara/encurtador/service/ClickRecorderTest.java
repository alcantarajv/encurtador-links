package com.joaoalcantara.encurtador.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.joaoalcantara.encurtador.config.ClickTrackingProperties;
import com.joaoalcantara.encurtador.domain.ClickEvent;
import com.joaoalcantara.encurtador.repository.InMemoryClickRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fora do Spring o @Async e inerte: o metodo roda na propria thread do teste.
 * Isso e conveniente aqui -- da para testar O QUE e gravado sem lidar com
 * espera de thread. Que a gravacao realmente acontece em outra thread e
 * comportamento do framework, verificado com a aplicacao no ar.
 */
@DisplayName("ClickRecorder")
class ClickRecorderTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final InMemoryClickRepository repository = new InMemoryClickRepository();

    private ClickRecorder recorderWith(boolean enabled, String salt) {
        return new ClickRecorder(repository, new ClickTrackingProperties(enabled, salt, 5, 7));
    }

    private ClickEvent eventFrom(String ip, String referrer, String userAgent) {
        return new ClickEvent("abc1234", 1L, NOW, referrer, userAgent, ip);
    }

    @Test
    @DisplayName("grava o clique do link informado")
    void gravaClique() {
        ClickRecorder recorder = recorderWith(true, "sal");

        recorder.record(eventFrom("203.0.113.10", "https://origem.com", "Mozilla/5.0"));

        assertThat(repository.countByLinkId(1L)).isEqualTo(1);
        assertThat(repository.findLastClickAt(1L)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("nao grava nada quando o rastreio esta desligado")
    void naoGravaQuandoDesligado() {
        ClickRecorder recorder = recorderWith(false, "sal");

        recorder.record(eventFrom("203.0.113.10", null, null));

        assertThat(repository.countByLinkId(1L)).isZero();
    }

    @Test
    @DisplayName("aceita clique sem referrer e sem user agent")
    void aceitaCliqueSemCabecalhos() {
        ClickRecorder recorder = recorderWith(true, "sal");

        recorder.record(eventFrom("203.0.113.10", null, null));

        assertThat(repository.countByLinkId(1L)).isEqualTo(1);
        assertThat(repository.findTopReferrers(1L, 5)).isEmpty();
    }

    /**
     * O mesmo IP tem que produzir sempre o mesmo hash, senao a contagem de
     * visitantes distintos nao funciona.
     */
    @Test
    @DisplayName("conta o mesmo IP como um unico visitante")
    void mesmoIpContaComoUmVisitante() {
        ClickRecorder recorder = recorderWith(true, "sal");

        recorder.record(eventFrom("203.0.113.10", null, null));
        recorder.record(eventFrom("203.0.113.10", null, null));
        recorder.record(eventFrom("198.51.100.7", null, null));

        assertThat(repository.countByLinkId(1L)).isEqualTo(3);
        assertThat(repository.countDistinctVisitorsByLinkId(1L)).isEqualTo(2);
    }

    /**
     * Este e o teste que garante que o IP nao vai parar no banco em texto claro.
     */
    @Test
    @DisplayName("nunca grava o IP, apenas o hash")
    void nuncaGravaOIpEmTextoClaro() {
        ClickRecorder recorder = recorderWith(true, "sal");

        recorder.record(eventFrom("203.0.113.10", null, null));

        assertThat(repository.storedIpHashes()).hasSize(1);
        assertThat(repository.storedIpHashes().getFirst())
                .doesNotContain("203.0.113.10")
                .hasSize(64);
    }

    /**
     * Sal diferente, hash diferente -- e o que impede alguem de reconhecer os
     * IPs comparando com uma tabela de hashes pre-calculada.
     */
    @Test
    @DisplayName("produz hashes diferentes para sais diferentes")
    void salMudaOHash() {
        InMemoryClickRepository outroRepositorio = new InMemoryClickRepository();

        recorderWith(true, "sal-a").record(eventFrom("203.0.113.10", null, null));
        new ClickRecorder(outroRepositorio, new ClickTrackingProperties(true, "sal-b", 5, 7))
                .record(eventFrom("203.0.113.10", null, null));

        assertThat(repository.storedIpHashes().getFirst())
                .isNotEqualTo(outroRepositorio.storedIpHashes().getFirst());
    }

    /**
     * Cabecalho nao tem limite no protocolo HTTP, mas a coluna tem. Cortar e
     * melhor do que perder o registro inteiro por estouro de coluna.
     */
    @Test
    @DisplayName("corta user agent maior que a coluna")
    void cortaUserAgentGigante() {
        ClickRecorder recorder = recorderWith(true, "sal");

        recorder.record(eventFrom("203.0.113.10", null, "x".repeat(1000)));

        assertThat(repository.storedUserAgents().getFirst()).hasSize(512);
    }
}
