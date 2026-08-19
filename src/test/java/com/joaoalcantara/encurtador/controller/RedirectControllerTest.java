package com.joaoalcantara.encurtador.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.joaoalcantara.encurtador.exception.LinkNotFoundException;
import com.joaoalcantara.encurtador.service.LinkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
@DisplayName("GET /{code}")
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LinkService linkService;

    @Test
    @DisplayName("redireciona com 302 para a URL de destino")
    void redirecionaComFound() throws Exception {
        given(linkService.resolve("abc1234")).willReturn("https://exemplo.com/destino");

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://exemplo.com/destino"));
    }

    /**
     * O no-store e o que mantem o contador de cliques da Etapa 6 honesto: sem
     * ele, um proxy no meio do caminho poderia responder sozinho e o servico
     * nunca ficaria sabendo do acesso.
     */
    @Test
    @DisplayName("instrui o navegador a nao cachear o redirecionamento")
    void naoPermiteCacheDoRedirecionamento() throws Exception {
        given(linkService.resolve(any())).willReturn("https://exemplo.com");

        mockMvc.perform(get("/abc1234"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("retorna 404 quando o codigo nao existe ou expirou")
    void retorna404QuandoNaoEncontrado() throws Exception {
        willThrow(new LinkNotFoundException("sumiu12")).given(linkService).resolve("sumiu12");

        mockMvc.perform(get("/sumiu12"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Link nao encontrado"))
                .andExpect(jsonPath("$.detail").value("link nao encontrado ou expirado"));
    }

    /**
     * A expressao regular do mapeamento e o que impede o servico de ser chamado
     * para caminhos que obviamente nao sao codigo curto.
     */
    @Test
    @DisplayName("nao trata como codigo curto um caminho que nao casa com o padrao")
    void ignoraCaminhoQueNaoEhCodigo() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound());

        verify(linkService, never()).resolve(any());
    }

    @Test
    @DisplayName("nao trata como codigo curto um caminho curto demais")
    void ignoraCaminhoCurtoDemais() throws Exception {
        mockMvc.perform(get("/ab"))
                .andExpect(status().isNotFound());

        verify(linkService, never()).resolve(any());
    }
}
