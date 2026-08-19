# Encurtador de Links

API REST para encurtamento de links com redirecionamento de baixa latência, controle de abuso e coleta de métricas de acesso.

> Projeto em desenvolvimento. Este README é atualizado a cada etapa concluída.

## Sobre o projeto

Encurtar uma URL é simples. O que torna o problema interessante é o que acontece depois: o endpoint de redirecionamento é o caminho mais quente da aplicação — ele precisa responder em poucos milissegundos, resistir a picos de tráfego e registrar dados de acesso sem atrasar a resposta do usuário.

Este projeto foi construído com foco nesses pontos, e não apenas no CRUD de links.

## Funcionalidades

- [ ] Criação de links curtos com código gerado de forma determinística e sem colisões
- [ ] Redirecionamento HTTP com cache em memória distribuída (Redis)
- [ ] Expiração opcional de links
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

## Estrutura do projeto

```
encurtador-links/
├── .mvn/wrapper/          # Maven Wrapper — garante a mesma versão do Maven para todos
├── src/
│   ├── main/
│   │   ├── java/          # Código da aplicação
│   │   └── resources/     # Configurações (application.properties)
│   └── test/
│       └── java/          # Testes automatizados
├── mvnw / mvnw.cmd        # Scripts do Maven Wrapper (Linux/macOS e Windows)
└── pom.xml                # Dependências e configuração de build
```

## Decisões técnicas

**Maven Wrapper em vez de Maven instalado.** Os arquivos `mvnw` e `.mvn/` ficam versionados no repositório e baixam automaticamente a versão correta do Maven. Qualquer pessoa que clone o projeto compila com exatamente a mesma versão, sem precisar instalar nada.

**Actuator com exposição restrita.** Apenas os endpoints `/health` e `/info` são expostos. Expor `*` liberaria endpoints com informação sensível sobre o ambiente da aplicação.

**Stack traces não retornam na resposta HTTP.** Uma stack trace em resposta de API expõe estrutura interna, versões de bibliotecas e caminhos de arquivo — informação útil para quem quer atacar o serviço.

_Esta seção é atualizada conforme novas decisões são tomadas._

## Autor

**João Vitor Alcântara Corrêa**
[LinkedIn](https://linkedin.com/in/joaovalcantara)
