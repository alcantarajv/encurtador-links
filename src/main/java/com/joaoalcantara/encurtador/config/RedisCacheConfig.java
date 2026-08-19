package com.joaoalcantara.encurtador.config;

import com.joaoalcantara.encurtador.domain.LinkTarget;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuracao do cache em Redis.
 *
 * O @EnableCaching e o que liga a leitura das anotacoes @Cacheable. Sem ele o
 * Spring simplesmente ignora a anotacao -- de novo, sem erro nenhum.
 *
 * ARMADILHA DE VERSAO (Spring Boot 4)
 *
 * O Spring Boot 4 traz o Jackson 3, cujas classes estao em "tools.jackson" e nao
 * mais em "com.fasterxml.jackson". O Spring Data Redis passou a oferecer duas
 * familias de serializador:
 *
 *   GenericJackson2JsonRedisSerializer  -> Jackson 2 (o que todo tutorial mostra)
 *   JacksonJsonRedisSerializer          -> Jackson 3 (o correto aqui)
 *
 * Reparou que a versao nova e a que NAO tem numero no nome? Copiar o exemplo da
 * internet quebra na compilacao ou, pior, em tempo de execucao.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Quanto tempo uma entrada fica no Redis.
     *
     * Este TTL nao tem relacao com a expiracao do link: quem decide se o link
     * venceu e o expiresAt guardado dentro do valor cacheado. O TTL existe para
     * o cache nao crescer indefinidamente e para que uma entrada eventualmente
     * volte a ser lida do banco.
     */
    private static final Duration ENTRY_TTL = Duration.ofHours(1);

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ENTRY_TTL)

                // Sem isto, o Spring grava "null" no cache para chave inexistente.
                .disableCachingNullValues()

                // Prefixo legivel: as chaves ficam "encurtador:links::abc1234".
                // Facilita inspecionar e limpar so o que e desta aplicacao no
                // redis-cli, e evita colisao caso outro servico use o mesmo Redis.
                .prefixCacheNameWith("encurtador:")

                // Chave como texto puro. O padrao seria serializacao Java, que
                // deixaria a chave ilegivel no redis-cli.
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))

                // Valor como JSON. O padrao do Spring e serializacao nativa do
                // Java: gera bytes ilegiveis, exige implementar Serializable e
                // quebra quando a classe muda de forma. Com o serializador
                // tipado em LinkTarget o JSON sai limpo, sem campo de tipo.
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(LinkTarget.class)));
    }
}
