package com.joaoalcantara.encurtador.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joaoalcantara.encurtador.config.ShortenerProperties;
import com.joaoalcantara.encurtador.domain.Link;
import com.joaoalcantara.encurtador.exception.InvalidUrlException;
import com.joaoalcantara.encurtador.service.LinkService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste de fatia web: sobe apenas o controller, a serializacao JSON, a
 * validacao e o tratador de excecoes -- nada de servico ou repositorio reais.
 *
 * Armadilha de versao (Spring Boot 4):
 *  - @WebMvcTest mudou de pacote: org.springframework.boot.webmvc.test.autoconfigure
 *    (os tutoriais mostram org.springframework.boot.test.autoconfigure.web.servlet)
 *  - @MockBean foi removido; o substituto e @MockitoBean, do spring-test
 */
@WebMvcTest(LinkController.class)
@EnableConfigurationProperties(ShortenerProperties.class)
@TestPropertySource(properties = "shortener.base-url=http://localhost:8080")
@DisplayName("POST /api/v1/links")
class LinkControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LinkService linkService;

    @Test
    @DisplayName("retorna 201 com o link criado e o header Location")
    void criaLinkComSucesso() throws Exception {
        given(linkService.create(eq("https://exemplo.com/pagina"), any()))
                .willReturn(new Link("abc1234", "https://exemplo.com/pagina", NOW, null));

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl": "https://exemplo.com/pagina"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/abc1234"))
                .andExpect(jsonPath("$.code").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://exemplo.com/pagina"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    @DisplayName("retorna 400 quando a URL nao foi informada")
    void rejeitaUrlAusente() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"))
                .andExpect(jsonPath("$.errors.originalUrl").value(hasItem("a URL e obrigatoria")));
    }

    @Test
    @DisplayName("retorna 400 quando a URL nao usa http ou https")
    void rejeitaUrlSemProtocoloValido() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl": "ftp://exemplo.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.originalUrl").value(hasItem("a URL precisa comecar com http:// ou https://")));
    }

    @Test
    @DisplayName("retorna 400 quando a data de expiracao esta no passado")
    void rejeitaExpiracaoNoPassado() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl": "https://exemplo.com", "expiresAt": "2020-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expiresAt").value(hasItem("a data de expiracao precisa estar no futuro")));
    }

    @Test
    @DisplayName("retorna 400 quando o corpo nao e um JSON valido")
    void rejeitaJsonMalformado() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"));
    }

    @Test
    @DisplayName("retorna 400 quando o servico recusa a URL")
    void rejeitaUrlRecusadaPeloServico() throws Exception {
        willThrow(new InvalidUrlException("a URL precisa conter um dominio valido"))
                .given(linkService).create(any(), any());

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl": "https://exemplo.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("URL invalida"))
                .andExpect(jsonPath("$.detail").value("a URL precisa conter um dominio valido"));
    }
}
