package com.joaoalcantara.encurtador.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tradutor de excecao para resposta HTTP.
 *
 * Centralizar aqui evita try/catch espalhado pelos controllers e garante que
 * todo erro da API saia no mesmo formato.
 *
 * O formato e o ProblemDetail (RFC 9457), padrao do Spring desde a versao 6 --
 * a maioria dos tutoriais ainda mostra uma classe "ErroResponse" escrita a mao.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Disparada quando alguma anotacao de validacao do @Valid falha.
     *
     * Cada campo devolve uma lista de mensagens porque um campo pode violar mais
     * de uma regra ao mesmo tempo (uma URL vazia quebra @NotBlank e @Pattern
     * juntos). Devolver so uma exigiria escolher qual, e a ordem em que o Bean
     * Validation avalia as anotacoes nao e garantida -- a resposta mudaria de
     * uma execucao para outra.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        // TreeMap: campos em ordem alfabetica, para a resposta ser sempre igual.
        Map<String, List<String>> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.computeIfAbsent(fieldError.getField(), field -> new ArrayList<>())
                        .add(fieldError.getDefaultMessage()));
        errors.values().forEach(java.util.Collections::sort);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos estao invalidos");
        problem.setTitle("Requisicao invalida");
        problem.setProperty("errors", errors);

        return problem;
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ProblemDetail handleInvalidUrl(InvalidUrlException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("URL invalida");

        return problem;
    }

    /**
     * Codigo curto inexistente ou expirado.
     *
     * 404 e nao 410 (Gone): o 410 confirmaria que o codigo ja existiu.
     */
    @ExceptionHandler(LinkNotFoundException.class)
    public ProblemDetail handleLinkNotFound(LinkNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "link nao encontrado ou expirado");
        problem.setTitle("Link nao encontrado");

        return problem;
    }

    /** JSON malformado ou campo com tipo errado (ex.: data fora do formato ISO-8601). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "o corpo da requisicao nao pode ser lido; verifique o JSON enviado");
        problem.setTitle("Requisicao invalida");

        return problem;
    }

    /**
     * A mensagem tecnica vai para o log, nao para a resposta: detalhe interno em
     * corpo de erro entrega estrutura da aplicacao para quem esta sondando.
     */
    @ExceptionHandler(CodeGenerationException.class)
    public ProblemDetail handleCodeGeneration(CodeGenerationException exception) {
        log.error("Falha ao gerar codigo curto", exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "nao foi possivel criar o link; tente novamente");
        problem.setTitle("Erro interno");

        return problem;
    }
}
