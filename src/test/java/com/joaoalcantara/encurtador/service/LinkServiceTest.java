package com.joaoalcantara.encurtador.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.exception.CodeGenerationException;
import com.joaoalcantara.encurtador.exception.InvalidUrlException;
import com.joaoalcantara.encurtador.exception.LinkNotFoundException;
import com.joaoalcantara.encurtador.repository.InMemoryLinkRepository;
import com.joaoalcantara.encurtador.repository.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Teste sem Spring: a classe e instanciada na mao com as dependencias que ela
 * declara. Roda em milissegundos porque nao sobe contexto nenhum.
 */
@DisplayName("LinkService")
class LinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final LinkRepository repository = new InMemoryLinkRepository();

    /** Gerador previsivel: devolve os codigos da fila, na ordem. */
    private static ShortCodeGenerator generatorReturning(String... codes) {
        Deque<String> queue = new ArrayDeque<>(java.util.List.of(codes));
        return () -> queue.size() > 1 ? queue.poll() : queue.peek();
    }

    private LinkService serviceWith(ShortCodeGenerator generator) {
        // O LinkLookup e construido na mao: fora do Spring, a anotacao
        // @Cacheable e inerte, entao o teste exercita a regra sem Redis.
        return new LinkService(repository, new LinkLookup(repository), generator, clock);
    }

    @Test
    @DisplayName("cria link com o codigo gerado e a URL informada")
    void criaLinkComCodigoGerado() {
        LinkService service = serviceWith(generatorReturning("abc1234"));

        Link link = service.create("https://exemplo.com/pagina", null);

        assertThat(link.getCode()).isEqualTo("abc1234");
        assertThat(link.getOriginalUrl()).isEqualTo("https://exemplo.com/pagina");
        assertThat(link.getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("registra a criacao usando o relogio injetado")
    void usaRelogioInjetado() {
        LinkService service = serviceWith(generatorReturning("abc1234"));

        Link link = service.create("https://exemplo.com", null);

        assertThat(link.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("guarda a data de expiracao quando informada")
    void guardaDataDeExpiracao() {
        LinkService service = serviceWith(generatorReturning("abc1234"));
        Instant expiresAt = NOW.plusSeconds(3_600);

        Link link = service.create("https://exemplo.com", expiresAt);

        assertThat(link.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("persiste o link criado no repositorio")
    void persisteLinkCriado() {
        LinkService service = serviceWith(generatorReturning("abc1234"));

        service.create("https://exemplo.com", null);

        assertThat(repository.existsByCode("abc1234")).isTrue();
    }

    @Test
    @DisplayName("remove espacos em volta da URL")
    void removeEspacosDaUrl() {
        LinkService service = serviceWith(generatorReturning("abc1234"));

        Link link = service.create("  https://exemplo.com  ", null);

        assertThat(link.getOriginalUrl()).isEqualTo("https://exemplo.com");
    }

    @Test
    @DisplayName("sorteia outro codigo quando o primeiro ja existe")
    void tentaNovoCodigoQuandoColide() {
        repository.save(new Link("colidiu", "https://ja-existe.com", NOW, null));
        LinkService service = serviceWith(generatorReturning("colidiu", "livre12"));

        Link link = service.create("https://exemplo.com", null);

        assertThat(link.getCode()).isEqualTo("livre12");
    }

    @Test
    @DisplayName("falha quando nao encontra codigo livre nas tentativas disponiveis")
    void falhaAposMaximoDeTentativas() {
        repository.save(new Link("colidiu", "https://ja-existe.com", NOW, null));
        LinkService service = serviceWith(generatorReturning("colidiu"));

        assertThatThrownBy(() -> service.create("https://exemplo.com", null))
                .isInstanceOf(CodeGenerationException.class);
    }

    @ParameterizedTest(name = "rejeita \"{0}\"")
    @DisplayName("rejeita URL invalida")
    @ValueSource(strings = {
            "",
            "   ",
            "exemplo.com",
            "javascript:alert(1)",
            "ftp://exemplo.com/arquivo",
            "http://",
            "http:// espaco.com"
    })
    void rejeitaUrlInvalida(String url) {
        LinkService service = serviceWith(generatorReturning("abc1234"));

        assertThatThrownBy(() -> service.create(url, null))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    @DisplayName("rejeita URL nula")
    void rejeitaUrlNula() {
        LinkService service = serviceWith(generatorReturning("abc1234"));

        assertThatThrownBy(() -> service.create(null, null))
                .isInstanceOf(InvalidUrlException.class);
    }

    // -----------------------------------------------------------------------
    // resolve: o caminho do redirecionamento
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("resolve devolve a URL de destino do codigo")
    void resolveDevolveUrlDeDestino() {
        LinkService service = serviceWith(generatorReturning("abc1234"));
        service.create("https://exemplo.com/destino", null);

        assertThat(service.resolve("abc1234")).isEqualTo("https://exemplo.com/destino");
    }

    @Test
    @DisplayName("resolve falha quando o codigo nao existe")
    void resolveFalhaQuandoCodigoNaoExiste() {
        LinkService service = serviceWith(generatorReturning("abc1234"));

        assertThatThrownBy(() -> service.resolve("naoexiste"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    @DisplayName("resolve funciona para link sem data de expiracao")
    void resolveFuncionaSemExpiracao() {
        LinkService service = serviceWith(generatorReturning("abc1234"));
        service.create("https://exemplo.com", null);

        assertThat(service.resolve("abc1234")).isEqualTo("https://exemplo.com");
    }

    @Test
    @DisplayName("resolve aceita link cuja expiracao ainda nao chegou")
    void resolveAceitaLinkDentroDoPrazo() {
        LinkService service = serviceWith(generatorReturning("abc1234"));
        service.create("https://exemplo.com", NOW.plusSeconds(60));

        assertThat(service.resolve("abc1234")).isEqualTo("https://exemplo.com");
    }

    /**
     * O relogio esta fixo em NOW, entao um link que expirou "um segundo atras"
     * e criado ja vencido -- e a checagem de expiracao do resolve tem que pegar.
     */
    @Test
    @DisplayName("resolve trata link expirado como inexistente")
    void resolveRecusaLinkExpirado() {
        LinkService service = serviceWith(generatorReturning("abc1234"));
        service.create("https://exemplo.com", NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.resolve("abc1234"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    @DisplayName("resolve recusa o link no instante exato da expiracao")
    void resolveRecusaNoInstanteExatoDaExpiracao() {
        LinkService service = serviceWith(generatorReturning("abc1234"));
        service.create("https://exemplo.com", NOW);

        assertThatThrownBy(() -> service.resolve("abc1234"))
                .isInstanceOf(LinkNotFoundException.class);
    }
}
