package com.joaoalcantara.encurtador.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do registro de cliques (prefixo "shortener.click-tracking").
 *
 * @param enabled    permite desligar o registro por completo
 * @param ipSalt     segredo misturado ao IP antes do hash.
 *                   <p>
 *                   Sem sal, o hash de um IP e sempre o mesmo em qualquer lugar
 *                   do mundo: quem vazasse o banco poderia gerar o SHA-256 de
 *                   todos os 4 bilhoes de IPv4 numa tarde e descobrir quem
 *                   clicou. O sal, que so a aplicacao conhece, torna essa tabela
 *                   inutil.
 *                   <p>
 *                   Trocar o sal reinicia a contagem de visitantes distintos: os
 *                   hashes antigos deixam de casar com os novos.
 * @param topReferrersLimit quantas origens o endpoint de estatisticas devolve
 * @param historyDays       quantos dias de historico diario devolver
 */
@ConfigurationProperties(prefix = "shortener.click-tracking")
public record ClickTrackingProperties(
        boolean enabled,
        String ipSalt,
        int topReferrersLimit,
        int historyDays
) {
}
