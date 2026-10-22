package br.com.oficina.diagnosticoreparo.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecucaoTest {

  private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
  private static final Instant T1 = T0.plusSeconds(60);

  private Execucao nova() {
    return Execucao.naFilaDeDiagnostico("OS-1", 1L, "ABC1D23", "barulho", "corr", T0);
  }

  @Test
  void ciclo() {
    Execucao e = nova();
    e.avancar(EtapaExecucao.EM_DIAGNOSTICO, T1);
    e.concluirDiagnostico("pastilha gasta", T1);
    assertThat(e.getDiagnostico()).isEqualTo("pastilha gasta");
    e.avancar(EtapaExecucao.NA_FILA_REPARO, T1);
    assertThat(e.dados().entradaFilaEm()).isEqualTo(T1);
    e.avancar(EtapaExecucao.EM_REPARO, T1);
    e.avancar(EtapaExecucao.REPARO_CONCLUIDO, T1);
    assertThat(e.getEtapa()).isEqualTo(EtapaExecucao.REPARO_CONCLUIDO);
    assertThat(e.dados().entradaFilaEm()).isEqualTo(T1);
    assertThat(e.getCorrelationId()).isEqualTo("corr");
  }

  @Test
  void transicaoInvalida() {
    assertThatThrownBy(() -> nova().avancar(EtapaExecucao.EM_REPARO, T1))
        .isInstanceOf(TransicaoInvalidaException.class)
        .hasMessageContaining("NA_FILA_DIAGNOSTICO");
  }

  @Test
  void refazerVoltaParaFila() {
    Execucao e = nova();
    e.avancar(EtapaExecucao.EM_DIAGNOSTICO, T0);
    e.concluirDiagnostico("x", T0);
    e.refazerDiagnostico(T1);
    assertThat(e.getEtapa()).isEqualTo(EtapaExecucao.NA_FILA_DIAGNOSTICO);
    assertThat(e.isRetrabalho()).isTrue();
  }

  @Test
  void guardaSoAsUltimasMensagens() {
    Execucao e = nova();
    for (int i = 0; i < Execucao.LIMITE_MENSAGENS + 5; i++) {
      e.registrarMensagem("m" + i);
    }
    assertThat(e.dados().mensagensProcessadas()).hasSize(Execucao.LIMITE_MENSAGENS);
    assertThat(e.jaProcessou("m0")).isFalse();
    assertThat(e.jaProcessou("m24")).isTrue();
  }

  @Test
  void restauraEstado() {
    Execucao.Dados d =
        new Execucao.Dados(
            "OS-2", null, null, null, null, EtapaExecucao.EM_REPARO, T0, T0, "d", true, 3, null);
    Execucao e = Execucao.restaurar(d);
    assertThat(e.getVersao()).isEqualTo(3);
    e.versaoSalva(4);
    assertThat(e.dados().versao()).isEqualTo(4);
    assertThat(e.dados().mensagensProcessadas()).isEqualTo(List.of());
  }
}
