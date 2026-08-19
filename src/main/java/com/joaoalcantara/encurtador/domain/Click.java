package com.joaoalcantara.encurtador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Um acesso a um link curto.
 *
 * Todos os campos alem do link e do instante sao opcionais: navegador nenhum e
 * obrigado a mandar Referer ou User-Agent, e um cliente pode omitir os dois.
 * A estatistica trabalha com o que chegar.
 */
@Entity
@Table(name = "clicks")
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY e nao EAGER (o padrao do @ManyToOne).
     *
     * Com EAGER, toda vez que o Hibernate carregasse um clique ele carregaria o
     * link junto -- e uma consulta que devolve mil cliques dispararia mil
     * consultas extras. O gravador de cliques nunca le o link; so precisa da
     * chave estrangeira.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "link_id", nullable = false)
    private Link link;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(length = 2048)
    private String referrer;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** SHA-256 do IP com segredo da aplicacao. Nunca o IP em si. */
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    protected Click() {
    }

    public Click(Link link, Instant clickedAt, String referrer, String userAgent, String ipHash) {
        this.link = Objects.requireNonNull(link, "link nao pode ser nulo");
        this.clickedAt = Objects.requireNonNull(clickedAt, "clickedAt nao pode ser nulo");
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.ipHash = ipHash;
    }

    public Long getId() {
        return id;
    }

    public Link getLink() {
        return link;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpHash() {
        return ipHash;
    }
}
