# Encurtador de Links

API REST para encurtamento de links com redirecionamento de baixa latência, controle de abuso e coleta de métricas de acesso.

> Projeto em desenvolvimento. Este README é atualizado a cada etapa concluída.

## Sobre o projeto

Encurtar uma URL é simples. O que torna o problema interessante é o que acontece depois: o endpoint de redirecionamento é o caminho mais quente da aplicação — ele precisa responder em poucos milissegundos, resistir a picos de tráfego e registrar dados de acesso sem atrasar a resposta do usuário.

Este projeto foi construído com foco nesses pontos, e não apenas no CRUD de links.

## Funcionalidades

- [x] Criação de links curtos com código aleatório em Base62 e verificação de colisão
- [x] Validação de entrada com respostas de erro no formato Problem Details (RFC 9457)
- [ ] Persistência em PostgreSQL
- [ ] Redirecionamento HTTP com cache em memória distribuída (Redis)
- [ ] Expiração opcional de links _(o campo já é aceito e armazenado; a checagem entra com o redirecionamento)_
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

**Pré-requisito:** JDK 21 instalado. O Maven não precisa ser instalado — o projeto usa o Maven Wrapper.

```bash
# Windows
.\mvnw spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

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

## API

> **Atenção:** até a Etapa 3 os links ficam em memória. Reiniciar a aplicação apaga tudo.

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

## Configuração

| Propriedade | Variável de ambiente | Padrão | Para que serve |
|---|---|---|---|
| `shortener.base-url` | `SHORTENER_BASE_URL` | `http://localhost:8080` | endereço público usado para montar a URL curta devolvida na resposta |

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
│   ├── main/resources/              # application.properties
│   └── test/java/                   # Testes espelhando a estrutura acima
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

## Autor

**João Vitor Alcântara Corrêa**
[LinkedIn](https://linkedin.com/in/joaovalcantara)
