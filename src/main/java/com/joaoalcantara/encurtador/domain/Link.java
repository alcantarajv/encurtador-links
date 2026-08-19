package com.joaoalcantara.encurtador.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Um link encurtado.
 *
 * Classe comum em vez de record porque na Etapa 3 ela vira uma entidade JPA,
 * e o JPA nao consegue mapear records (precisa de construtor sem argumentos).
 * Os campos sao finais enquanto isso: quanto mais tempo o objeto ficar imutavel,
 * menos lugares podem alterar o estado dele por engano.
 */
public class Link {

    private final String code;
    private final String originalUrl;
    private final Instant createdAt;

    /** Nulo significa "nunca expira". */
    private final Instant expiresAt;

    public Link(String code, String originalUrl, Instant createdAt, Instant expiresAt) {
        this.code = Objects.requireNonNull(code, "code nao pode ser nulo");
        this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl nao pode ser nulo");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt nao pode ser nulo");
        this.expiresAt = expiresAt;
    }

    /**
     * Regra de negocio: um link sem data de expiracao vale para sempre.
     * O instante e recebido como parametro em vez de usar Instant.now() aqui dentro
     * para que o teste consiga controlar "que horas sao".
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public String getCode() {
        return code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Link other)) {
            return false;
        }
        return code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return "Link{code='" + code + "', originalUrl='" + originalUrl + "'}";
    }
}
