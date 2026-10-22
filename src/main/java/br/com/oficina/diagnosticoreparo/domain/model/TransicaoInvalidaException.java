package br.com.oficina.diagnosticoreparo.domain.model;

public class TransicaoInvalidaException extends RuntimeException {

  public TransicaoInvalidaException(EtapaExecucao atual, EtapaExecucao destino) {
    super("Execução em " + atual + " não pode ir para " + destino);
  }
}
