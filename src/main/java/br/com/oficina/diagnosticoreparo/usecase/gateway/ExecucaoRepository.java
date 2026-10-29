package br.com.oficina.diagnosticoreparo.usecase.gateway;

import br.com.oficina.diagnosticoreparo.domain.model.EtapaExecucao;
import br.com.oficina.diagnosticoreparo.domain.model.Execucao;
import java.util.List;
import java.util.Optional;

public interface ExecucaoRepository {

  Optional<Execucao> buscar(String numeroOs);

  /** Grava com controle otimista pela versão; falha se outra escrita chegou antes. */
  void salvar(Execucao execucao);

  /** Fila do técnico: execuções na etapa, por ordem de chegada. */
  List<Execucao> listarPorEtapa(EtapaExecucao etapa);
}
