package br.com.oficina.diagnosticoreparo.usecase.saga;

/** Mesmos tipos do contrato da Saga do OS Service (docs/saga/contrato-mensagens.md). */
public enum TipoMensagemSaga {
  INICIAR_DIAGNOSTICO(DestinoMensagem.DIAGNOSTICO_REPARO),
  INICIAR_REPARO(DestinoMensagem.DIAGNOSTICO_REPARO),
  CANCELAR_EXECUCAO(DestinoMensagem.DIAGNOSTICO_REPARO),
  GERAR_ORCAMENTO(DestinoMensagem.ORCAMENTO_PAGAMENTO),
  CANCELAR_ORCAMENTO(DestinoMensagem.ORCAMENTO_PAGAMENTO),
  SOLICITAR_PAGAMENTO(DestinoMensagem.ORCAMENTO_PAGAMENTO),
  DIAGNOSTICO_INICIADO(DestinoMensagem.OS_SERVICE),
  DIAGNOSTICO_CONCLUIDO(DestinoMensagem.OS_SERVICE),
  ORCAMENTO_APROVADO(DestinoMensagem.OS_SERVICE),
  ORCAMENTO_REJEITADO(DestinoMensagem.OS_SERVICE),
  REPARO_CONCLUIDO(DestinoMensagem.OS_SERVICE),
  PAGAMENTO_CONFIRMADO(DestinoMensagem.OS_SERVICE),
  PAGAMENTO_RECUSADO(DestinoMensagem.OS_SERVICE);

  private final DestinoMensagem destino;

  TipoMensagemSaga(DestinoMensagem destino) {
    this.destino = destino;
  }

  public DestinoMensagem destino() {
    return destino;
  }
}
