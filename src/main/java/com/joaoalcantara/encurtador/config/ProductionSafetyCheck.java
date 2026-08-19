package com.joaoalcantara.encurtador.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Verificacoes que so fazem sentido num deploy de verdade, executadas na subida
 * da aplicacao.
 *
 * Todas as configuracoes sensiveis deste projeto tem valor padrao, para que
 * alguem que acabou de clonar o repositorio consiga rodar sem preencher nada.
 * Esse mesmo padrao vira armadilha em producao: a aplicacao sobe, responde 200 e
 * parece saudavel enquanto faz a coisa errada em silencio.
 *
 * Os dois casos cobertos aqui ja aconteceram com gente experiente:
 *
 * 1. O sal de desenvolvimento fica em producao. Como ele esta publicado neste
 *    repositorio, qualquer pessoa pode calcular o SHA-256 dos 4 bilhoes de IPv4
 *    e descobrir, a partir do banco, quais IPs visitaram quais links. O hash
 *    deixa de proteger qualquer coisa.
 *
 * 2. A SHORTENER_BASE_URL nao e configurada. A aplicacao funciona perfeitamente
 *    -- e devolve "http://localhost:8080/abc1234" para todo mundo. Cada link
 *    criado e inutil, e nada no log denuncia isso.
 *
 * Em vez de descobrir depois, a aplicacao se recusa a subir. Falha barulhenta na
 * hora do deploy custa minutos; falha silenciosa custa o tempo entre o deploy e
 * a hora em que alguem repara.
 *
 * O @Profile("prod") mantem tudo isto fora do caminho no desenvolvimento e nos
 * testes, onde os valores padrao sao exatamente o que se quer.
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);

    /** O mesmo valor que aparece no application.properties e no .env.example. */
    static final String DEV_IP_SALT = "sal-de-desenvolvimento-trocar-em-producao";

    /**
     * Um sal curto pode ser quebrado por forca bruta junto com o IP. Nao ha
     * numero magico aqui: 16 caracteres ja tornam a busca inviavel, e o
     * "openssl rand -base64 32" sugerido no .env.example gera 44.
     */
    static final int MIN_SALT_LENGTH = 16;

    private final ShortenerProperties shortener;
    private final ClickTrackingProperties clickTracking;

    public ProductionSafetyCheck(ShortenerProperties shortener, ClickTrackingProperties clickTracking) {
        this.shortener = shortener;
        this.clickTracking = clickTracking;
    }

    /**
     * O metodo e separado do @PostConstruct para poder ser chamado direto do
     * teste, sem subir contexto do Spring.
     */
    @PostConstruct
    void checkOnStartup() {
        check();
    }

    void check() {
        checkIpSalt();
        checkBaseUrl();
    }

    private void checkIpSalt() {
        // A verificacao do sal so faz sentido se o registro de cliques estiver
        // ligado -- sem ele, nenhum IP e processado.
        if (!clickTracking.enabled()) {
            return;
        }

        String salt = clickTracking.ipSalt();

        if (salt == null || salt.isBlank()) {
            throw new IllegalStateException(
                    "CLICK_IP_SALT nao foi definida. Gere um valor com 'openssl rand -base64 32'.");
        }

        if (DEV_IP_SALT.equals(salt)) {
            throw new IllegalStateException(
                    "CLICK_IP_SALT ainda e o valor de desenvolvimento, que esta publicado no "
                            + "repositorio -- com ele, o hash do IP nao protege ninguem. "
                            + "Gere um valor com 'openssl rand -base64 32'.");
        }

        if (salt.length() < MIN_SALT_LENGTH) {
            throw new IllegalStateException(
                    "CLICK_IP_SALT tem apenas " + salt.length() + " caracteres; o minimo e "
                            + MIN_SALT_LENGTH + ". Gere um valor com 'openssl rand -base64 32'.");
        }
    }

    private void checkBaseUrl() {
        String baseUrl = shortener.baseUrl();

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "SHORTENER_BASE_URL nao foi definida. Use o endereco publico do servico, "
                            + "por exemplo https://encurtador.exemplo.com");
        }

        if (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) {
            throw new IllegalStateException(
                    "SHORTENER_BASE_URL aponta para " + baseUrl + ", entao toda URL curta "
                            + "devolvida pela API seria inutil fora desta maquina. Use o endereco "
                            + "publico do servico.");
        }

        // Aviso, e nao erro: as tres plataformas do roadmap entregam HTTPS por
        // padrao, mas um dominio proprio recem-apontado pode passar um tempo
        // sem certificado, e derrubar a aplicacao por isso seria pior.
        if (!baseUrl.startsWith("https://")) {
            log.warn("SHORTENER_BASE_URL nao usa HTTPS ({}). As URLs curtas serao servidas "
                    + "em texto claro.", baseUrl);
        }
    }
}
