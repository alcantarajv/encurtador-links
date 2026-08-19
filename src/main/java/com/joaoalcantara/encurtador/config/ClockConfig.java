package com.joaoalcantara.encurtador.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publica um Clock como bean.
 *
 * Chamar Instant.now() direto na regra de negocio deixa o codigo impossivel de
 * testar: nao da para escrever "dado que agora sao 10h" num teste. Injetando o
 * relogio, o teste passa um Clock.fixed e controla o tempo.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
