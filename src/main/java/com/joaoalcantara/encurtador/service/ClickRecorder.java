package com.joaoalcantara.encurtador.service;

import com.joaoalcantara.encurtador.config.AsyncConfig;
import com.joaoalcantara.encurtador.config.ClickTrackingProperties;
import com.joaoalcantara.encurtador.domain.ClickEvent;
import com.joaoalcantara.encurtador.repository.ClickRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava o clique fora da thread da requisicao.
 *
 * Classe separada do RedirectController pelo mesmo motivo do LinkLookup: o
 * @Async so vale quando a chamada atravessa o proxy do Spring. Chamado de dentro
 * do proprio bean, o metodo roda na mesma thread e o ganho evapora -- sem erro
 * nenhum para avisar.
 */
@Component
public class ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

    /** Limites das colunas em V2__create_clicks_table.sql. */
    private static final int MAX_REFERRER = 2048;
    private static final int MAX_USER_AGENT = 512;

    private final ClickRepository clickRepository;
    private final ClickTrackingProperties properties;

    public ClickRecorder(ClickRepository clickRepository, ClickTrackingProperties properties) {
        this.clickRepository = clickRepository;
        this.properties = properties;
    }

    /**
     * O @Transactional e necessario porque esta execucao acontece fora da
     * requisicao: a transacao aberta pelo resolve() ja foi encerrada quando esta
     * thread comeca.
     *
     * Nao ha try/catch aqui, e isso e deliberado. A primeira versao tinha um, e
     * ele dava falsa seguranca: quando o banco esta fora do ar, a falha acontece
     * ao ABRIR a transacao -- no proxy que envolve este metodo -- e nunca chega
     * a executar a primeira linha do corpo. Quem trata qualquer falha, inclusive
     * essa, e o AsyncUncaughtExceptionHandler declarado no AsyncConfig.
     *
     * De todo jeito, falhar aqui nao afeta ninguem: o visitante ja foi
     * redirecionado antes desta thread comecar.
     */
    @Async(AsyncConfig.CLICK_EXECUTOR)
    @Transactional
    public void record(ClickEvent event) {
        if (!properties.enabled()) {
            return;
        }

        clickRepository.save(
                event.linkId(),
                event.clickedAt(),
                truncate(event.referrer(), MAX_REFERRER),
                truncate(event.userAgent(), MAX_USER_AGENT),
                hashOf(event.clientIp()));

        log.debug("Clique registrado para o codigo {}", event.code());
    }

    /**
     * Corta o valor no tamanho da coluna.
     *
     * User-Agent e Referer chegam do cliente e nao tem limite no protocolo: um
     * cabecalho gigante estouraria a coluna e derrubaria a gravacao. Cortar e
     * melhor do que perder o registro inteiro.
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * SHA-256 do IP concatenado ao sal da aplicacao.
     *
     * Sem o sal, o hash de um IP seria sempre o mesmo em qualquer lugar do
     * mundo: quem vazasse o banco geraria o hash dos 4 bilhoes de IPv4 numa
     * tarde e descobriria quem clicou. O sal, que so a aplicacao conhece,
     * transforma essa tabela em lixo.
     */
    private String hashOf(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((properties.ipSalt() + clientIp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 e obrigatorio em toda JVM; se faltar, o ambiente esta quebrado.
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}
