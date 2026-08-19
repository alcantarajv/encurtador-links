# Encurtador de Links

[![CI](https://github.com/alcantarajv/encurtador-links/actions/workflows/ci.yml/badge.svg)](https://github.com/alcantarajv/encurtador-links/actions/workflows/ci.yml)

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
- [x] Rate limiting por IP, com políticas distintas para criação e redirecionamento
- [x] Registro assíncrono de cliques (data/hora, referrer, user agent, hash do IP)
- [x] Endpoint de estatísticas agregadas por link
- [x] Empacotamento em imagem Docker e ambiente completo via Docker Compose
- [ ] Documentação interativa da API via OpenAPI/Swagger
- [ ] País de origem do clique _(exige base GeoIP — ver "Fora de escopo" abaixo)_

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (LTS) |
| Framework | Spring Boot 4 |
| Banco de dados | PostgreSQL |
| Cache | Redis |
| Build | Maven |
| Testes | JUnit 5, AssertJ, Testcontainers |
| Containerização | Docker / Docker Compose |
| CI | GitHub Actions |

## Como executar localmente

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

### Testes

```bash
.\mvnw test
```

**Não é preciso subir nada antes.** Os testes de integração levantam PostgreSQL e Redis em containers descartáveis via Testcontainers — só é necessário ter o Docker rodando. A suíte inteira leva cerca de 20 segundos.

| Tipo | Quantidade | Infraestrutura |
|---|---|---|
| Unidade e fatia web | 67 | nenhuma |
| Integração | 32 | containers criados pelo próprio teste |

Os testes de integração cobrem justamente o que os de unidade não alcançam: o SQL de agregação, o cache no Redis (fora do Spring, o `@Cacheable` é inerte), o script Lua do rate limiter e o registro assíncrono de cliques de ponta a ponta.

## Integração contínua

Cada push e cada pull request para `main` disparam o workflow [`ci.yml`](.github/workflows/ci.yml), em duas etapas encadeadas:

| Etapa | O que faz | Falha quando |
|---|---|---|
| **Testes** | roda os 99 testes, incluindo os de integração | qualquer teste quebra |
| **Imagem Docker** | constrói a imagem, sobe a stack completa e chama a API | a imagem não constrói, não fica saudável ou não responde |

A segunda só executa se a primeira passar. Construir imagem de um código já sabidamente quebrado é desperdício.

A verificação da imagem não se contenta em construir: ela sobe o `docker compose --profile app` com `--wait` (que espera os healthchecks e falha se algum não passar), cria um link pela API e confere que o `Location` do redirecionamento aponta para a URL original. Uma imagem pode compilar e não subir — variável de ambiente faltando, permissão de diretório, `ENTRYPOINT` errado — e só a stack de pé respondendo descarta isso.

Quando um teste falha, os relatórios do Surefire ficam disponíveis para download na página da execução; quando a etapa da imagem falha, o log da aplicação é impresso antes do runner ser destruído.

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

### Fora de escopo por ora: país de origem

O país do visitante exigiria uma base GeoIP (a GeoLite2 da MaxMind é a usual), que precisa de conta, chave de licença e atualização periódica de um arquivo binário de dezenas de megabytes. É uma dependência de peso desproporcional ao restante do projeto, então ficou de fora — e não silenciosamente: está na lista de funcionalidades como pendente.
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
## Configuração

| Propriedade | Variável de ambiente | Padrão | Para que serve |
|---|---|---|---|
| `shortener.base-url` | `SHORTENER_BASE_URL` | `http://localhost:8080` | endereço público usado para montar a URL curta devolvida na resposta |
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/encurtador` | endereço do banco |
| `spring.datasource.username` | `DB_USERNAME` | `encurtador` | usuário do banco |
| `spring.datasource.password` | `DB_PASSWORD` | `encurtador` | senha do banco |
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | endereço do Redis |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | porta do Redis |
| `shortener.rate-limit.enabled` | `RATE_LIMIT_ENABLED` | `true` | liga/desliga o limitador |
| `shortener.rate-limit.creation.limit` | `RATE_LIMIT_CREATION` | `10` | criações de link por minuto, por IP |
| `shortener.rate-limit.redirect.limit` | `RATE_LIMIT_REDIRECT` | `120` | redirecionamentos por minuto, por IP |
| `shortener.rate-limit.stats.limit` | `RATE_LIMIT_STATS` | `30` | consultas de estatística por minuto, por IP |
| `shortener.click-tracking.enabled` | `CLICK_TRACKING_ENABLED` | `true` | liga/desliga o registro de cliques |
| `shortener.click-tracking.ip-salt` | `CLICK_IP_SALT` | valor de desenvolvimento | segredo usado no hash do IP — **trocar em produção** |
| — | `APP_PORT` | `8080` | porta publicada no host pelo Compose (só afeta o container) |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | nenhum | no container vale `docker`, que reduz o nível de log e esconde os detalhes do `/actuator/health` |

Os valores padrão existem para desenvolvimento local e batem com o que o `docker-compose.yml` cria. Em produção todos vêm do ambiente — nenhuma credencial fica em arquivo versionado.

As variáveis podem ser definidas no `.env` da raiz, que o Compose lê sozinho — o `.env.example` serve de modelo e é o único dos dois versionado.

## Estrutura do projeto

```
encurtador-links/
├── .github/workflows/ci.yml         # Integração contínua: testes e verificação da imagem
├── .mvn/wrapper/                    # Maven Wrapper — garante a mesma versão do Maven para todos
├── src/
│   ├── main/java/.../encurtador/
│   │   ├── config/                  # Beans de configuração (Clock, cache, propriedades)
│   │   ├── controller/              # Porta HTTP: recebe e devolve JSON
│   │   ├── domain/                  # Modelo e regras do domínio
│   │   ├── dto/                     # Contratos de entrada e saída da API
│   │   ├── exception/               # Exceções de negócio e tratador global
│   │   ├── ratelimit/               # Limitador por IP e interceptor HTTP
│   │   ├── repository/              # Contrato de armazenamento e implementações
│   │   └── service/                 # Regra de negócio
│   ├── main/resources/
│   │   ├── db/migration/            # Migrations do Flyway (V1__..., V2__...)
│   │   ├── application.properties
│   │   └── application-docker.properties   # sobrepõe o base quando roda em container
│   └── test/java/
│       ├── integration/             # Testes com PostgreSQL e Redis reais (Testcontainers)
│       └── ...                      # Demais testes espelham a estrutura de main
├── Dockerfile                       # Imagem da aplicação, em dois estágios
├── .dockerignore                    # O que não é enviado ao daemon do Docker no build
├── docker-compose.yml               # PostgreSQL, Redis e a aplicação (esta, sob perfil)
├── .env.example                     # Modelo das variáveis lidas pelo Compose
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


**Contador de rate limit no Redis, não em memória.** Um contador local funciona enquanto existe uma única instância da aplicação. Com duas instâncias atrás de um balanceador, cada uma contaria metade das requisições e o limite real viraria o dobro do configurado. O Redis é onde as instâncias combinam a contagem.

**Janela fixa, com a limitação assumida.** A primeira requisição de um IP cria a chave com prazo de validade igual à janela; as seguintes só incrementam. O ponto fraco é a virada: com limite de 10 por minuto, dá para fazer 10 no fim de uma janela e 10 no começo da seguinte — 20 em poucos segundos. Janela deslizante e token bucket resolvem isso ao custo de bastante complexidade; para conter abuso grosseiro, a janela fixa entrega o necessário.

**`INCR` e `PEXPIRE` num script Lua.** Em dois comandos separados existiria uma janela em que o `INCR` acontece e o `PEXPIRE` não — por queda da aplicação no meio. A chave ficaria sem prazo de validade e aquele IP seria bloqueado **para sempre**. O Redis executa scripts Lua atomicamente.

**Falha aberta quando o Redis cai.** Sem Redis não dá para saber quantas requisições o IP já fez. São duas escolhas ruins: bloquear todo mundo (o serviço cai junto com o Redis) ou deixar passar (fica sem proteção até o Redis voltar). Para um encurtador, ficar no ar vale mais — o limite é proteção contra abuso, não barreira de segurança. Num fluxo de login ou pagamento a escolha seria a oposta. Pelo mesmo motivo, o cache tem um `CacheErrorHandler` que engole erros do Redis e deixa a chamada seguir para o banco: as duas decisões precisam concordar, senão o limitador liberaria a requisição e o cache a derrubaria logo em seguida.

**O custo dessa escolha:** com o Redis fora do ar, cada requisição paga o timeout de 2 segundos antes de desistir. O serviço continua correto, mas fica lento. Quem resolve isso de verdade é um circuit breaker — que, depois de N falhas, para de tentar por um tempo em vez de esperar o timeout toda vez.

**O IP vem do `getRemoteAddr()`, não de ler `X-Forwarded-For` na mão.** Atrás de um proxy, `getRemoteAddr()` devolveria o IP do proxy e todos os visitantes cairiam no mesmo contador. Mas ler o cabeçalho diretamente é pior: ele é escrito pelo cliente, e qualquer um poderia forjar um IP diferente a cada requisição para escapar do limite. A configuração `server.forward-headers-strategy=framework` faz o Spring tratar isso num filtro próprio, antes da requisição chegar à aplicação. Isso vale **enquanto a aplicação só for alcançável através do proxy** — exposta direto na internet, o cabeçalho volta a ser forjável.

**Health check fora do limitador.** O `/actuator/**` não é interceptado: a plataforma de hospedagem chama o health check a cada poucos segundos, sempre do mesmo IP. Seria o primeiro a levar 429, e o serviço seria declarado morto pelo próprio limitador.

**Nota para o deploy:** com o Redis fora do ar, `/actuator/health` responde `503` — o que é correto, o sistema está degradado. Mas `/actuator/health/readiness` e `/actuator/health/liveness` continuam `200`, porque a aplicação segue servindo pelo PostgreSQL. As sondas da plataforma devem apontar para esses dois, e não para o endpoint agregado, sob pena de o container ser reiniciado enquanto atende normalmente.


**Registro de clique fora da thread da requisição.** Gravar o acesso é trabalho do serviço, não do visitante: se o `INSERT` acontecesse antes da resposta, cada redirecionamento pagaria uma escrita no banco. O `@Async` com pool dedicado devolve o `302` imediatamente e grava depois.

**Os dados do clique são copiados da requisição *antes* de ir para a outra thread.** Assim que a resposta é enviada, o Tomcat devolve o `HttpServletRequest` ao pool e o reaproveita. Ler um cabeçalho na thread de gravação leria dado de **outro visitante**, ou estouraria. Por isso existe o record `ClickEvent`: ele é o que atravessa a fronteira entre as threads.

**Fila limitada e `DiscardPolicy`.** A fila padrão do executor é ilimitada — parece generosa, mas num pico ela cresce até a aplicação ficar sem memória. Com fila de 500 e descarte, a troca fica explícita: sob pico extremo perde-se estatística para não perder o redirecionamento. As alternativas são piores: `CallerRunsPolicy` faria a thread da requisição executar a gravação (exatamente o que o pool existe para evitar, e no pior momento possível) e `AbortPolicy` lançaria exceção dentro do fluxo do redirecionamento.

**O IP vira hash antes de ser gravado.** Ver a seção *Privacidade dos dados de acesso*.

**`getReferenceById` em vez de `findById` ao gravar o clique.** O `getReferenceById` devolve um proxy preguiçoso: o Hibernate não faz `SELECT` na tabela `links`, só usa o id para preencher a chave estrangeira. Com `findById` seriam duas idas ao banco por clique em vez de uma — e o dado carregado seria descartado em seguida.

**Agregação em SQL, não em Java.** Os métodos de leitura do `ClickRepository` devolvem números já agregados. Contar mil cliques em SQL custa uma consulta; trazer os mil registros para a memória e contar em Java custa mil linhas atravessando a rede.

**O `resolve` não é transacional — e isso foi uma correção, não um esquecimento.** Até esta etapa o método tinha `@Transactional(readOnly = true)`, o que parecia inofensivo. Não era: a transação abre **antes** de o cache ser consultado, então toda resposta — inclusive as que o Redis já tinha — pegava uma conexão do PostgreSQL. Com o banco fora do ar, o redirecionamento de um link cacheado respondia `500` depois de esperar o tempo limite de conexão, sem nunca ter precisado do banco. Sem a anotação, o acerto de cache não encosta no PostgreSQL.

**Timeout de conexão do HikariCP reduzido para 3 segundos.** O padrão é 30. Com o banco fora do ar, cada requisição que dependa dele segura uma thread do Tomcat por meio minuto — em poucos segundos de tráfego o servidor fica sem threads e para de responder até o que não depende do banco. Mesma lógica já aplicada ao Redis: falhar rápido é melhor do que travar.

**`AsyncUncaughtExceptionHandler` em vez de `try/catch` dentro do método.** Um método `@Async void` não tem quem o espere: se lançar, ninguém recebe a exceção. A primeira versão tinha um `try/catch` no corpo do método, e ele dava falsa segurança — quando o banco está fora do ar, a falha acontece ao **abrir a transação**, no proxy que envolve o método, e o `try/catch` interno nunca é alcançado. O tratador global fica por fora de tudo e registra a falha com o evento completo.

**Testcontainers em vez de infraestrutura ligada à mão.** Desde a Etapa 3 a suíte tinha um teste que só passava se alguém tivesse rodado `docker compose up -d` antes. Isso funciona no computador de quem lembra; não funciona no de quem acabou de clonar o projeto, e não funcionaria na integração contínua. Agora os containers são responsabilidade do próprio teste.

**Containers estáticos iniciados em bloco `static`, não `@Testcontainers` + `@Container`.** O caminho que os tutoriais mostram sobe e derruba os containers a cada **classe** de teste — com cinco classes de integração, seriam cinco PostgreSQL subindo e descendo. Iniciando no bloco `static`, eles sobem uma vez por JVM e todas as classes compartilham. Ninguém precisa derrubá-los: o Testcontainers deixa um container auxiliar (Ryuk) encarregado de limpar quando o processo morre.

**As imagens dos testes são as mesmas do `docker-compose.yml`.** Teste que roda numa versão diferente da de produção testa outra coisa.

**Testes de integração sem `@Transactional`.** A anotação faria cada teste rodar dentro de uma transação revertida no fim, e as consultas nativas poderiam não enxergar dados ainda não gravados. Aqui os dados são gravados de verdade e a limpeza é explícita, no `@BeforeEach`.

**O bug que essa etapa encontrou.** O `date_trunc('day', clicked_at)` sobre uma coluna `timestamptz` converte o valor para o fuso da **sessão** do banco antes de truncar — e o driver JDBC define esse fuso a partir do relógio da JVM. Numa máquina em UTC−3, um clique às 23:30Z e outro às 00:30Z do dia seguinte viravam 20:30 e 21:30 do *mesmo* dia local, e a série diária juntava os dois no dia errado. A correção foi `date_trunc('day', clicked_at AT TIME ZONE 'UTC')`. Nenhum teste de unidade poderia ter encontrado isso: o dublê em memória agrupa em Java, sempre em UTC. É a justificativa inteira da etapa em um caso.

**Imagem em dois estágios.** O primeiro estágio precisa do JDK completo, do Maven e de todo o código-fonte; o segundo só precisa da JRE e do resultado. Como a imagem final parte do zero e copia apenas o que interessa do primeiro, nada disso é publicado — nem fonte, nem Maven, nem o cache do `.m2`. Medido neste projeto: a mesma aplicação num estágio único dá **1,01 GB** em disco (389 MB para trafegar no registro); em dois estágios, **412 MB** em disco e 134 MB para trafegar. Dentro do container o conteúdo são 158 MB de JRE, 64 MB de dependências e 64 KB de código deste projeto.

**O jar quebrado em duas camadas, e não copiado inteiro.** O "jar gordo" do Spring Boot tem 66 MB, e 64 MB disso são bibliotecas de terceiros que só mudam quando o `pom.xml` muda. Copiado inteiro, cada alteração de uma linha de Java geraria uma camada nova de 66 MB para reconstruir, armazenar e enviar ao registro. O comando `java -Djarmode=tools ... extract` separa o conteúdo em `lib/` (66,5 MB, muda raramente) e `app.jar` (74 KB, muda sempre), copiados em duas instruções nessa ordem. Medido neste projeto: construção do zero em 1min35s, reconstrução depois de mexer numa classe em **7 segundos** — com o `COPY lib/` marcado `CACHED`.

> ⚠️ Praticamente todo tutorial de Dockerfile para Spring Boot usa `java -Djarmode=layertools -jar app.jar extract`. Esse modo foi substituído no Boot 3.3 e **removido** no Boot 4 — ele responde `Unsupported jarmode 'layertools'`.

**A aplicação sob um perfil do Compose.** Sem isso, `docker compose up -d` passaria a construir e subir também a aplicação, atrapalhando o desenvolvimento na IDE e brigando pela porta 8080. Com `profiles: ["app"]`, o comando de sempre continua levantando só a infraestrutura, e `docker compose --profile app up -d` levanta o conjunto completo.

**`depends_on` com `condition: service_healthy`.** Um `depends_on` simples só garante que o container foi criado, não que o banco aceita conexão. Como o Flyway roda na subida da aplicação, sem a condição de saúde ele tentaria migrar contra um Postgres ainda inicializando.

**Usuário sem privilégios e `ENTRYPOINT` em forma de lista.** Container roda como root por padrão; a aplicação não precisa de privilégio nenhum, não escreve em disco e usa a porta 8080. A forma de lista (`exec`) faz a JVM ser o processo 1 — na forma de shell, o `/bin/sh` seria o PID 1 e o `SIGTERM` do `docker stop` nunca chegaria na JVM, que morreria no `SIGKILL` dez segundos depois. Nos logs dá para conferir os dois: `INFO 1 ---` e o `Commencing graceful shutdown` ao parar.

**`-XX:MaxRAMPercentage=75.0`.** A JVM enxerga os limites do cgroup desde o Java 10, mas por padrão reserva no máximo 1/4 da memória do container para o heap. Num limite de 512 MB, isso são 128 MB de heap com 384 MB ociosos enquanto a aplicação sofre com coleta de lixo.

> ⚠️ Muitos Dockerfiles ainda trazem `-Djava.security.egd=file:/dev/./urandom`. Era um contorno para lentidão de entropia em Linux antigo, desnecessário desde o Java 8u162. Não está aqui.

**Perfil `docker` do Spring, e não um segundo `application.properties`.** O arquivo base liga log de DEBUG e imprime cada consulta gerada pelo Hibernate — ótimo para aprender, insuportável num serviço que recebe tráfego. O `application-docker.properties` é lido depois do base e sobrescreve só essas chaves; tudo o mais continua vindo de um lugar só. Ele também troca `management.endpoint.health.show-details` para `never`: sem autenticação configurada, o `/actuator/health` é público, e com detalhes ele revela o banco em uso, o espaço livre em disco e o estado do Redis.

**Os testes não rodam dentro da imagem.** Desde a Etapa 7 os testes de integração sobem containers via Testcontainers, o que exige acesso a um daemon do Docker — que não existe dentro do container de build. Rodar a suíte é responsabilidade da máquina do desenvolvedor e da integração contínua (Etapa 9), onde o daemon está disponível.

**`.dockerignore` antes de tudo.** Sem ele, o `docker build` empacota a pasta inteira e envia ao daemon — incluindo o `target/` com o jar de 66 MB e o `.git` com o histórico completo — só para descartar depois. Vale também como proteção: arquivo que não entra no contexto não tem como acabar dentro da imagem por descuido, e é por isso que o `.env` está listado lá.

**Sem bloco `services:` no workflow.** Quase todo tutorial de CI para Spring Boot declara `services: postgres: ... redis: ...` dentro do arquivo do GitHub Actions. Desde a Etapa 7 isso seria duplicação — e pior: a versão e a configuração desses containers ficariam mantidas num segundo lugar, livres para divergir do `docker-compose.yml` sem ninguém notar. Quem sobe a infraestrutura de teste é o próprio teste, via Testcontainers, e o runner `ubuntu-latest` já traz o Docker instalado.

**A CI sobe a aplicação, não só constrói a imagem.** Uma imagem pode compilar e não subir. O passo usa `docker compose --profile app up -d --wait`: o `--wait` espera todos os healthchecks e sai com erro se algum não passar no tempo previsto — sem ele, o comando retorna assim que os containers são criados e o teste seguinte correria contra uma aplicação ainda inicializando. Depois disso, um `curl` cria um link e confere o `Location` do redirecionamento.

**`permissions: contents: read` declarado explicitamente.** O token que o GitHub injeta no job começa com as permissões do repositório. Este workflow só precisa ler código — não publica release, não comenta em PR, não escreve em lugar nenhum. Declarar o mínimo limita o estrago caso alguma dependência da build seja comprometida.

**`concurrency` com `cancel-in-progress`.** Dois pushes seguidos no mesmo branch tornam a execução antiga irrelevante; cancelá-la libera a fila em vez de gastar minutos num resultado que ninguém vai ler.

> ⚠️ O `mvnw` estava registrado no Git como `100644` — sem o bit de execução. O Windows não tem esse conceito, então o Git o gravou assim na Etapa 0 e nada quebrou até agora; num runner Linux, `./mvnw test` responderia `Permission denied`. A correção definitiva é `git update-index --chmod=+x mvnw`; o workflow também faz `chmod +x mvnw` como proteção.

> ⚠️ As versões das actions andaram bem mais rápido do que o conteúdo publicado sugere: os tutoriais mostram `actions/checkout@v4` e `actions/setup-java@v3`, que estão três e duas versões maiores atrás de `@v7` e `@v5`.

## Autor

**João Vitor Alcântara Corrêa**
[LinkedIn](https://linkedin.com/in/joaovalcantara)
