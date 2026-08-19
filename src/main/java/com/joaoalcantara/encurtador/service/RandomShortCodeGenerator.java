package com.joaoalcantara.encurtador.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Gera codigos aleatorios de 7 caracteres em Base62 (A-Z, a-z, 0-9).
 *
 * Por que aleatorio e nao sequencial: a alternativa classica e converter o id do
 * banco para Base62, o que nunca colide. O problema e que os codigos ficam
 * enumeraveis (quem tem "2" tenta "3") e qualquer pessoa consegue varrer todos
 * os links criados. Aleatorio custa uma consulta a mais para checar colisao, mas
 * nao entrega o acervo de links de graca.
 *
 * SecureRandom e nao Random: Random usa um gerador previsivel a partir da
 * semente, o que permitiria adivinhar os proximos codigos.
 *
 * 62^7 = cerca de 3,5 trilhoes de combinacoes.
 */
@Component
public class RandomShortCodeGenerator implements ShortCodeGenerator {

    static final int CODE_LENGTH = 7;
    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
