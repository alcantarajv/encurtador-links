package com.joaoalcantara.encurtador.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool de threads dedicado ao registro de cliques.
 *
 * O @EnableAsync liga a leitura das anotacoes @Async. Sem ele o metodo anotado
 * roda normalmente, na mesma thread -- e o redirecionamento volta a esperar a
 * gravacao sem que nada indique o problema.
 *
 * POR QUE UM POOL PROPRIO E NAO O PADRAO DO SPRING
 *
 * Sem um Executor nomeado, o Spring usa um pool compartilhado por toda a
 * aplicacao. Um pico de cliques encheria esse pool e atrasaria qualquer outra
 * tarefa assincrona que viesse a existir. Um pool dedicado isola o estrago.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    public static final String CLICK_EXECUTOR = "clickExecutor";

    @Bean(CLICK_EXECUTOR)
    public Executor clickExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Gravar clique e trabalho de I/O curto: duas threads dao conta do
        // regime normal, e o pool cresce ate quatro se a fila encher.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);

        // Fila LIMITADA. Uma fila ilimitada -- o padrao -- parece generosa, mas
        // num pico ela cresce ate a aplicacao ficar sem memoria: as tarefas
        // entram mais rapido do que saem e ninguem avisa.
        executor.setQueueCapacity(500);

        // Nome de thread reconhecivel no log e no dump: "click-1", "click-2".
        executor.setThreadNamePrefix("click-");

        // O QUE FAZER QUANDO A FILA ENCHE
        //
        // DiscardPolicy: descarta o clique silenciosamente.
        //
        // As alternativas sao piores aqui. CallerRunsPolicy faria a thread da
        // requisicao executar a gravacao -- exatamente o que este pool existe
        // para evitar, e o redirecionamento voltaria a esperar o banco no pior
        // momento possivel, que e justamente quando ha pico. AbortPolicy
        // lancaria excecao dentro do fluxo do redirecionamento.
        //
        // A troca e explicita: sob pico extremo, perde-se estatistica para nao
        // perder o redirecionamento. Contagem de cliques e dado aproximado; o
        // visitante chegar ao destino, nao.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());

        // No desligamento, termina o que ja esta na fila em vez de matar as
        // threads no meio -- senao todo deploy perderia os cliques pendentes.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        executor.initialize();
        return executor;
    }

    /**
     * Onde morrem as excecoes das tarefas assincronas.
     *
     * Um metodo @Async que devolve void nao tem quem o espere: se ele lancar,
     * ninguem recebe a excecao. O padrao do Spring registra a falha com uma
     * mensagem generica ("Unexpected exception occurred invoking async method"),
     * e sem contexto nenhum sobre o que se perdeu.
     *
     * POR QUE UM try/catch DENTRO DO METODO NAO BASTA
     *
     * Foi a primeira tentativa aqui, e ela cobria menos do que parecia. O
     * @Transactional e aplicado por um proxy que envolve o metodo: quando o
     * problema e obter conexao com o banco, a falha acontece ao ABRIR a
     * transacao, antes de a primeira linha do corpo executar. O try/catch de
     * dentro nunca e alcancado. Descoberto na pratica, derrubando o PostgreSQL
     * com a aplicacao no ar.
     *
     * Este tratador fica por fora de tudo e pega qualquer falha.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, method, params) ->
                log.error("Tarefa assincrona {} falhou com argumentos {}", method.getName(), params, exception);
    }
}
