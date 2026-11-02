package br.com.oficina.diagnosticoreparo.adapter.controller;

/** Exemplos de resposta exibidos no Swagger (constantes para uso em anotações). */
final class Exemplos {
  private Exemplos() {}

  private static final String OS =
      "\"numeroOs\": \"OS-102026-000003\", \"idCliente\": 1, \"placa\": \"ABC1D23\","
          + " \"descricaoProblema\": \"Barulho ao frear\", ";
  private static final String DATAS =
      "\"entradaFilaEm\": \"2026-10-30T21:30:00Z\", \"atualizadoEm\": \"2026-10-30T21:35:00Z\"}";
  private static final String LAUDO =
      "\"diagnostico\": \"Pastilhas de freio gastas; trocar pastilhas dianteiras\", ";

  static final String NA_FILA =
      "{" + OS + "\"etapa\": \"NA_FILA_DIAGNOSTICO\", \"retrabalho\": false, " + DATAS;
  static final String FILA = "[" + NA_FILA + "]";
  static final String EM_DIAGNOSTICO =
      "{" + OS + "\"etapa\": \"EM_DIAGNOSTICO\", \"retrabalho\": false, " + DATAS;
  static final String DIAGNOSTICO_CONCLUIDO =
      "{"
          + OS
          + "\"etapa\": \"DIAGNOSTICO_CONCLUIDO\", "
          + LAUDO
          + "\"retrabalho\": false, "
          + DATAS;
  static final String EM_REPARO =
      "{" + OS + "\"etapa\": \"EM_REPARO\", " + LAUDO + "\"retrabalho\": false, " + DATAS;
  static final String REPARO_CONCLUIDO =
      "{" + OS + "\"etapa\": \"REPARO_CONCLUIDO\", " + LAUDO + "\"retrabalho\": false, " + DATAS;

  static final String CONCLUIR_DIAGNOSTICO =
      "{\"diagnostico\": \"Pastilhas de freio gastas; trocar pastilhas dianteiras\"}";

  private static final String INSTANCIA = ", \"instance\": \"/execucoes/OS-102026-000003\"}";
  static final String ERRO_404 =
      "{\"type\": \"about:blank\", \"title\": \"Not Found\", \"status\": 404, \"detail\":"
          + " \"Execução da OS OS-102026-000003 não encontrada\""
          + INSTANCIA;
  static final String ERRO_409 =
      "{\"type\": \"about:blank\", \"title\": \"Conflict\", \"status\": 409, \"detail\":"
          + " \"Execução em NA_FILA_DIAGNOSTICO não pode ir para REPARO_CONCLUIDO\""
          + INSTANCIA;
}
