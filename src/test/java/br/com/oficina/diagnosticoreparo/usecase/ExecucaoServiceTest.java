package br.com.oficina.diagnosticoreparo.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.diagnosticoreparo.adapter.messaging.MemoriaMensageriaGateway;
import br.com.oficina.diagnosticoreparo.adapter.persistence.MemoriaExecucaoRepository;
import br.com.oficina.diagnosticoreparo.domain.model.EtapaExecucao;
import br.com.oficina.diagnosticoreparo.domain.model.TransicaoInvalidaException;
import br.com.oficina.diagnosticoreparo.usecase.saga.MensagemSaga;
import br.com.oficina.diagnosticoreparo.usecase.saga.TipoMensagemSaga;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecucaoServiceTest {

  private final MemoriaExecucaoRepository repo = new MemoriaExecucaoRepository();
  private final MemoriaMensageriaGateway eventos = new MemoriaMensageriaGateway();
  private final ExecucaoService servico =
      new ExecucaoService(
          repo, eventos, Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC));

  private static MensagemSaga comando(
      TipoMensagemSaga tipo, String os, Map<String, Object> payload) {
    return new MensagemSaga(
        UUID.randomUUID().toString(),
        tipo,
        "corr-" + os,
        os,
        "oficina-os-service",
        Instant.now(),
        payload);
  }

  private void abrir(String os) {
    assertThat(
            servico.processar(
                comando(
                    TipoMensagemSaga.INICIAR_DIAGNOSTICO,
                    os,
                    MensagemSaga.dados(
                        "idCliente", 5, "placa", "ABC1D23", "descricaoProblema", "freio"))))
        .isTrue();
  }

  private TipoMensagemSaga ultimoEvento(String os) {
    var lista = eventos.enviadasPara(os);
    return lista.get(lista.size() - 1).tipo();
  }

  @Test
  void fluxoFelizPublicaEventosComCorrelationId() {
    abrir("OS-1");
    assertThat(servico.fila(EtapaExecucao.NA_FILA_DIAGNOSTICO)).hasSize(1);
    servico.iniciarDiagnostico("OS-1");
    assertThat(ultimoEvento("OS-1")).isEqualTo(TipoMensagemSaga.DIAGNOSTICO_INICIADO);
    servico.concluirDiagnostico("OS-1", "trocar pastilhas");
    MensagemSaga concluido = eventos.enviadasPara("OS-1").get(1);
    assertThat(concluido.tipo()).isEqualTo(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO);
    assertThat(concluido.texto("diagnostico")).isEqualTo("trocar pastilhas");
    assertThat(concluido.correlationId()).isEqualTo("corr-OS-1");
    assertThat(concluido.origem()).isEqualTo(MensagemSaga.ORIGEM);
    assertThat(servico.processar(comando(TipoMensagemSaga.INICIAR_REPARO, "OS-1", null))).isTrue();
    assertThat(servico.fila(EtapaExecucao.NA_FILA_REPARO)).hasSize(1);
    servico.iniciarReparo("OS-1");
    servico.concluirReparo("OS-1");
    assertThat(ultimoEvento("OS-1")).isEqualTo(TipoMensagemSaga.REPARO_CONCLUIDO);
    assertThat(servico.buscar("OS-1").getEtapa()).isEqualTo(EtapaExecucao.REPARO_CONCLUIDO);
    assertThat(servico.buscar("OS-1").dados().idCliente()).isEqualTo(5L);
  }

  @Test
  void ignoraDuplicadosEMensagensDeOutroDestino() {
    MensagemSaga inicio = comando(TipoMensagemSaga.INICIAR_DIAGNOSTICO, "OS-2", null);
    assertThat(servico.processar(inicio)).isTrue();
    assertThat(servico.processar(inicio)).isFalse();
    assertThat(servico.processar(comando(TipoMensagemSaga.INICIAR_DIAGNOSTICO, "OS-2", null)))
        .isFalse();
    assertThat(servico.processar(comando(TipoMensagemSaga.GERAR_ORCAMENTO, "OS-2", null)))
        .isFalse();
    assertThat(servico.processar(comando(TipoMensagemSaga.INICIAR_REPARO, "OS-2", null))).isFalse();
    assertThat(servico.processar(comando(TipoMensagemSaga.INICIAR_REPARO, "OS-X", null))).isFalse();
    assertThat(servico.processar(comando(TipoMensagemSaga.CANCELAR_EXECUCAO, "OS-X", null)))
        .isFalse();
  }

  @Test
  void refazerDiagnosticoNaoRepeteDiagnosticoIniciado() {
    abrir("OS-3");
    servico.iniciarDiagnostico("OS-3");
    servico.concluirDiagnostico("OS-3", "laudo 1");
    assertThat(
            servico.processar(
                comando(
                    TipoMensagemSaga.INICIAR_DIAGNOSTICO,
                    "OS-3",
                    MensagemSaga.dados("refazer", true))))
        .isTrue();
    int antes = eventos.enviadasPara("OS-3").size();
    servico.iniciarDiagnostico("OS-3");
    assertThat(eventos.enviadasPara("OS-3")).hasSize(antes);
    servico.concluirDiagnostico("OS-3", "laudo 2");
    assertThat(ultimoEvento("OS-3")).isEqualTo(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO);
  }

  @Test
  void cancelamentoECompensacao() {
    abrir("OS-4");
    assertThat(servico.processar(comando(TipoMensagemSaga.CANCELAR_EXECUCAO, "OS-4", null)))
        .isTrue();
    assertThat(servico.buscar("OS-4").getEtapa()).isEqualTo(EtapaExecucao.CANCELADA);
    assertThat(servico.processar(comando(TipoMensagemSaga.CANCELAR_EXECUCAO, "OS-4", null)))
        .isFalse();
    assertThatThrownBy(() -> servico.iniciarDiagnostico("OS-4"))
        .isInstanceOf(TransicaoInvalidaException.class);
  }

  @Test
  void execucaoInexistente() {
    assertThatThrownBy(() -> servico.buscar("OS-404"))
        .isInstanceOf(ExecucaoNaoEncontradaException.class);
  }
}
