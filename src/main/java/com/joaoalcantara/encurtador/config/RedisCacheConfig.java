package com.joaoalcantara.encurtador.config;

import com.joaoalcantara.encurtador.domain.LinkTarget;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
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
public class RedisCacheConfig implements CachingConfigurer {

    /**
     * Quanto tempo uma entrada fica no Redis.
     *
     * Este TTL nao tem relacao com a expiracao do link: quem decide se o link
     * venceu e o expiresAt guardado dentro do valor cacheado. O TTL existe para
     * o cache nao crescer indefinidamente e para que uma entrada eventualmente
     * volte a ser lida do banco.
     */
    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

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

    /**
     * O que fazer quando o Redis nao responde.
     *
     * Por padrao o Spring deixa o erro subir: com o Redis fora do ar, TODO
     * redirecionamento falharia -- mesmo o PostgreSQL estando saudavel e tendo a
     * resposta. O cache deixaria de ser otimizacao e viraria ponto unico de
     * falha.
     *
     * Aqui o erro e registrado e engolido: a chamada segue para o banco como se
     * fosse um cache miss. O servico fica mais lento e continua correto.
     *
     * E a mesma decisao tomada no RedisRateLimiter -- falhar aberto. As duas
     * precisam concordar: de nada adiantaria o limitador liberar a requisicao se
     * o cache derrubasse ela logo em seguida.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Falha ao ler do cache {} (chave {}): {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Falha ao gravar no cache {} (chave {}): {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Falha ao remover do cache {} (chave {}): {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Falha ao limpar o cache {}: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
