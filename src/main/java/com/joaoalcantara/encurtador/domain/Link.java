package com.joaoalcantara.encurtador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Um link encurtado. Entidade JPA mapeada na tabela "links".
 *
 * O schema da tabela nao e criado por esta classe: quem cria e a migration do
 * Flyway (V1__create_links_table.sql). As anotacoes aqui apenas descrevem como
 * o Hibernate le e escreve nessa tabela -- e, com ddl-auto=validate, a
 * aplicacao se recusa a subir se as duas versoes discordarem.
 */
@Entity
@Table(name = "links")
public class Link {

    /**
     * Chave primaria tecnica, gerada pelo banco.
     *
     * O identificador de negocio e o "code". O id existe porque uma chave
     * primaria curta e numerica e mais barata para o banco indexar e para
     * futuras chaves estrangeiras (a tabela de cliques da Etapa 6 vai apontar
     * para ca).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Nulo significa "nunca expira". */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Construtor exigido pelo JPA, que instancia a entidade vazia e preenche os
     * campos por reflexao. E protected para que nenhum codigo da aplicacao o
     * use por engano e crie um Link sem estado valido.
     *
     * Foi por causa disto que os campos deixaram de ser finais na Etapa 3: o
     * JPA nao consegue preencher campo final por reflexao de forma confiavel.
     */
    protected Link() {
    }

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

    public Long getId() {
        return id;
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

    /**
     * Igualdade pelo "code", nao pelo id.
     *
     * Armadilha classica de JPA: um objeto novo tem id nulo ate ser gravado, e
     * se equals depender do id, o mesmo objeto muda de identidade no meio da
     * transacao -- o que quebra HashSet e HashMap. O code e atribuido na
     * construcao e nunca muda, entao serve como chave de negocio estavel.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Link other)) {
            return false;
        }
        return Objects.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

    @Override
    public String toString() {
        return "Link{id=" + id + ", code='" + code + "', originalUrl='" + originalUrl + "'}";
    }
}
