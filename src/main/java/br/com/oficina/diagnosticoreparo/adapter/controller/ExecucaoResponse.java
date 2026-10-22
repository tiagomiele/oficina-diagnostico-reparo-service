package br.com.oficina.diagnosticoreparo.adapter.controller;

import br.com.oficina.diagnosticoreparo.domain.model.Execucao;
import java.time.Instant;

public record ExecucaoResponse(
    String numeroOs,
    String etapa,
    Long idCliente,
    String placa,
    String descricaoProblema,
    String diagnostico,
    boolean retrabalho,
    Instant entradaFilaEm,
    Instant atualizadoEm) {

  static ExecucaoResponse de(Execucao execucao) {
    Execucao.Dados d = execucao.dados();
    return new ExecucaoResponse(
        d.numeroOs(),
        d.etapa().name(),
        d.idCliente(),
        d.placa(),
        d.descricaoProblema(),
        d.diagnostico(),
        d.retrabalho(),
        d.entradaFilaEm(),
        d.atualizadoEm());
  }
}
