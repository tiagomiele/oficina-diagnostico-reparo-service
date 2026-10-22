package br.com.oficina.diagnosticoreparo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import br.com.oficina.diagnosticoreparo.domain.model.EtapaExecucao;
import br.com.oficina.diagnosticoreparo.domain.model.Execucao;
import br.com.oficina.diagnosticoreparo.usecase.ExecucaoService;
import br.com.oficina.diagnosticoreparo.usecase.gateway.ExecucaoRepository;
import br.com.oficina.diagnosticoreparo.usecase.saga.MensagemSaga;
import br.com.oficina.diagnosticoreparo.usecase.saga.TipoMensagemSaga;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/** DynamoDB e SQS do LocalStack: comando consumido, execução gravada e evento publicado. */
@SpringBootTest
class LocalStackIntegrationTest {

  private static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.DYNAMODB);
  private static final String TABELA = "oficina-test-execucoes";
  private static final String INDICE = "etapa-entradaFilaEm-index";
  private static final SqsClient SQS;
  private static final String OS_EVENTOS;
  private static final String COMANDOS;

  static {
    LOCALSTACK.start();
    var credenciais = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    SQS =
        SqsClient.builder()
            .endpointOverride(LOCALSTACK.getEndpoint())
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(credenciais)
            .build();
    OS_EVENTOS = SQS.createQueue(b -> b.queueName("os-eventos")).queueUrl();
    COMANDOS = SQS.createQueue(b -> b.queueName("diagnostico-reparo-comandos")).queueUrl();
    try (DynamoDbClient dynamo =
        DynamoDbClient.builder()
            .endpointOverride(LOCALSTACK.getEndpoint())
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(credenciais)
            .build()) {
      dynamo.createTable(
          b ->
              b.tableName(TABELA)
                  .billingMode(BillingMode.PAY_PER_REQUEST)
                  .attributeDefinitions(
                      atributo("numeroOs"), atributo("etapa"), atributo("entradaFilaEm"))
                  .keySchema(chave("numeroOs", KeyType.HASH))
                  .globalSecondaryIndexes(
                      GlobalSecondaryIndex.builder()
                          .indexName(INDICE)
                          .keySchema(
                              chave("etapa", KeyType.HASH), chave("entradaFilaEm", KeyType.RANGE))
                          .projection(
                              Projection.builder().projectionType(ProjectionType.ALL).build())
                          .build()));
    }
  }

  private static AttributeDefinition atributo(String nome) {
    return AttributeDefinition.builder()
        .attributeName(nome)
        .attributeType(ScalarAttributeType.S)
        .build();
  }

  private static KeySchemaElement chave(String nome, KeyType tipo) {
    return KeySchemaElement.builder().attributeName(nome).keyType(tipo).build();
  }

  @DynamicPropertySource
  static void propriedades(DynamicPropertyRegistry registry) {
    String endpoint = LOCALSTACK.getEndpoint().toString();
    registry.add("oficina.mensageria.tipo", () -> "sqs");
    registry.add("oficina.mensageria.sqs.endpoint", () -> endpoint);
    registry.add("oficina.mensageria.sqs.region", LOCALSTACK::getRegion);
    registry.add("oficina.mensageria.sqs.os-eventos-url", () -> OS_EVENTOS);
    registry.add("oficina.mensageria.sqs.comandos-url", () -> COMANDOS);
    registry.add("oficina.mensageria.sqs.wait-time-seconds", () -> "1");
    registry.add("oficina.mensageria.sqs.poll-delay-ms", () -> "200");
    registry.add("oficina.persistencia.tipo", () -> "dynamodb");
    registry.add("oficina.persistencia.dynamodb.endpoint", () -> endpoint);
    registry.add("oficina.persistencia.dynamodb.region", LOCALSTACK::getRegion);
    registry.add("oficina.persistencia.dynamodb.table-name", () -> TABELA);
  }

  @Autowired private ExecucaoService servico;
  @Autowired private ExecucaoRepository repo;
  @Autowired private ObjectMapper mapper;

  @Test
  void comandoDaFilaGravaNoDynamoEPublicaEvento() throws Exception {
    String os = "OS-LS-" + UUID.randomUUID();
    MensagemSaga comando =
        new MensagemSaga(
            UUID.randomUUID().toString(),
            TipoMensagemSaga.INICIAR_DIAGNOSTICO,
            "corr-ls",
            os,
            "oficina-os-service",
            Instant.now(),
            MensagemSaga.dados("idCliente", 9, "placa", "XYZ9A88", "descricaoProblema", "ruído"));
    String corpo = mapper.writeValueAsString(comando);
    SQS.sendMessage(b -> b.queueUrl(COMANDOS).messageBody(corpo));

    await().atMost(Duration.ofSeconds(30)).until(() -> repo.buscar(os).isPresent());
    Execucao gravada = repo.buscar(os).orElseThrow();
    assertThat(gravada.dados().placa()).isEqualTo("XYZ9A88");
    assertThat(gravada.jaProcessou(comando.messageId())).isTrue();
    assertThat(repo.listarPorEtapa(EtapaExecucao.NA_FILA_DIAGNOSTICO))
        .extracting(Execucao::getNumeroOs)
        .contains(os);

    servico.iniciarDiagnostico(os);
    List<Message> eventos =
        SQS.receiveMessage(b -> b.queueUrl(OS_EVENTOS).maxNumberOfMessages(10).waitTimeSeconds(5))
            .messages();
    MensagemSaga evento = mapper.readValue(eventos.get(0).body(), MensagemSaga.class);
    assertThat(evento.tipo()).isEqualTo(TipoMensagemSaga.DIAGNOSTICO_INICIADO);
    assertThat(evento.correlationId()).isEqualTo("corr-ls");
  }

  @Test
  void escritaConcorrenteFalha() {
    String os = "OS-CC-" + UUID.randomUUID();
    repo.salvar(Execucao.naFilaDeDiagnostico(os, null, null, null, null, Instant.now()));
    Execucao a = repo.buscar(os).orElseThrow();
    Execucao b = repo.buscar(os).orElseThrow();
    a.avancar(EtapaExecucao.EM_DIAGNOSTICO, Instant.now());
    repo.salvar(a);
    b.avancar(EtapaExecucao.CANCELADA, Instant.now());
    assertThatThrownBy(() -> repo.salvar(b)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                repo.salvar(
                    Execucao.naFilaDeDiagnostico(os, null, null, null, null, Instant.now())))
        .isInstanceOf(IllegalStateException.class);
    assertThat(repo.buscar(os).orElseThrow().getEtapa()).isEqualTo(EtapaExecucao.EM_DIAGNOSTICO);
    assertThat(repo.buscar("OS-NADA")).isEmpty();
  }
}
