package br.com.oficina.diagnosticoreparo.domain.model;

/** Etapas do trabalho do técnico no Serviço de Diagnóstico e Reparo. */
public enum EtapaExecucao {
  NA_FILA_DIAGNOSTICO,
  EM_DIAGNOSTICO,
  DIAGNOSTICO_CONCLUIDO,
  NA_FILA_REPARO,
  EM_REPARO,
  REPARO_CONCLUIDO,
  CANCELADA;

  public boolean podeAvancarPara(EtapaExecucao destino) {
    if (destino == null) {
      return false;
    }
    return switch (this) {
      case NA_FILA_DIAGNOSTICO -> destino == EM_DIAGNOSTICO || destino == CANCELADA;
      case EM_DIAGNOSTICO -> destino == DIAGNOSTICO_CONCLUIDO || destino == CANCELADA;
      case DIAGNOSTICO_CONCLUIDO ->
          destino == NA_FILA_REPARO || destino == NA_FILA_DIAGNOSTICO || destino == CANCELADA;
      case NA_FILA_REPARO -> destino == EM_REPARO || destino == CANCELADA;
      case EM_REPARO -> destino == REPARO_CONCLUIDO || destino == CANCELADA;
      default -> false;
    };
  }

  public boolean isFinal() {
    return !podeAvancarParaAlgumaEtapa();
  }

  private boolean podeAvancarParaAlgumaEtapa() {
    for (EtapaExecucao destino : values()) {
      if (podeAvancarPara(destino)) {
        return true;
      }
    }
    return false;
  }
}
