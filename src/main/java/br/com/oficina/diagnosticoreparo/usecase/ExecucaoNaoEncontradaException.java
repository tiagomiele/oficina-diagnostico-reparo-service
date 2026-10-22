package br.com.oficina.diagnosticoreparo.usecase;

public class ExecucaoNaoEncontradaException extends RuntimeException {

  public ExecucaoNaoEncontradaException(String numeroOs) {
    super("Execução da OS " + numeroOs + " não encontrada");
  }
}
