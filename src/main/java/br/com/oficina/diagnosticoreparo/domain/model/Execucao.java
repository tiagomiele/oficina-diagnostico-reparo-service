package br.com.oficina.diagnosticoreparo.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Trabalho do técnico em uma OS: fila, diagnóstico e reparo. */
public final class Execucao {

  public static final int LIMITE_MENSAGENS = 20;

  private final String numeroOs;
  private final Long idCliente;
  private final String placa;
  private final String descricaoProblema;
  private final String correlationId;
  private final List<String> mensagensProcessadas;
  private EtapaExecucao etapa;
  private Instant entradaFilaEm;
  private Instant atualizadoEm;
  private String diagnostico;
  private boolean retrabalho;
  private long versao;

  private Execucao(Dados dados) {
    this.numeroOs = Objects.requireNonNull(dados.numeroOs(), "numeroOs é obrigatório");
    this.idCliente = dados.idCliente();
    this.placa = dados.placa();
    this.descricaoProblema = dados.descricaoProblema();
    this.correlationId = dados.correlationId();
    this.etapa = Objects.requireNonNull(dados.etapa(), "etapa é obrigatória");
    this.entradaFilaEm = dados.entradaFilaEm();
    this.atualizadoEm = dados.atualizadoEm();
    this.diagnostico = dados.diagnostico();
    this.retrabalho = dados.retrabalho();
    this.versao = dados.versao();
    this.mensagensProcessadas =
        new ArrayList<>(
            dados.mensagensProcessadas() == null ? List.of() : dados.mensagensProcessadas());
  }

  /** Estado persistido de uma execução. */
  public record Dados(
      String numeroOs,
      Long idCliente,
      String placa,
      String descricaoProblema,
      String correlationId,
      EtapaExecucao etapa,
      Instant entradaFilaEm,
      Instant atualizadoEm,
      String diagnostico,
      boolean retrabalho,
      long versao,
      List<String> mensagensProcessadas) {}

  public static Execucao naFilaDeDiagnostico(
      String numeroOs,
      Long idCliente,
      String placa,
      String descricaoProblema,
      String correlationId,
      Instant agora) {
    return new Execucao(
        new Dados(
            numeroOs,
            idCliente,
            placa,
            descricaoProblema,
            correlationId,
            EtapaExecucao.NA_FILA_DIAGNOSTICO,
            agora,
            agora,
            null,
            false,
            0,
            List.of()));
  }

  public static Execucao restaurar(Dados dados) {
    return new Execucao(dados);
  }

  public void avancar(EtapaExecucao destino, Instant agora) {
    if (!etapa.podeAvancarPara(destino)) {
      throw new TransicaoInvalidaException(etapa, destino);
    }
    etapa = destino;
    atualizadoEm = agora;
    if (destino == EtapaExecucao.NA_FILA_DIAGNOSTICO || destino == EtapaExecucao.NA_FILA_REPARO) {
      entradaFilaEm = agora;
    }
  }

  public void concluirDiagnostico(String laudo, Instant agora) {
    avancar(EtapaExecucao.DIAGNOSTICO_CONCLUIDO, agora);
    diagnostico = laudo;
  }

  /** Orçamento rejeitado com refazer: a OS volta para a fila de diagnóstico. */
  public void refazerDiagnostico(Instant agora) {
    avancar(EtapaExecucao.NA_FILA_DIAGNOSTICO, agora);
    retrabalho = true;
  }

  public boolean jaProcessou(String messageId) {
    return mensagensProcessadas.contains(messageId);
  }

  /** Guarda só as últimas mensagens para o item do DynamoDB não crescer sem limite. */
  public void registrarMensagem(String messageId) {
    mensagensProcessadas.add(messageId);
    while (mensagensProcessadas.size() > LIMITE_MENSAGENS) {
      mensagensProcessadas.remove(0);
    }
  }

  public void versaoSalva(long novaVersao) {
    versao = novaVersao;
  }

  public Dados dados() {
    return new Dados(
        numeroOs,
        idCliente,
        placa,
        descricaoProblema,
        correlationId,
        etapa,
        entradaFilaEm,
        atualizadoEm,
        diagnostico,
        retrabalho,
        versao,
        List.copyOf(mensagensProcessadas));
  }

  public String getNumeroOs() {
    return numeroOs;
  }

  public EtapaExecucao getEtapa() {
    return etapa;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public String getDiagnostico() {
    return diagnostico;
  }

  public boolean isRetrabalho() {
    return retrabalho;
  }

  public long getVersao() {
    return versao;
  }
}
