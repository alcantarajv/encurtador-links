package com.joaoalcantara.encurtador.domain;

import java.time.Instant;

/**
 * Para onde um codigo curto aponta, ate quando, e qual e o link no banco.
 *
 * E o pedaco do Link que o caminho de redirecionamento precisa -- e so ele. A
 * entidade inteira carrega data de criacao e a colecao de cliques; guardar isso
 * no cache seria pagar memoria e trafego de rede por campo que o caminho quente
 * nunca le.
 *
 * O id entrou na Etapa 6: o registro do clique precisa da chave estrangeira, e
 * te-la aqui evita uma ida ao banco so para descobri-la a cada acesso.
 *
 * ATENCAO AO MUDAR ESTE RECORD: o formato e o mesmo gravado no Redis. Entradas
 * antigas continuam la ate o TTL vencer e serao lidas com os campos novos
 * nulos. Depois de um deploy que mexa aqui, limpe o cache.
 *
 * @param id          chave do link no banco
 * @param originalUrl destino do redirecionamento
 * @param expiresAt   momento da expiracao; nulo significa que o link nao expira
 */
public record LinkTarget(Long id, String originalUrl, Instant expiresAt) {

    /**
     * Unica definicao da regra de expiracao no projeto -- Link.isExpired delega
     * para ca. Regra de negocio duplicada e regra que um dia vai divergir.
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
