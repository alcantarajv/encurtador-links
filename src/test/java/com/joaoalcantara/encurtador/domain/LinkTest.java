package com.joaoalcantara.encurtador.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Link")
class LinkTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    @DisplayName("link sem data de expiracao nunca expira")
    void semExpiracaoNuncaExpira() {
        Link link = new Link("abc1234", "https://exemplo.com", NOW, null);

        assertThat(link.isExpired(NOW.plusSeconds(999_999))).isFalse();
    }

    @Test
    @DisplayName("nao esta expirado antes da data de expiracao")
    void naoExpiradoAntesDaData() {
        Link link = new Link("abc1234", "https://exemplo.com", NOW, NOW.plusSeconds(60));

        assertThat(link.isExpired(NOW.plusSeconds(59))).isFalse();
    }

    @Test
    @DisplayName("esta expirado no instante exato da expiracao")
    void expiradoNoInstanteExato() {
        Instant expiresAt = NOW.plusSeconds(60);
        Link link = new Link("abc1234", "https://exemplo.com", NOW, expiresAt);

        assertThat(link.isExpired(expiresAt)).isTrue();
    }

    @Test
    @DisplayName("esta expirado depois da data de expiracao")
    void expiradoDepoisDaData() {
        Link link = new Link("abc1234", "https://exemplo.com", NOW, NOW.plusSeconds(60));

        assertThat(link.isExpired(NOW.plusSeconds(61))).isTrue();
    }

    @Test
    @DisplayName("nao permite construir sem codigo")
    void rejeitaCodigoNulo() {
        assertThatThrownBy(() -> new Link(null, "https://exemplo.com", NOW, null))
                .isInstanceOf(NullPointerException.class);
    }
}
