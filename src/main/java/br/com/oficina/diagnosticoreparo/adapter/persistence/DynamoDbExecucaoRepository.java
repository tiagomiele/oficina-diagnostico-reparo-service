package br.com.oficina.diagnosticoreparo.adapter.persistence;

import br.com.oficina.diagnosticoreparo.domain.model.EtapaExecucao;
import br.com.oficina.diagnosticoreparo.domain.model.Execucao;
import br.com.oficina.diagnosticoreparo.usecase.gateway.ExecucaoRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

/**
 * Tabela {@code oficina-<env>-execucoes}: chave {@code numeroOs}; índice {@code
 * etapa-entradaFilaEm-index} lista a fila do técnico. A escrita é condicional pela {@code versao}
 * (controle otimista).
 */
@Component
@ConditionalOnProperty(name = "oficina.persistencia.tipo", havingValue = "dynamodb")
public class DynamoDbExecucaoRepository implements ExecucaoRepository {

  private final DynamoDbClient dynamo;
  private final String tabela;
  private final String indiceFila;

  public DynamoDbExecucaoRepository(
      DynamoDbClient dynamo,
      @Value("${oficina.persistencia.dynamodb.table-name}") String tabela,
      @Value("${oficina.persistencia.dynamodb.fila-index}") String indiceFila) {
    this.dynamo = dynamo;
    this.tabela = tabela;
    this.indiceFila = indiceFila;
  }

  @Override
  public Optional<Execucao> buscar(String numeroOs) {
    Map<String, AttributeValue> item =
        dynamo
            .getItem(
                b -> b.tableName(tabela).consistentRead(true).key(Map.of("numeroOs", s(numeroOs))))
            .item();
    return item == null || item.isEmpty() ? Optional.empty() : Optional.of(paraDominio(item));
  }

  @Override
  public void salvar(Execucao execucao) {
    long versaoAtual = execucao.getVersao();
    long novaVersao = versaoAtual + 1;
    Map<String, AttributeValue> item = paraItem(execucao.dados(), novaVersao);
    PutItemRequest.Builder put = PutItemRequest.builder().tableName(tabela).item(item);
    if (versaoAtual == 0) {
      put.conditionExpression("attribute_not_exists(numeroOs)");
    } else {
      put.conditionExpression("versao = :v")
          .expressionAttributeValues(Map.of(":v", n(versaoAtual)));
    }
    try {
      dynamo.putItem(put.build());
    } catch (ConditionalCheckFailedException e) {
      throw new IllegalStateException(
          "Execução da OS " + execucao.getNumeroOs() + " alterada por outra requisição", e);
    }
    execucao.versaoSalva(novaVersao);
  }

  @Override
  public List<Execucao> listarPorEtapa(EtapaExecucao etapa) {
    return dynamo
        .query(
            QueryRequest.builder()
                .tableName(tabela)
                .indexName(indiceFila)
                .keyConditionExpression("etapa = :e")
                .expressionAttributeValues(Map.of(":e", s(etapa.name())))
                .scanIndexForward(true)
                .build())
        .items()
        .stream()
        .map(DynamoDbExecucaoRepository::paraDominio)
        .toList();
  }

  static Map<String, AttributeValue> paraItem(Execucao.Dados d, long versao) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put("numeroOs", s(d.numeroOs()));
    item.put("etapa", s(d.etapa().name()));
    item.put("retrabalho", AttributeValue.fromBool(d.retrabalho()));
    item.put("versao", n(versao));
    item.put(
        "mensagensProcessadas",
        AttributeValue.fromL(
            d.mensagensProcessadas().stream().map(DynamoDbExecucaoRepository::s).toList()));
    opcional(item, "idCliente", d.idCliente() == null ? null : n(d.idCliente()));
    opcional(item, "placa", texto(d.placa()));
    opcional(item, "descricaoProblema", texto(d.descricaoProblema()));
    opcional(item, "correlationId", texto(d.correlationId()));
    opcional(item, "diagnostico", texto(d.diagnostico()));
    opcional(item, "entradaFilaEm", instante(d.entradaFilaEm()));
    opcional(item, "atualizadoEm", instante(d.atualizadoEm()));
    return item;
  }

  static Execucao paraDominio(Map<String, AttributeValue> item) {
    return Execucao.restaurar(
        new Execucao.Dados(
            item.get("numeroOs").s(),
            item.containsKey("idCliente") ? Long.valueOf(item.get("idCliente").n()) : null,
            ler(item, "placa"),
            ler(item, "descricaoProblema"),
            ler(item, "correlationId"),
            EtapaExecucao.valueOf(item.get("etapa").s()),
            lerInstante(item, "entradaFilaEm"),
            lerInstante(item, "atualizadoEm"),
            ler(item, "diagnostico"),
            Boolean.TRUE.equals(item.get("retrabalho").bool()),
            Long.parseLong(item.get("versao").n()),
            item.get("mensagensProcessadas").l().stream().map(AttributeValue::s).toList()));
  }

  private static void opcional(
      Map<String, AttributeValue> item, String nome, AttributeValue valor) {
    if (valor != null) {
      item.put(nome, valor);
    }
  }

  private static AttributeValue texto(String valor) {
    return valor == null ? null : s(valor);
  }

  private static AttributeValue instante(Instant valor) {
    return valor == null ? null : s(valor.toString());
  }

  private static String ler(Map<String, AttributeValue> item, String nome) {
    AttributeValue valor = item.get(nome);
    return valor == null ? null : valor.s();
  }

  private static Instant lerInstante(Map<String, AttributeValue> item, String nome) {
    String valor = ler(item, nome);
    return valor == null ? null : Instant.parse(valor);
  }

  private static AttributeValue s(String valor) {
    return AttributeValue.fromS(valor);
  }

  private static AttributeValue n(long valor) {
    return AttributeValue.fromN(Long.toString(valor));
  }
}
