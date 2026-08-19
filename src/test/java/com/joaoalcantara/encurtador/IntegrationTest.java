package com.joaoalcantara.encurtador;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base dos testes de integracao: sobe PostgreSQL e Redis de verdade, em
 * containers, sem depender de nada instalado na maquina.
 *
 * O QUE ISTO RESOLVE
 *
 * Desde a Etapa 3 a suite tinha um teste que so passava se alguem tivesse
 * rodado "docker compose up -d" antes. Isso funciona no computador de quem
 * lembra; nao funciona no de quem acabou de clonar o projeto, e nao funciona na
 * integracao continua da Etapa 9. Aqui os containers sao responsabilidade do
 * proprio teste.
 *
 * POR QUE OS CONTAINERS SAO ESTATICOS E INICIADOS NUM BLOCO static
 *
 * O caminho que os tutoriais mostram e anotar a classe com @Testcontainers e os
 * campos com @Container. Nesse modo o JUnit sobe e derruba os containers a cada
 * CLASSE de teste -- com cinco classes de integracao, sao cinco PostgreSQL
 * subindo e descendo, e a suite fica lenta sem necessidade.
 *
 * Iniciando no bloco static, os containers sobem uma vez por JVM e todas as
 * classes compartilham. Ninguem precisa derruba-los: o Testcontainers deixa um
 * container auxiliar (Ryuk) encarregado de limpar tudo quando o processo morre.
 *
 * O @ServiceConnection dispensa configurar spring.datasource.url e
 * spring.data.redis.host na mao -- o Spring Boot le host e porta do container e
 * injeta sozinho. Os tutoriais mais antigos usam @DynamicPropertySource, que
 * ainda funciona mas exige escrever cada propriedade.
 *
 * As imagens sao as mesmas do docker-compose.yml de proposito: teste que roda
 * numa versao diferente da de producao testa outra coisa.
 */
public abstract class IntegrationTest {

    @ServiceConnection
    // Sem <> : no Testcontainers 2.0 o PostgreSQLContainer deixou de ser generico.
    // Todo tutorial escreve new PostgreSQLContainer<>(...), que agora nao compila.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    /**
     * GenericContainer e nao um container especifico de Redis: o Spring Boot
     * reconhece o servico pelo NOME DA IMAGEM, entao nao ha necessidade de uma
     * dependencia extra so para isso.
     */
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }
}
