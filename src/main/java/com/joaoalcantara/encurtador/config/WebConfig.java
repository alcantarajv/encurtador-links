package com.joaoalcantara.encurtador.config;

import com.joaoalcantara.encurtador.ratelimit.RateLimitInterceptor;
import com.joaoalcantara.encurtador.ratelimit.RateLimiter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Liga os interceptores as rotas que eles protegem.
 *
 * Os caminhos sao declarados aqui, e nao dentro do interceptor, porque assim da
 * para ler numa tela so o que esta protegido e com qual politica.
 *
 * Repare no que ficou de fora: /actuator/**. O health check e chamado pela
 * plataforma de hospedagem a cada poucos segundos, sempre do mesmo IP -- seria
 * o primeiro a levar 429, e o servico seria declarado morto por causa do
 * proprio limitador.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public WebConfig(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(
                        rateLimiter, properties.creation(), "creation", properties.enabled()))
                .addPathPatterns("/api/v1/links");

        registry.addInterceptor(new RateLimitInterceptor(
                        rateLimiter, properties.redirect(), "redirect", properties.enabled()))
                .addPathPatterns("/{code:[A-Za-z0-9]{4,16}}");
    }
}
