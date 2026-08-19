# Encurtador de Links

API REST para encurtamento de links com redirecionamento de baixa latência, controle de abuso e coleta de métricas de acesso.

> Projeto em desenvolvimento. Este README é atualizado a cada etapa concluída.

## Sobre o projeto

Encurtar uma URL é simples. O que torna o problema interessante é o que acontece depois: o endpoint de redirecionamento é o caminho mais quente da aplicação — ele precisa responder em poucos milissegundos, resistir a picos de tráfego e registrar dados de acesso sem atrasar a resposta do usuário.

Este projeto foi construído com foco nesses pontos, e não apenas no CRUD de links.

## Funcionalidades

- [x] Criação de links curtos com código aleatório em Base62 e verificação de colisão
- [x] Validação de entrada com respostas de erro no formato Problem Details (RFC 9457)
- [x] Persistência em PostgreSQL com migrations versionadas (Flyway)
- [x] Redirecionamento HTTP com cache em memória distribuída (Redis)
- [x] Expiração opcional de links
- [ ] Rate limiting por IP para prevenir abuso na criação de links
- [ ] Registro assíncrono de cliques (data/hora, referrer, user agent, país)
- [ ] Endpoint de estatísticas agregadas por link
- [ ] Documentação interativa da API via OpenAPI/Swagger

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (LTS) |
| Framework | Spring Boot 4 |
| Banco de dados | PostgreSQL |
| Cache | Redis |
| Build | Maven |
| Testes | JUnit 5, Testcontainers |
| Containerização | Docker / Docker Compose |
| CI | GitHub Actions |

## Como executar localmente

**Pré-requisitos:** JDK 21 e Docker Desktop. O Maven não precisa ser instalado — o projeto usa o Maven Wrapper.

**1. Suba o banco de dados:**

```bash
docker compose up -d
```

Isso levanta um PostgreSQL 17 na porta 5432 e um Redis 8 na porta 6379. Para conferir se estão saudáveis:

```bash
docker compose ps
```

**2. Suba a aplicação:**

```bash
# Windows
.\mvnw spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Na primeira subida o Flyway cria a tabela `links` e registra a migration aplicada. A aplicação sobe em `http://localhost:8080`.

Para derrubar o banco (mantendo os dados) ou apagar tudo:

```bash
docker compose down
docker compose down -v
```

Para verificar se está no ar:

```bash
curl http://localhost:8080/actuator/health
```

Resposta esperada:

```json
{"status":"UP","groups":["liveness","readiness"]}
```

Para rodar os testes:

```bash
.\mvnw test
```

> A maior parte da suíte roda sem infraestrutura nenhuma. O teste de contexto
> (`EncurtadorLinksApplicationTests`) sobe a aplicação inteira e por isso exige o
> banco de pé: rode `docker compose up -d` antes. Essa dependência de um banco
> ligado à mão é exatamente o problema que a Etapa 7 resolve com Testcontainers.

## API

### `POST /api/v1/links` — cria um link curto

Requisição:

```json
{
  "originalUrl": "https://www.exemplo.com/uma/pagina/com/url/bem/longa",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

| Campo | Obrigatório | Regras |
|---|---|---|
| `originalUrl` | sim | precisa começar com `http://` ou `https://`, ter domínio e no máximo 2048 caracteres |
| `expiresAt` | não | instante ISO-8601 no futuro; ausente significa que o link nunca expira |

Resposta `201 Created`, com o header `Location` apontando para o link curto:

```json
{
  "code": "g1KGzjc",
  "shortUrl": "http://localhost:8080/g1KGzjc",
  "originalUrl": "https://www.exemplo.com/uma/pagina/com/url/bem/longa",
  "createdAt": "2026-08-19T10:41:38.583408400Z",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

Erros seguem o formato **Problem Details (RFC 9457)**, com um campo extra `errors` quando a falha é de validação:

```json
{
  "type": "about:blank",
  "title": "Requisicao invalida",
  "status": 400,
  "detail": "Um ou mais campos estao invalidos",
  "instance": "/api/v1/links",
  "errors": {
    "originalUrl": [
      "a URL e obrigatoria",
      "a URL precisa comecar com http:// ou https://"
    ]
  }
}
```

Exemplo de chamada:

```bash
curl -i -X POST http://localhost:8080/api/v1/links -H "Content-Type: application/json" -d "{\"originalUrl\":\"https://www.google.com\"}"
```

### `GET /{code}` — redireciona para a URL original

```bash
curl -i http://localhost:8080/GSIVMAP
```

Resposta `302 Found`:

```
HTTP/1.1 302
Location: https://spring.io/projects/spring-boot
Cache-Control: no-store
```

Se o código não existir **ou o link já tiver expirado**, a resposta é `404`, com o mesmo corpo nos dois casos — responder "existiu, mas venceu" entregaria de graça a informação de que aquele código já foi válido.

```json
{
  "type": "about:blank",
  "title": "Link nao encontrado",
  "status": 404,
  "detail": "link nao encontrado ou expirado",
  "instance": "/8zvpfK3"
}
```

O caminho aceita apenas de 4 a 16 caracteres alfanuméricos. Qualquer outra coisa (`/favicon.ico`, `/robots.txt`) devolve 404 sem chegar a consultar o cache ou o banco.

## Configuração

| Propriedade | Variável de ambiente | Padrão | Para que serve |
|---|---|---|---|
| `shortener.base-url` | `SHORTENER_BASE_URL` | `http://localhost:8080` | endereço público usado para montar a URL curta devolvida na resposta |
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/encurtador` | endereço do banco |
| `spring.datasource.username` | `DB_USERNAME` | `encurtador` | usuário do banco |
| `spring.datasource.password` | `DB_PASSWORD` | `encurtador` | senha do banco |
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | endereço do Redis |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | porta do Redis |

Os valores padrão existem para desenvolvimento local e batem com o que o `docker-compose.yml` cria. Em produção todos vêm do ambiente — nenhuma credencial fica em arquivo versionado.

## Estrutura do projeto

```
encurtador-links/
├── .mvn/wrapper/                    # Maven Wrapper — garante a mesma versão do Maven para todos
├── src/
│   ├── main/java/.../encurtador/
│   │   ├── config/                  # Beans de configuração (Clock, propriedades)
│   │   ├── controller/              # Porta HTTP: recebe e devolve JSON
│   │   ├── domain/                  # Modelo e regras do domínio
│   │   ├── dto/                     # Contratos de entrada e saída da API
│   │   ├── exception/               # Exceções de negócio e tratador global
│   │   ├── repository/              # Contrato de armazenamento e implementações
│   │   └── service/                 # Regra de negócio
│   ├── main/resources/
│   │   ├── db/migration/            # Migrations do Flyway (V1__..., V2__...)
│   │   └── application.properties
│   └── test/java/                   # Testes espelhando a estrutura acima
├── docker-compose.yml               # PostgreSQL e Redis para desenvolvimento local
├── mvnw / mvnw.cmd                  # Scripts do Maven Wrapper (Linux/macOS e Windows)
└── pom.xml                          # Dependências e configuração de build
```

## Decisões técnicas

**Maven Wrapper em vez de Maven instalado.** Os arquivos `mvnw` e `.mvn/` ficam versionados no repositório e baixam automaticamente a versão correta do Maven. Qualquer pessoa que clone o projeto compila com exatamente a mesma versão, sem precisar instalar nada.

**Actuator com exposição restrita.** Apenas os endpoints `/health` e `/info` são expostos. Expor `*` liberaria endpoints com informação sensível sobre o ambiente da aplicação.

**Stack traces não retornam na resposta HTTP.** Uma stack trace em resposta de API expõe estrutura interna, versões de bibliotecas e caminhos de arquivo — informação útil para quem quer atacar o serviço.

**Código curto aleatório em vez de sequencial.** A alternativa comum é converter o id do banco para Base62, o que nunca colide. O problema é que os códigos ficam enumeráveis: quem recebe `2` tenta `3` e varre todos os links do serviço. O código aleatório de 7 caracteres (62⁷ ≈ 3,5 trilhões de combinações) custa uma consulta a mais para checar colisão, mas não entrega o acervo de links de graça. A geração usa `SecureRandom` — `Random` é previsível a partir da semente.

**Repositório atrás de uma interface.** Enquanto não há banco, os links ficam num `ConcurrentHashMap`. Como o serviço depende da interface `LinkRepository` e não da implementação, a troca por PostgreSQL na Etapa 3 não altera nenhuma linha da regra de negócio.

**Validação em duas camadas.** As anotações do Bean Validation no DTO protegem a porta HTTP; a checagem no serviço protege a regra de negócio de qualquer outra entrada (uma fila, um job, um teste). A anotação confere o formato do texto; o serviço confere se a URL é resolvível — protocolo aceito e domínio presente. Sem isso, `javascript:alert(1)` seria um link válido.

**Erros no formato Problem Details (RFC 9457).** É o padrão do Spring desde a versão 6 e evita inventar mais um formato de erro próprio. Cada campo inválido devolve uma lista de mensagens, porque um campo pode violar várias regras de uma vez e a ordem em que o Bean Validation as avalia não é garantida.

**`Clock` injetado como bean.** Chamar `Instant.now()` dentro da regra de negócio deixa o código impossível de testar — não dá para escrever "dado que agora são 10h". Com o relógio injetado, o teste usa `Clock.fixed` e controla o tempo.

**URL base como configuração, não deduzida da requisição.** Atrás de um proxy ou load balancer, o host que chega na requisição não é o host público. Por isso `shortener.base-url` é configuração, lida de variável de ambiente em produção.

**Migrations com Flyway, e `ddl-auto=validate` no Hibernate.** Deixar o Hibernate criar as tabelas (`ddl-auto=update`) é o caminho fácil e o mais perigoso: o schema passa a ser um efeito colateral das classes Java, ninguém sabe qual versão está em produção e não existe forma de reverter. Com Flyway, cada alteração é um arquivo SQL versionado no Git, aplicado uma vez e registrado na tabela `flyway_schema_history`. O `validate` fecha o cerco: se a entidade e a tabela discordarem, a aplicação se recusa a subir — o erro aparece no deploy, não horas depois numa consulta em produção.

**Índice único em `code`, no banco.** A verificação de colisão em Java tem uma janela entre o `SELECT` e o `INSERT` em que outra requisição pode gravar o mesmo código. Só o banco fecha essa janela. O índice serve também ao desempenho: o redirecionamento (Etapa 4) busca sempre por `code`, e sem índice cada acesso viraria varredura da tabela inteira.

**`TIMESTAMPTZ` em vez de `TIMESTAMP`.** O tipo sem fuso guarda "10:00" sem dizer 10:00 de onde. Com servidor e usuários em fusos diferentes, isso vira bug de expiração de link. O Hibernate está configurado para gravar e ler em UTC.

**Adaptador entre o serviço e o Spring Data.** O mais comum é `LinkRepository extends JpaRepository`, o que obrigaria o `LinkService` a conhecer o Spring Data e os vinte e poucos métodos que ele traz. Aqui a porta `LinkRepository` continua com dois métodos e um adaptador (`JpaLinkRepository`) faz o repasse. O preço é uma classe de repasse; o retorno é que a entrada do PostgreSQL não alterou nenhuma linha da regra de negócio, e os testes de regra continuam rodando em memória, sem banco e sem Docker.

**`equals`/`hashCode` pelo `code`, nunca pelo `id`.** Armadilha clássica de JPA: uma entidade nova tem `id` nulo até ser gravada, então usar o `id` faz o objeto mudar de identidade no meio da transação e quebra `HashSet` e `HashMap`. O `code` é atribuído na construção e nunca muda.

**`spring.jpa.open-in-view=false`.** O padrão do Spring Boot (`true`) mantém a sessão do Hibernate aberta até a resposta HTTP terminar: segura conexão do pool à toa e esconde consultas disparadas durante a serialização do JSON. Desligado, o carregamento de dados fica todo dentro do serviço, onde dá para enxergar.


**Redirecionamento com 302, não 301.** O `301 Moved Permanently` é mais rápido: o navegador memoriza o destino e nas próximas vezes sequer chama o serviço. É exatamente por isso que não serve aqui — se o navegador não chama, não há o que contar, e a Etapa 6 é registro de cliques. O 301 também é difícil de desfazer: um link publicado com destino errado fica cacheado no navegador de quem clicou, fora do alcance do servidor. O header `Cache-Control: no-store` reforça a mesma intenção para proxies no meio do caminho.

**Cache guarda uma projeção, não a entidade.** O que vai para o Redis é um `LinkTarget` — só `originalUrl` e `expiresAt`. A entidade `Link` carrega id, data de criação e tudo que as próximas etapas vão acrescentar; guardar isso no caminho quente seria pagar memória e tráfego de rede por campo que o redirecionamento nunca lê.

**A expiração é checada na leitura, fora do cache.** Como o `expiresAt` viaja junto no valor cacheado, um link vencido é recusado mesmo que a cópia no Redis continue viva por mais 59 minutos. Se o cache guardasse apenas a URL, a expiração passaria a depender do TTL do Redis — ou seja, o link continuaria funcionando por até uma hora depois de vencer. O TTL do cache existe para controlar memória, não para decidir regra de negócio.

**Código inexistente não vai para o cache.** Cachear a ausência protegeria o PostgreSQL de uma varredura de códigos aleatórios, mas abriria a possibilidade de um código ficar marcado como inexistente e ser criado logo depois. Com o volume deste projeto, proteger a correção vale mais; o freio contra varredura é o rate limiting da Etapa 5.

**A busca cacheada mora numa classe separada do serviço.** O `@Cacheable` só funciona quando a chamada atravessa o proxy que o Spring cria em volta do bean. Se o método estivesse no próprio `LinkService` e fosse chamado de outro método dele, seria um `this.findTarget(...)` direto — sem proxy, sem cache, e sem nenhum erro avisando. Essa armadilha se chama auto-invocação e vale igual para `@Transactional` e `@Async`.

**Chaves e valores legíveis no Redis.** O padrão do Spring é serialização nativa do Java: gera bytes ilegíveis, exige implementar `Serializable` e quebra quando a classe muda de forma. Aqui a chave é texto (`encurtador:links::abc1234`) e o valor é JSON, o que permite inspecionar o cache com `redis-cli GET` durante o desenvolvimento.

**Timeout curto no Redis.** O padrão é esperar indefinidamente. Se o Redis travar, o redirecionamento — o caminho mais quente da aplicação — trava junto. Dois segundos e falha.

**O mapeamento `/{code}` tem expressão regular.** Sem ela, `/{code}` capturaria qualquer caminho de um segmento: `/favicon.ico`, `/robots.txt`, tudo viraria consulta ao cache e ao banco.

## Autor

**João Vitor Alcântara Corrêa**
[LinkedIn](https://linkedin.com/in/joaovalcantara)
