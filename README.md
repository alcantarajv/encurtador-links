# Encurtador de Links

[![CI](https://github.com/alcantarajv/encurtador-links/actions/workflows/ci.yml/badge.svg)](https://github.com/alcantarajv/encurtador-links/actions/workflows/ci.yml)
[![licenca MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-green.svg)](LICENSE)

API REST para encurtamento de links com redirecionamento de baixa latência, controle de abuso e coleta de métricas de acesso.

Encurtar uma URL é simples. O que torna o problema interessante é o que acontece depois: o endpoint de redirecionamento é o caminho mais quente da aplicação — ele precisa responder em poucos milissegundos, resistir a picos de tráfego e registrar dados de acesso sem atrasar a resposta do usuário. Este projeto foi construído com foco nesses pontos, e não apenas no CRUD de links.

---

## No ar

**<https://encurtador-links-rudi.onrender.com>**

[Demonstração ao vivo](https://encurtador-links-rudi.onrender.com) · [Health](https://encurtador-links-rudi.onrender.com/actuator/health)

A raiz traz uma página que encurta um link ao vivo no navegador e devolve o código gerado, sem precisar de terminal.

O serviço roda no plano gratuito do Render e hiberna quando ocioso: a primeira requisição depois de um tempo parado leva cerca de um minuto para responder — as seguintes são imediatas.

---

## Sumário

- [No ar](#no-ar)
- [O problema central](#o-problema-central)
- [Decisões técnicas](#decisões-técnicas)
  - [Registro completo das decisões](docs/decisoes-tecnicas.md)
- [API](#api)
- [Como rodar](#como-rodar)
- [Testes](#testes)
- [Stack](#stack)
- [Projetos relacionados](#projetos-relacionados)
- [Autor](#autor)
- [Licença](#licença)

---

## O problema central

A tentação, num encurtador, é escrever o redirecionamento assim:

```java
@GetMapping("/{code}")
public ResponseEntity<Void> redirect(@PathVariable String code) {
    Link link = linkRepository.findByCode(code).orElseThrow();
    clickRepository.save(new Click(link, Instant.now()));
    return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
            .location(URI.create(link.getOriginalUrl()))
            .build();
}
```

Cinco linhas, passa em qualquer teste, e a página abre. Mas este é o endpoint mais chamado da aplicação — cada link publicado gera um acesso por visitante — e as cinco linhas têm três defeitos que não aparecem com um usuário só.

**O `301` desliga a contagem.** O navegador memoriza o destino e nas próximas vezes sequer chama o serviço. É mais rápido, e é exatamente por isso que não serve: sem chamada, não há clique para registrar. O `301` também é difícil de desfazer — um link publicado com destino errado fica cacheado no navegador de quem clicou, fora do alcance do servidor.

**A gravação do clique entra na conta do visitante.** O `INSERT` acontece antes da resposta, então cada redirecionamento paga uma escrita no banco antes de o navegador saber para onde ir. Quem espera é o visitante, por um trabalho que é do serviço.

**Toda resposta depende do banco estar de pé.** Uma consulta por clique, sempre — inclusive para o link que acabou de ser acessado mil vezes.

### O que o projeto faz em vez disso

O destino vem do Redis, e o que é guardado lá é uma **projeção** — só a URL de destino e a data de expiração —, não a entidade inteira: o caminho quente não lê os outros campos e não deveria pagar memória e tráfego de rede por eles.

A expiração viaja junto na projeção e é conferida na leitura. Se dependesse do TTL do Redis, um link vencido continuaria funcionando até a cópia cacheada morrer. O TTL existe para controlar memória, não para decidir regra de negócio.

O clique é gravado fora da thread da requisição, e os dados são copiados do `HttpServletRequest` **antes** de atravessar a fronteira entre as threads. O motivo é concreto: assim que a resposta é enviada, o Tomcat devolve a requisição ao pool e a reaproveita — ler um cabeçalho do outro lado leria o dado de **outro visitante**. É para isso que existe o record `ClickEvent`.

E quando a infraestrutura falha, o serviço escolhe continuar respondendo: erro no Redis não derruba a requisição, e o limitador de abuso deixa passar em vez de bloquear todo mundo. Para um encurtador, ficar no ar vale mais do que aplicar o limite com rigor — num fluxo de login ou de pagamento a escolha seria a oposta.

### O erro que parecia inofensivo

O método que resolve o código tinha `@Transactional(readOnly = true)`. Parece cuidado de quem sabe o que está fazendo.

A transação abre **antes** de o cache ser consultado. Toda resposta — inclusive as que o Redis já tinha na mão — pegava uma conexão do PostgreSQL. Com o banco fora do ar, um link cacheado respondia `500` depois de esperar o tempo limite de conexão, sem nunca ter precisado do banco para nada.

A correção foi tirar a anotação. É o tipo de defeito que nenhum teste de unidade encontra e nenhuma revisão de código estranha, porque a linha errada é aquela que parece a mais responsável do arquivo.

O raciocínio por trás de cada uma dessas escolhas está em [Decisões técnicas](#decisões-técnicas).

---

## Decisões técnicas

O projeto registrou 63 decisões ao longo do desenvolvimento, cada uma com o motivo e a alternativa descartada. As que estão abaixo são as que definem o projeto; o registro completo, agrupado por tema, está em **[docs/decisoes-tecnicas.md](docs/decisoes-tecnicas.md)**.

### O caminho quente

**`302`, não `301`.** O `301` é mais rápido porque o navegador memoriza o destino e nem chama mais o serviço — e é exatamente por isso que não serve: sem chamada, não há clique para contar. O `Cache-Control: no-store` reforça a mesma intenção para proxies no caminho.

**Fila limitada, com descarte.** A fila padrão do executor que grava os cliques é ilimitada e, num pico, cresce até faltar memória. Com 500 posições e descarte, a troca fica explícita: sob pico extremo perde-se estatística para não perder o redirecionamento.

### Abuso e falhas

**Rate limit no Redis, com `INCR` e `PEXPIRE` num script Lua.** Um contador em memória contaria só a parte de cada instância. E em dois comandos separados, uma queda entre eles deixaria a chave sem prazo de validade — e aquele IP bloqueado para sempre. O Redis executa o script atomicamente.

**Com o Redis fora, o serviço continua no ar, e o custo disso está escrito.** O limitador deixa passar e o cache segue para o banco. Cada requisição paga o timeout de 2 segundos antes de desistir: o serviço fica correto, mas lento. Quem resolveria isso é um circuit breaker.

**O IP vem do Spring, não do `X-Forwarded-For` lido na mão.** O cabeçalho é escrito pelo cliente, e lê-lo direto deixaria qualquer um forjar um IP a cada requisição para escapar do limite. O `server.forward-headers-strategy=framework` trata isso num filtro próprio.

### Dados

**Código aleatório, não o id em Base62.** O id convertido nunca colide, mas deixa os códigos enumeráveis: quem recebe `2` tenta `3`. O código aleatório de 7 caracteres, gerado com `SecureRandom`, custa uma consulta para checar colisão, e o índice único no banco fecha a janela entre a checagem e a gravação.

**O IP vira hash com sal antes de ser gravado.** A estatística de visitantes únicos só precisa saber se dois acessos vieram do mesmo lugar. Sem o sal, bastaria calcular o SHA-256 dos 4 bilhões de IPv4 para reverter a coluna inteira.

**O bug que só o teste de integração encontrou.** O `date_trunc('day', ...)` sobre `timestamptz` usa o fuso da sessão do banco, que o driver define pelo relógio da JVM: numa máquina em UTC−3, cliques de dois dias viravam o mesmo dia. O dublê em memória dos testes de unidade agrupa em Java, sempre em UTC, e nunca veria isso.

### Entrega

**Imagem em dois estágios e jar em camadas.** Medido neste projeto: de 1,01 GB em estágio único para 412 MB em dois estágios, e a reconstrução depois de mexer numa classe caiu para 7 segundos, com as dependências numa camada que só muda quando o `pom.xml` muda.

**A aplicação recusa subir mal configurada em produção.** Com o perfil `prod`, ela não sobe se o sal do hash de IP ainda for o de desenvolvimento, que está publicado neste repositório, nem se a URL base apontar para `localhost`. Falha barulhenta no deploy custa minutos; falha silenciosa custa o tempo até alguém reparar.

---

## API

A API cria links curtos com código aleatório em Base62, redireciona com cache no Redis, aceita expiração opcional, limita requisições por IP, registra cada clique fora da requisição e devolve estatísticas agregadas por link. Erros saem em Problem Details (RFC 9457).

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

### `GET /api/v1/links/{code}/stats` — estatísticas do link

```bash
curl http://localhost:8080/api/v1/links/J0ghmbq/stats
```

```json
{
  "code": "J0ghmbq",
  "shortUrl": "http://localhost:8080/J0ghmbq",
  "originalUrl": "https://spring.io/guides",
  "createdAt": "2026-08-19T12:54:45.383754Z",
  "expiresAt": null,
  "totalClicks": 8,
  "uniqueVisitors": 3,
  "lastClickAt": "2026-08-19T12:54:45.902021Z",
  "clicksByDay": [
    { "day": "2026-08-19", "clicks": 8 }
  ],
  "topReferrers": [
    { "referrer": "https://google.com", "clicks": 4 },
    { "referrer": "https://twitter.com", "clicks": 2 },
    { "referrer": "https://news.ycombinator.com", "clicks": 1 }
  ]
}
```

| Campo | Observação |
|---|---|
| `uniqueVisitors` | visitantes distintos, contados pelo hash do IP — o IP em si nunca é armazenado |
| `clicksByDay` | últimos 7 dias, do mais antigo ao mais recente; dias sem acesso não aparecem |
| `topReferrers` | 5 origens mais frequentes; acessos sem `Referer` ficam de fora |

Um link expirado continua tendo estatísticas: ele parou de redirecionar, mas o histórico de quem clicou nele não deixou de existir. Código inexistente devolve `404`.

### Privacidade dos dados de acesso

O endereço IP **não é gravado**. O que vai para o banco é o SHA-256 do IP combinado com um segredo da aplicação (`CLICK_IP_SALT`).

IP é dado pessoal — a LGPD o trata como tal quando permite identificar alguém em combinação com outras informações. O hash entrega a única coisa de que a estatística precisa (saber se dois acessos vieram do mesmo lugar) sem manter o dado original, e um vazamento do banco não expõe quem clicou.

O sal é essencial: sem ele, o hash de um IP seria idêntico em qualquer instalação do mundo, e bastaria pré-calcular o SHA-256 dos 4 bilhões de IPv4 para reverter a coluna inteira. Trocar o sal reinicia a contagem de visitantes distintos, porque os hashes antigos deixam de casar com os novos.

### Fora de escopo: país de origem

O país do visitante exigiria uma base GeoIP (a GeoLite2 da MaxMind é a usual), que precisa de conta, chave de licença e atualização periódica de um arquivo binário de dezenas de megabytes. É uma dependência de peso desproporcional ao restante do projeto, então ficou de fora — e não silenciosamente: fica registrado aqui como fora de escopo.

### Rate limiting

Os endpoints são limitados por IP, com políticas separadas:

| Endpoint | Limite padrão | Por quê |
|---|---|---|
| `POST /api/v1/links` | 10 / minuto | criar link grava no banco e consome espaço de códigos; ninguém cria dez links por minuto na mão |
| `GET /{code}` | 120 / minuto | é o uso normal do serviço — um limite apertado quebraria o produto em vez de protegê-lo |
| `GET /api/v1/links/{code}/stats` | 30 / minuto | roda consultas de agregação e não passa por cache |

Toda resposta traz o saldo da janela:

```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 7
```

Ao estourar, a resposta é `429` com `Retry-After` (em segundos):

```json
{
  "type": "about:blank",
  "title": "Muitas requisicoes",
  "status": 429,
  "detail": "limite de requisicoes excedido; tente novamente em 45s",
  "instance": "/api/v1/links",
  "retryAfterSeconds": 45
}
```

O `/actuator/**` fica de fora do limitador de propósito: o health check é chamado pela plataforma de hospedagem a cada poucos segundos, sempre do mesmo IP — seria o primeiro a levar 429, e o serviço seria declarado morto pelo próprio limitador.

---

## Como rodar

**Pré-requisitos:** Docker Desktop. Para o modo de desenvolvimento, também o JDK 21 — o Maven não precisa ser instalado, o projeto usa o Maven Wrapper.

Há dois modos, e a diferença entre eles é só quem executa a aplicação.

### Modo 1 — tudo em containers

Um comando, nada instalado além do Docker:

```bash
docker compose --profile app up -d
```

Isso constrói a imagem a partir do `Dockerfile` e sobe três containers: PostgreSQL, Redis e a aplicação. O `depends_on` com `condition: service_healthy` faz a aplicação esperar o banco aceitar conexão antes de subir — sem isso o Flyway tentaria migrar contra um Postgres ainda inicializando.

A primeira construção leva cerca de um minuto e meio (ela compila o projeto do zero dentro do container). As seguintes levam segundos.

Para acompanhar a subida e conferir o estado:

```bash
docker compose logs -f app
```

```bash
docker compose ps
```

### Modo 2 — desenvolvimento

Só a infraestrutura em container; a aplicação roda na IDE, com debug e recarga automática:

```bash
docker compose up -d
```

```bash
# Windows
.\mvnw spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Sem o `--profile app`, o serviço da aplicação não sobe — ele está marcado com um perfil no `docker-compose.yml` justamente para não brigar pela porta 8080 com a instância da IDE.

### Conferindo e derrubando

Em qualquer um dos modos, a aplicação responde em `http://localhost:8080`:

```bash
curl http://localhost:8080/actuator/health
```

```json
{"status":"UP","groups":["liveness","readiness"]}
```

```bash
docker compose --profile app down
```

```bash
docker compose --profile app down -v
```

O primeiro derruba os containers preservando os dados; o segundo apaga também os volumes.

### Configuração sensível

O `docker-compose.yml` tem defaults para tudo, então o projeto sobe recém-clonado sem nenhum ajuste. Para trocar credenciais, porta ou o sal do hash de IP, copie o modelo:

```bash
cp .env.example .env
```

O `.env` é lido automaticamente pelo Compose e está no `.gitignore`. O `.env.example`, versionado, documenta quais variáveis existem sem que nenhum valor real entre no repositório.

### Propriedades configuráveis

| Propriedade | Variável de ambiente | Padrão | Para que serve |
|---|---|---|---|
| `shortener.base-url` | `SHORTENER_BASE_URL` | `http://localhost:8080` | endereço público usado para montar a URL curta devolvida na resposta |
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/encurtador` | endereço do banco |
| `spring.datasource.username` | `DB_USERNAME` | `encurtador` | usuário do banco |
| `spring.datasource.password` | `DB_PASSWORD` | `encurtador` | senha do banco |
| `server.port` | `PORT` | `8080` | porta em que a aplicação escuta — plataformas de hospedagem injetam a delas |
| `spring.data.redis.url` | `REDIS_URL` | montada de `REDIS_HOST`/`REDIS_PORT` | URL única do Redis; tem precedência sobre host e porta |
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | endereço do Redis |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | porta do Redis |
| `shortener.rate-limit.enabled` | `RATE_LIMIT_ENABLED` | `true` | liga/desliga o limitador |
| `shortener.rate-limit.creation.limit` | `RATE_LIMIT_CREATION` | `10` | criações de link por minuto, por IP |
| `shortener.rate-limit.redirect.limit` | `RATE_LIMIT_REDIRECT` | `120` | redirecionamentos por minuto, por IP |
| `shortener.rate-limit.stats.limit` | `RATE_LIMIT_STATS` | `30` | consultas de estatística por minuto, por IP |
| `shortener.click-tracking.enabled` | `CLICK_TRACKING_ENABLED` | `true` | liga/desliga o registro de cliques |
| `shortener.click-tracking.ip-salt` | `CLICK_IP_SALT` | valor de desenvolvimento | segredo usado no hash do IP — **trocar em produção** |
| — | `APP_PORT` | `8080` | porta publicada no host pelo Compose (só afeta o container) |
| `spring.datasource.hikari.maximum-pool-size` | `DB_POOL_SIZE` | `10` | teto de conexões do pool |
| `spring.datasource.hikari.connection-timeout` | `DB_CONNECTION_TIMEOUT` | `3000` | espera por conexão livre, em ms — bancos que hibernam precisam de mais |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | nenhum | `docker` no container local; `prod` no deploy, que ativa os dois |

Os valores padrão existem para desenvolvimento local e batem com o que o `docker-compose.yml` cria. Em produção todos vêm do ambiente — nenhuma credencial fica em arquivo versionado.

As variáveis podem ser definidas no `.env` da raiz, que o Compose lê sozinho — o `.env.example` serve de modelo e é o único dos dois versionado.

### Estrutura do projeto

```
encurtador-links/
├── .github/workflows/ci.yml         # Integração contínua: testes e verificação da imagem
├── .mvn/wrapper/                    # Maven Wrapper — garante a mesma versão do Maven para todos
├── src/
│   ├── main/java/.../encurtador/
│   │   ├── config/                  # Beans de configuração, propriedades e checagens de produção
│   │   ├── controller/              # Porta HTTP: recebe e devolve JSON
│   │   ├── domain/                  # Modelo e regras do domínio
│   │   ├── dto/                     # Contratos de entrada e saída da API
│   │   ├── exception/               # Exceções de negócio e tratador global
│   │   ├── ratelimit/               # Limitador por IP e interceptor HTTP
│   │   ├── repository/              # Contrato de armazenamento e implementações
│   │   └── service/                 # Regra de negócio
│   ├── main/resources/
│   │   ├── db/migration/            # Migrations do Flyway (V1__..., V2__...)
│   │   ├── static/index.html        # Página inicial: demonstra a API sem terminal
│   │   ├── application.properties
│   │   └── application-docker.properties   # sobrepõe o base quando roda em container
│   └── test/java/
│       ├── integration/             # Testes com PostgreSQL e Redis reais (Testcontainers)
│       └── ...                      # Demais testes espelham a estrutura de main
├── Dockerfile                       # Imagem da aplicação, em dois estágios
├── .dockerignore                    # O que não é enviado ao daemon do Docker no build
├── docker-compose.yml               # PostgreSQL, Redis e a aplicação (esta, sob perfil)
├── .env.example                     # Modelo das variáveis lidas pelo Compose
├── render.yaml                      # Infraestrutura do deploy, versionada
├── mvnw / mvnw.cmd                  # Scripts do Maven Wrapper (Linux/macOS e Windows)
└── pom.xml                          # Dependências e configuração de build
```

---

## Testes

**109 testes: 77 unitários e de fatia web, e 32 de integração.** Todos rodam com um único comando:

```bash
./mvnw test
```

**Não é preciso subir nada antes.** Os testes de integração levantam PostgreSQL e Redis em containers descartáveis via Testcontainers — só é necessário ter o Docker rodando. A suíte inteira leva cerca de 25 segundos.

| Tipo | Quantidade | Infraestrutura |
|---|---|---|
| Unidade e fatia web | 77 | nenhuma |
| Integração | 32 | containers criados pelo próprio teste |

Os testes de integração cobrem justamente o que os de unidade não alcançam: o SQL de agregação, o cache no Redis (fora do Spring, o `@Cacheable` é inerte), o script Lua do rate limiter e o registro assíncrono de cliques de ponta a ponta.

### Na integração contínua

Cada push e cada pull request para `main` disparam o workflow [`ci.yml`](.github/workflows/ci.yml), em duas etapas encadeadas:

| Etapa | O que faz | Falha quando |
|---|---|---|
| **Testes** | roda os 109 testes, incluindo os de integração | qualquer teste quebra |
| **Imagem Docker** | constrói a imagem, sobe a stack completa e chama a API | a imagem não constrói, não fica saudável ou não responde |

A segunda só executa se a primeira passar. Construir imagem de um código já sabidamente quebrado é desperdício.

A verificação da imagem não se contenta em construir: ela sobe o `docker compose --profile app` com `--wait` (que espera os healthchecks e falha se algum não passar), cria um link pela API e confere que o `Location` do redirecionamento aponta para a URL original. Uma imagem pode compilar e não subir — variável de ambiente faltando, permissão de diretório, `ENTRYPOINT` errado — e só a stack de pé respondendo descarta isso.

Quando um teste falha, os relatórios do Surefire ficam disponíveis para download na página da execução; quando a etapa da imagem falha, o log da aplicação é impresso antes do runner ser destruído.

---

## Stack

Java 21 · Spring Boot 4.1 · PostgreSQL 17 · Redis 8 · Flyway · JPA/Hibernate · JUnit 6 · AssertJ · Testcontainers 2 · Docker · GitHub Actions · Render · Neon

---

## Projetos relacionados

Este é o primeiro de uma série de quatro projetos, e cada um ataca um problema de backend que só aparece em produção:

1. **encurtador-links** — **latência**: cache com Redis, rate limiting, processamento assíncrono
2. [reserva-quadras](https://github.com/alcantarajv/reserva-quadras) — **concorrência**: constraint de exclusão do PostgreSQL e teste multi-thread
3. [api-pedidos](https://github.com/alcantarajv/api-pedidos) — **consistência entre sistemas**: idempotência e padrão outbox
4. [triagem-chamados](https://github.com/alcantarajv/triagem-chamados) — **resiliência a uma dependência externa lenta, cara e instável**: fallback entre provedores de IA, circuit breaker, validação da resposta e controle de custo

---

## Autor

**João Vitor Alcântara Corrêa**
[joaoalcantara.dev](https://joaoalcantara.dev) · [GitHub](https://github.com/alcantarajv) · [LinkedIn](https://linkedin.com/in/joaovalcantara)

---

## Licença

[MIT](LICENSE).
