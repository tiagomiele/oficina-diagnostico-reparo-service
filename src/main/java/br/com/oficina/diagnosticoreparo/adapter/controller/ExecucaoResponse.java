package br.com.oficina.diagnosticoreparo.adapter.controller;

import br.com.oficina.diagnosticoreparo.domain.model.Execucao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Trabalho do técnico em uma OS (item da tabela DynamoDB de execuções)")
public record ExecucaoResponse(
    @Schema(description = "Número da OS (chave da tabela)", example = "OS-102026-000003")
        String numeroOs,
    @Schema(
            description = "Etapa atual",
            example = "NA_FILA_DIAGNOSTICO",
            allowableValues = {
              "NA_FILA_DIAGNOSTICO",
              "EM_DIAGNOSTICO",
              "DIAGNOSTICO_CONCLUIDO",
              "NA_FILA_REPARO",
              "EM_REPARO",
              "REPARO_CONCLUIDO",
              "CANCELADA"
            })
        String etapa,
    @Schema(description = "Id do cliente dono da OS", example = "1") Long idCliente,
    @Schema(description = "Placa do veículo", example = "ABC1D23") String placa,
    @Schema(description = "Problema relatado na abertura da OS", example = "Barulho ao frear")
        String descricaoProblema,
    @Schema(
            description = "Laudo do técnico (preenchido ao concluir o diagnóstico)",
            example = "Pastilhas de freio gastas; trocar pastilhas dianteiras")
        String diagnostico,
    @Schema(
            description = "true quando o cliente rejeitou o orçamento pedindo para refazer",
            example = "false")
        boolean retrabalho,
    @Schema(description = "Entrada na fila atual (ordena a fila)", example = "2026-10-30T21:30:00Z")
        Instant entradaFilaEm,
    @Schema(description = "Última alteração", example = "2026-10-30T21:35:00Z")
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
