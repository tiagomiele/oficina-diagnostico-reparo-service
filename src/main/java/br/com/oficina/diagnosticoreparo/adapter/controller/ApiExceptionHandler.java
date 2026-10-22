package br.com.oficina.diagnosticoreparo.adapter.controller;

import br.com.oficina.diagnosticoreparo.domain.model.TransicaoInvalidaException;
import br.com.oficina.diagnosticoreparo.usecase.ExecucaoNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ExecucaoNaoEncontradaException.class)
  ProblemDetail naoEncontrada(ExecucaoNaoEncontradaException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({TransicaoInvalidaException.class, IllegalStateException.class})
  ProblemDetail conflito(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }
}
