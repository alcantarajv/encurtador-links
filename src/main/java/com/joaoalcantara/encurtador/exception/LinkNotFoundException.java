package com.joaoalcantara.encurtador.exception;

/**
 * Codigo curto inexistente ou expirado.
 *
 * Os dois casos compartilham a mesma excecao de proposito: para quem chama, um
 * link expirado e um link que nunca existiu sao indistinguiveis. Responder
 * "existiu, mas venceu" entregaria de graca a informacao de que aquele codigo
 * ja foi valido.
 */
public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String code) {
        super("nenhum link ativo para o codigo " + code);
    }
}
