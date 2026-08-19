package com.joaoalcantara.encurtador;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sobe a aplicacao inteira e verifica que todos os beans conseguem ser criados.
 *
 * Parece um teste bobo, e nao e: ele pega erro de configuracao que nenhum teste
 * de unidade alcanca -- bean duplicado, dependencia circular, propriedade
 * obrigatoria faltando, migration do Flyway invalida, entidade que nao bate com
 * a tabela.
 *
 * Ate a Etapa 6 este era o unico teste que exigia infraestrutura ligada a mao.
 * Agora ele herda os containers do IntegrationTest e roda em qualquer maquina
 * que tenha Docker.
 */
@SpringBootTest
@DisplayName("Contexto da aplicacao")
class EncurtadorLinksApplicationTests extends IntegrationTest {

	@Test
	@DisplayName("sobe com todos os beans e migrations aplicadas")
	void contextLoads() {
	}

}
