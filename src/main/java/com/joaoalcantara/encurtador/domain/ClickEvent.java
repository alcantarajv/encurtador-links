package com.joaoalcantara.encurtador.domain;

import java.time.Instant;

/**
 * Os dados de um acesso, ja extraidos da requisicao HTTP.
 *
 * POR QUE ISTO EXISTE
 *
 * O registro do clique acontece em outra thread, depois que a resposta ja foi
 * enviada. Nesse momento o HttpServletRequest do Tomcat ja foi devolvido ao pool
 * e reaproveitado por outra requisicao -- ler qualquer coisa dele ali dentro
 * devolveria dado de outro visitante ou uma excecao.
 *
 * Por isso tudo o que interessa e copiado para este record ainda na thread da
 * requisicao, e so ele atravessa a fronteira para a thread de gravacao.
 *
 * @param code       codigo curto acessado
 * @param linkId     chave do link, para nao precisar consultar o banco de novo
 * @param clickedAt  instante do acesso
 * @param referrer   de onde veio o visitante; pode ser nulo
 * @param userAgent  navegador declarado; pode ser nulo
 * @param clientIp   IP de origem, que sera transformado em hash antes de gravar
 */
public record ClickEvent(
        String code,
        Long linkId,
        Instant clickedAt,
        String referrer,
        String userAgent,
        String clientIp
) {
}
