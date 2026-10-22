package br.com.oficina.diagnosticoreparo.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EtapaExecucaoTest {

  private static final Map<EtapaExecucao, Set<EtapaExecucao>> TRANSICOES =
      Map.of(
          EtapaExecucao.NA_FILA_DIAGNOSTICO,
          EnumSet.of(EtapaExecucao.EM_DIAGNOSTICO, EtapaExecucao.CANCELADA),
          EtapaExecucao.EM_DIAGNOSTICO,
          EnumSet.of(EtapaExecucao.DIAGNOSTICO_CONCLUIDO, EtapaExecucao.CANCELADA),
          EtapaExecucao.DIAGNOSTICO_CONCLUIDO,
          EnumSet.of(
              EtapaExecucao.NA_FILA_REPARO,
              EtapaExecucao.NA_FILA_DIAGNOSTICO,
              EtapaExecucao.CANCELADA),
          EtapaExecucao.NA_FILA_REPARO,
          EnumSet.of(EtapaExecucao.EM_REPARO, EtapaExecucao.CANCELADA),
          EtapaExecucao.EM_REPARO,
          EnumSet.of(EtapaExecucao.REPARO_CONCLUIDO, EtapaExecucao.CANCELADA));

  @ParameterizedTest
  @EnumSource(EtapaExecucao.class)
  void respeitaAsTransicoesPermitidas(EtapaExecucao origem) {
    Set<EtapaExecucao> permitidas = TRANSICOES.getOrDefault(origem, Set.of());
    for (EtapaExecucao destino : EtapaExecucao.values()) {
      assertThat(origem.podeAvancarPara(destino)).isEqualTo(permitidas.contains(destino));
    }
    assertThat(origem.isFinal()).isEqualTo(permitidas.isEmpty());
  }

  @Test
  void naoAvancaParaDestinoNulo() {
    assertThat(EtapaExecucao.NA_FILA_DIAGNOSTICO.podeAvancarPara(null)).isFalse();
    assertThat(EtapaExecucao.NA_FILA_DIAGNOSTICO.podeAvancarPara(EtapaExecucao.EM_DIAGNOSTICO))
        .isTrue();
  }
}
