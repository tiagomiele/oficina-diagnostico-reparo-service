package br.com.oficina.diagnosticoreparo.adapter.persistence;

import br.com.oficina.diagnosticoreparo.domain.model.EtapaExecucao;
import br.com.oficina.diagnosticoreparo.domain.model.Execucao;
import br.com.oficina.diagnosticoreparo.usecase.gateway.ExecucaoRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Persistência local para desenvolvimento e testes, sem DynamoDB. */
@Component
@ConditionalOnProperty(
    name = "oficina.persistencia.tipo",
    havingValue = "memoria",
    matchIfMissing = true)
public class MemoriaExecucaoRepository implements ExecucaoRepository {

  private final Map<String, Execucao.Dados> itens = new ConcurrentHashMap<>();

  @Override
  public Optional<Execucao> buscar(String numeroOs) {
    return Optional.ofNullable(itens.get(numeroOs)).map(Execucao::restaurar);
  }

  @Override
  public void salvar(Execucao execucao) {
    execucao.versaoSalva(execucao.getVersao() + 1);
    itens.put(execucao.getNumeroOs(), execucao.dados());
  }

  @Override
  public List<Execucao> listarPorEtapa(EtapaExecucao etapa) {
    return itens.values().stream()
        .filter(d -> d.etapa() == etapa)
        .sorted(Comparator.comparing(Execucao.Dados::entradaFilaEm))
        .map(Execucao::restaurar)
        .toList();
  }
}
