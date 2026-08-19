package com.joaoalcantara.encurtador.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RandomShortCodeGenerator")
class RandomShortCodeGeneratorTest {

    private final RandomShortCodeGenerator generator = new RandomShortCodeGenerator();

    @Test
    @DisplayName("gera codigo com o tamanho configurado")
    void geraCodigoComTamanhoCorreto() {
        assertThat(generator.generate()).hasSize(RandomShortCodeGenerator.CODE_LENGTH);
    }

    @Test
    @DisplayName("usa apenas caracteres do alfabeto Base62")
    void usaApenasAlfabetoBase62() {
        String code = generator.generate();

        assertThat(code).matches("[A-Za-z0-9]+");
    }

    /**
     * Nao prova ausencia de colisao (nada prova), mas denuncia um gerador
     * quebrado que devolvesse sempre o mesmo valor ou variasse pouco.
     */
    @Test
    @DisplayName("gera codigos distintos em sequencia")
    void geraCodigosDistintos() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.generate());
        }

        assertThat(codes).hasSize(1_000);
    }
}
