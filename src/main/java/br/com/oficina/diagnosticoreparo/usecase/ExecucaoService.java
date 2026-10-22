package br.com.oficina.diagnosticoreparo.usecase;

import static br.com.oficina.diagnosticoreparo.usecase.saga.MensagemSaga.dados;

import br.com.oficina.diagnosticoreparo.domain.model.EtapaExecucao;
import br.com.oficina.diagnosticoreparo.domain.model.Execucao;
import br.com.oficina.diagnosticoreparo.domain.model.TransicaoInvalidaException;
import br.com.oficina.diagnosticoreparo.usecase.gateway.ExecucaoRepository;
import br.com.oficina.diagnosticoreparo.usecase.gateway.MensageriaGateway;
import br.com.oficina.diagnosticoreparo.usecase.saga.DestinoMensagem;
import br.com.oficina.diagnosticoreparo.usecase.saga.MensagemSaga;
import br.com.oficina.diagnosticoreparo.usecase.saga.TipoMensagemSaga;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Recebe os comandos da Saga e as ações do técnico, publicando os eventos para o OS Service. */
@Service
public class ExecucaoService {

  private static final Logger log = LoggerFactory.getLogger(ExecucaoService.class);

  private final ExecucaoRepository execucoes;
  private final MensageriaGateway mensageria;
  private final Clock clock;

  @Autowired
  public ExecucaoService(ExecucaoRepository execucoes, MensageriaGateway mensageria) {
    this(execucoes, mensageria, Clock.systemUTC());
  }

  ExecucaoService(ExecucaoRepository execucoes, MensageriaGateway mensageria, Clock clock) {
    this.execucoes = execucoes;
    this.mensageria = mensageria;
    this.clock = clock;
  }

  /**
   * Aplica um comando recebido da fila. Retorna {@code false} quando o comando é ignorado
   * (duplicado, fora de ordem ou destinado a outro serviço).
   */
  public boolean processar(MensagemSaga comando) {
    if (comando.tipo().destino() != DestinoMensagem.DIAGNOSTICO_REPARO) {
      log.warn(
          "Mensagem {} da OS {} não é comando deste serviço", comando.tipo(), comando.numeroOs());
      return false;
    }
    Optional<Execucao> atual = execucoes.buscar(comando.numeroOs());
    if (atual.isPresent() && atual.get().jaProcessou(comando.messageId())) {
      log.info("Comando {} já processado (messageId {})", comando.tipo(), comando.messageId());
      return false;
    }
    try {
      Optional<Execucao> alterada =
          switch (comando.tipo()) {
            case INICIAR_DIAGNOSTICO -> iniciarDiagnostico(comando, atual);
            case INICIAR_REPARO -> atual.map(this::entrarNaFilaDeReparo);
            default -> atual.flatMap(this::cancelar);
          };
      if (alterada.isEmpty()) {
        log.warn("Comando {} da OS {} ignorado", comando.tipo(), comando.numeroOs());
        return false;
      }
      Execucao execucao = alterada.get();
      execucao.registrarMensagem(comando.messageId());
      execucoes.salvar(execucao);
      log.info(
          "Comando {} aplicado: OS {} em {}",
          comando.tipo(),
          comando.numeroOs(),
          execucao.getEtapa());
      return true;
    } catch (TransicaoInvalidaException e) {
      log.warn(
          "Comando {} da OS {} fora de ordem: {}",
          comando.tipo(),
          comando.numeroOs(),
          e.getMessage());
      return false;
    }
  }

  private Optional<Execucao> iniciarDiagnostico(MensagemSaga comando, Optional<Execucao> atual) {
    if (atual.isEmpty()) {
      return Optional.of(
          Execucao.naFilaDeDiagnostico(
              comando.numeroOs(),
              comando.numero("idCliente"),
              comando.texto("placa"),
              comando.texto("descricaoProblema"),
              comando.correlationId(),
              clock.instant()));
    }
    if (!comando.verdadeiro("refazer")) {
      return Optional.empty();
    }
    Execucao execucao = atual.get();
    execucao.refazerDiagnostico(clock.instant());
    return Optional.of(execucao);
  }

  private Execucao entrarNaFilaDeReparo(Execucao execucao) {
    execucao.avancar(EtapaExecucao.NA_FILA_REPARO, clock.instant());
    return execucao;
  }

  private Optional<Execucao> cancelar(Execucao execucao) {
    if (execucao.getEtapa().isFinal()) {
      return Optional.empty();
    }
    execucao.avancar(EtapaExecucao.CANCELADA, clock.instant());
    return Optional.of(execucao);
  }

  public Execucao iniciarDiagnostico(String numeroOs) {
    Execucao execucao = avancar(numeroOs, EtapaExecucao.EM_DIAGNOSTICO);
    if (!execucao.isRetrabalho()) {
      publicar(TipoMensagemSaga.DIAGNOSTICO_INICIADO, execucao, Map.of());
    }
    return execucao;
  }

  public Execucao concluirDiagnostico(String numeroOs, String diagnostico) {
    Execucao execucao = buscar(numeroOs);
    execucao.concluirDiagnostico(diagnostico, clock.instant());
    execucoes.salvar(execucao);
    publicar(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO, execucao, dados("diagnostico", diagnostico));
    return execucao;
  }

  public Execucao iniciarReparo(String numeroOs) {
    return avancar(numeroOs, EtapaExecucao.EM_REPARO);
  }

  public Execucao concluirReparo(String numeroOs) {
    Execucao execucao = avancar(numeroOs, EtapaExecucao.REPARO_CONCLUIDO);
    publicar(TipoMensagemSaga.REPARO_CONCLUIDO, execucao, Map.of());
    return execucao;
  }

  public Execucao buscar(String numeroOs) {
    return execucoes
        .buscar(numeroOs)
        .orElseThrow(() -> new ExecucaoNaoEncontradaException(numeroOs));
  }

  public List<Execucao> fila(EtapaExecucao etapa) {
    return execucoes.listarPorEtapa(etapa);
  }

  private Execucao avancar(String numeroOs, EtapaExecucao destino) {
    Execucao execucao = buscar(numeroOs);
    execucao.avancar(destino, clock.instant());
    execucoes.salvar(execucao);
    return execucao;
  }

  private void publicar(TipoMensagemSaga tipo, Execucao execucao, Map<String, Object> payload) {
    mensageria.enviar(
        MensagemSaga.evento(tipo, execucao.getNumeroOs(), execucao.getCorrelationId(), payload));
  }
}
