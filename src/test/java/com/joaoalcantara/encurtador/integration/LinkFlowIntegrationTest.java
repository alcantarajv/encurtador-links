package com.joaoalcantara.encurtador.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.joaoalcantara.encurtador.IntegrationTest;
import com.joaoalcantara.encurtador.dto.LinkResponse;
import com.joaoalcantara.encurtador.dto.LinkStatsResponse;
import com.joaoalcantara.encurtador.repository.SpringDataClickRepository;
import com.joaoalcantara.encurtador.repository.SpringDataLinkRepository;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * A jornada completa por HTTP de verdade: criar, redirecionar e consultar
 * estatisticas, com PostgreSQL e Redis reais.
 *
 * E o unico teste que exercita o registro assincrono de ponta a ponta. Nos
 * testes de unidade o @Async e inerte e o metodo roda na propria thread; aqui a
 * gravacao acontece de fato em outra thread, depois que a resposta ja foi
 * enviada -- por isso a conferencia usa espera ativa em vez de ler o resultado
 * na linha seguinte.
 *
 * O rate limiting fica desligado: ele tem teste proprio, e aqui so atrapalharia.
 *
 * ARMADILHAS DE VERSAO (Spring Boot 4) NESTA CLASSE
 *
 * 1. O TestRestTemplate mudou de pacote: agora e
 *    org.springframework.boot.resttestclient.TestRestTemplate, e nao mais
 *    org.springframework.boot.test.web.client.
 * 2. Ele deixou de ser configurado automaticamente por
 *    @SpringBootTest(webEnvironment = RANDOM_PORT). Sem o
 *    @AutoConfigureTestRestTemplate abaixo, a injecao falha dizendo que nao ha
 *    bean do tipo -- mensagem que nao sugere em nada qual e a causa.
 * 3. Ele depende do RestTemplateBuilder, que vive no modulo
 *    spring-boot-restclient. Como a aplicacao nao faz chamadas HTTP de saida,
 *    esse modulo precisou entrar no pom com escopo de teste.
 * 4. O cliente agora SEGUE redirecionamentos por padrao. Num teste de
 *    encurtador isso e desastroso: o 302 vira 200 da pagina de destino, e a
 *    asercao mais importante do projeto passa a testar o site do Spring. A
 *    correcao e o withRedirects(DONT_FOLLOW) no setUp.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "shortener.rate-limit.enabled=false")
@AutoConfigureTestRestTemplate
@DisplayName("Jornada completa do link")
class LinkFlowIntegrationTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate restAutoConfigurado;

    /** O mesmo cliente, mas sem seguir redirecionamento -- ver ponto 4 acima. */
    private TestRestTemplate rest;

    @Autowired
    private SpringDataLinkRepository springDataLinkRepository;

    @Autowired
    private SpringDataClickRepository springDataClickRepository;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void limpaTudo() {
        rest = restAutoConfigurado.withRedirects(HttpRedirects.DONT_FOLLOW);

        springDataClickRepository.deleteAll();
        springDataLinkRepository.deleteAll();

        Set<String> chaves = redis.keys("*");
        if (chaves != null && !chaves.isEmpty()) {
            redis.delete(chaves);
        }
    }

    private String criaLink(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<LinkResponse> resposta = rest.exchange(
                "/api/v1/links",
                HttpMethod.POST,
                new HttpEntity<>("{\"originalUrl\": \"" + url + "\"}", headers),
                LinkResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().code();
    }

    private ResponseEntity<Void> acessa(String code, String ip, String referrer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", ip);
        if (referrer != null) {
            headers.set(HttpHeaders.REFERER, referrer);
        }
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (teste de integracao)");

        return rest.exchange("/" + code, HttpMethod.GET, new HttpEntity<>(headers), Void.class);
    }

    private LinkStatsResponse estatisticas(String code) {
        return rest.getForObject("/api/v1/links/" + code + "/stats", LinkStatsResponse.class);
    }

    @Test
    @DisplayName("cria, redireciona e contabiliza o acesso")
    void jornadaCompleta() {
        String code = criaLink("https://spring.io/guides");

        ResponseEntity<Void> redirecionamento = acessa(code, "203.0.113.10", "https://google.com");

        assertThat(redirecionamento.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirecionamento.getHeaders().getLocation())
                .hasToString("https://spring.io/guides");
        assertThat(redirecionamento.getHeaders().getCacheControl()).isEqualTo("no-store");

        // O clique e gravado em outra thread: espera ate aparecer, em vez de
        // supor que ja apareceu.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(estatisticas(code).totalClicks()).isEqualTo(1));

        LinkStatsResponse stats = estatisticas(code);
        assertThat(stats.originalUrl()).isEqualTo("https://spring.io/guides");
        assertThat(stats.uniqueVisitors()).isEqualTo(1);
        assertThat(stats.lastClickAt()).isNotNull();
        assertThat(stats.topReferrers()).singleElement()
                .satisfies(origem -> assertThat(origem.referrer()).isEqualTo("https://google.com"));
    }

    @Test
    @DisplayName("conta visitantes distintos pelo IP de origem")
    void contaVisitantesDistintos() {
        String code = criaLink("https://exemplo.com/popular");

        acessa(code, "203.0.113.10", "https://google.com");
        acessa(code, "203.0.113.10", "https://google.com");
        acessa(code, "198.51.100.7", "https://twitter.com");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(estatisticas(code).totalClicks()).isEqualTo(3));

        LinkStatsResponse stats = estatisticas(code);
        assertThat(stats.uniqueVisitors()).isEqualTo(2);
        assertThat(stats.topReferrers()).extracting("referrer")
                .containsExactly("https://google.com", "https://twitter.com");
    }

    /**
     * Confirma que o cabecalho X-Forwarded-For chega ate o registro do clique --
     * ou seja, que o server.forward-headers-strategy esta de fato ativo. Sem
     * isso, todos os acessos seriam contados como um unico visitante (o proxy).
     */
    @Test
    @DisplayName("registra hashes diferentes para IPs encaminhados diferentes")
    void ipEncaminhadoChegaAoRegistro() {
        String code = criaLink("https://exemplo.com/proxy");

        acessa(code, "203.0.113.10", null);
        acessa(code, "198.51.100.7", null);
        acessa(code, "192.0.2.44", null);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(estatisticas(code).uniqueVisitors()).isEqualTo(3));
    }

    @Test
    @DisplayName("responde 404 para codigo que nao existe")
    void codigoInexistente() {
        ResponseEntity<Void> resposta = acessa("naoexi9", "203.0.113.10", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("nao registra clique para codigo que nao existe")
    void codigoInexistenteNaoGeraClique() {
        acessa("naoexi9", "203.0.113.10", null);

        assertThat(springDataClickRepository.count()).isZero();
    }

    @Test
    @DisplayName("recusa URL invalida na criacao")
    void recusaUrlInvalida() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resposta = rest.exchange(
                "/api/v1/links",
                HttpMethod.POST,
                new HttpEntity<>("{\"originalUrl\": \"javascript:alert(1)\"}", headers),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(springDataLinkRepository.count()).isZero();
    }
}
