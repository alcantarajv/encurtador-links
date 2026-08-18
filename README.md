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
| Linguagem | Java 21 |
| Framework | Spring Boot 3 |
| Banco de dados | PostgreSQL |
| Cache | Redis |
| Build | Maven |
| Testes | JUnit 5, Testcontainers |
| Containerização | Docker / Docker Compose |
| CI | GitHub Actions |

## Como executar localmente

_Instruções serão adicionadas na etapa de containerização._

## Decisões técnicas

_Esta seção documenta as escolhas de arquitetura tomadas ao longo do desenvolvimento e será preenchida conforme o projeto avança._

## Autor

**João Vitor Alcântara Corrêa**
[LinkedIn](https://linkedin.com/in/joaovalcantara)
