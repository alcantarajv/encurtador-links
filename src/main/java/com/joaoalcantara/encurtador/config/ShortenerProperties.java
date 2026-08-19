package com.joaoalcantara.encurtador.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da aplicacao lida do application.properties (prefixo "shortener").
 *
 * @ConfigurationProperties em vez de @Value espalhado pelo codigo: a configuracao
 * fica reunida num tipo so, com nome e tipo checados na subida da aplicacao em
 * vez de estourar em tempo de execucao.
 *
 * @param baseUrl endereco publico do servico, usado para montar a URL curta
 *                devolvida ao cliente. Em producao, atras de proxy, o host que
 *                chega na requisicao nao e o host publico -- por isso ele e
 *                configuracao e nao algo deduzido do request.
 */
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(String baseUrl) {
}
