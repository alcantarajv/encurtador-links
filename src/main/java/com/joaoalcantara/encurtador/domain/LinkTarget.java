package com.joaoalcantara.encurtador.domain;

import java.time.Instant;

/**
 * Para onde um codigo curto aponta, e ate quando.
 *
 * E o pedaco do Link que o redirecionamento precisa -- e so ele. A entidade
 * inteira carrega id, data de criacao e tudo que a Etapa 6 ainda vai acrescentar;
 * guardar isso no cache seria pagar memoria e trafego de rede por campo que o
 * caminho quente nunca le.
 *
 * Record e nao classe: alem de ser um pacote de dados imutavel, o formato JSON
 * gravado no Redis fica curto e legivel.
 *
 * @param originalUrl destino do redirecionamento
 * @param expiresAt   momento da expiracao; nulo significa que o link nao expira
 */
public record LinkTarget(String originalUrl, Instant expiresAt) {

    /**
     * Unica definicao da regra de expiracao no projeto -- Link.isExpired delega
     * para ca. Regra de negocio duplicada e regra que um dia vai divergir.
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
