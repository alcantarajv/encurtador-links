package com.joaoalcantara.encurtador.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Corpo da requisicao de criacao de link.
 *
 * Record porque um DTO e so um pacote de dados imutavel: o compilador ja gera
 * construtor, getters, equals e toString.
 *
 * As anotacoes de validacao ficam aqui, na fronteira da aplicacao, para que
 * dado malformado seja recusado antes de chegar na regra de negocio.
 *
 * @param originalUrl URL que sera encurtada
 * @param expiresAt   momento da expiracao; nulo significa que o link nao expira
 */
public record CreateLinkRequest(

        @NotBlank(message = "a URL e obrigatoria")
        @Size(max = 2048, message = "a URL nao pode passar de 2048 caracteres")
        @Pattern(
                regexp = "^https?://.+",
                message = "a URL precisa comecar com http:// ou https://"
        )
        String originalUrl,

        @Future(message = "a data de expiracao precisa estar no futuro")
        Instant expiresAt
) {
}
