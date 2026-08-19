package com.joaoalcantara.encurtador.service;

/**
 * Gera o codigo curto que identifica um link.
 *
 * E uma interface para que o teste do LinkService possa injetar um gerador
 * previsivel e verificar o comportamento de colisao sem depender de sorte.
 */
public interface ShortCodeGenerator {

    String generate();
}
