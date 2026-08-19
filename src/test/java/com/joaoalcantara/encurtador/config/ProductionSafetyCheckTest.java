package com.joaoalcantara.encurtador.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * As verificacoes de producao, sem subir contexto do Spring.
 *
 * A classe existe justamente para derrubar a aplicacao, entao o que interessa
 * testar e o oposto do de costume: quais configuracoes precisam impedir a subida
 * e quais precisam deixar passar.
 */
@DisplayName("ProductionSafetyCheck")
class ProductionSafetyCheckTest {

    private static final String URL_PUBLICA = "https://encurtador.exemplo.com";
    private static final String SAL_FORTE = "K7pQm2xR9tLvN4wZ8bY3cA6dE1fG5hJ0";

    private ProductionSafetyCheck check(String baseUrl, boolean trackingLigado, String sal) {
        return new ProductionSafetyCheck(
                new ShortenerProperties(baseUrl),
                new ClickTrackingProperties(trackingLigado, sal, 5, 7));
    }

    @Test
    @DisplayName("aceita uma configuracao de producao completa")
    void configuracaoValida() {
        assertThatCode(() -> check(URL_PUBLICA, true, SAL_FORTE).check())
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Sal do hash de IP
    // -----------------------------------------------------------------------

    /**
     * O caso que motivou a classe: o valor esta publicado neste repositorio, e
     * com ele o hash do IP nao protege ninguem.
     */
    @Test
    @DisplayName("recusa o sal de desenvolvimento")
    void recusaSalDeDesenvolvimento() {
        assertThatThrownBy(() -> check(URL_PUBLICA, true, ProductionSafetyCheck.DEV_IP_SALT).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLICK_IP_SALT");
    }

    @Test
    @DisplayName("recusa sal em branco")
    void recusaSalEmBranco() {
        assertThatThrownBy(() -> check(URL_PUBLICA, true, "   ").check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLICK_IP_SALT");
    }

    @Test
    @DisplayName("recusa sal curto demais para resistir a forca bruta")
    void recusaSalCurto() {
        String curto = "a".repeat(ProductionSafetyCheck.MIN_SALT_LENGTH - 1);

        assertThatThrownBy(() -> check(URL_PUBLICA, true, curto).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("caracteres");
    }

    @Test
    @DisplayName("aceita sal com exatamente o tamanho minimo")
    void aceitaSalNoLimite() {
        String noLimite = "a".repeat(ProductionSafetyCheck.MIN_SALT_LENGTH);

        assertThatCode(() -> check(URL_PUBLICA, true, noLimite).check())
                .doesNotThrowAnyException();
    }

    /**
     * Sem registro de cliques nenhum IP e processado, entao exigir sal forte
     * so impediria um deploy que nao tem o problema.
     */
    @Test
    @DisplayName("ignora o sal quando o registro de cliques esta desligado")
    void ignoraSalComTrackingDesligado() {
        assertThatCode(() -> check(URL_PUBLICA, false, ProductionSafetyCheck.DEV_IP_SALT).check())
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Endereco publico
    // -----------------------------------------------------------------------

    /**
     * Sem esta verificacao a aplicacao sobe saudavel e devolve
     * "http://localhost:8080/abc1234" para todo mundo -- links inuteis, e nada
     * no log denunciando.
     */
    @Test
    @DisplayName("recusa base-url apontando para localhost")
    void recusaBaseUrlLocal() {
        assertThatThrownBy(() -> check("http://localhost:8080", true, SAL_FORTE).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHORTENER_BASE_URL");
    }

    @Test
    @DisplayName("recusa base-url apontando para 127.0.0.1")
    void recusaBaseUrlLoopback() {
        assertThatThrownBy(() -> check("http://127.0.0.1:8080", true, SAL_FORTE).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHORTENER_BASE_URL");
    }

    @Test
    @DisplayName("recusa base-url em branco")
    void recusaBaseUrlEmBranco() {
        assertThatThrownBy(() -> check("", true, SAL_FORTE).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHORTENER_BASE_URL");
    }

    /**
     * HTTP em producao merece aviso, nao queda: um dominio proprio recem
     * apontado pode passar um tempo sem certificado, e derrubar a aplicacao por
     * isso seria pior do que servir em texto claro por alguns minutos.
     */
    @Test
    @DisplayName("aceita base-url sem HTTPS, apenas avisando")
    void aceitaBaseUrlSemHttps() {
        assertThatCode(() -> check("http://encurtador.exemplo.com", true, SAL_FORTE).check())
                .doesNotThrowAnyException();
    }
}
