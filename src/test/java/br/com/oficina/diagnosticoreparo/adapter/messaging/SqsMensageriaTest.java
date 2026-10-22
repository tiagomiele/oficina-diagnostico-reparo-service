package br.com.oficina.diagnosticoreparo.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.oficina.diagnosticoreparo.usecase.ExecucaoService;
import br.com.oficina.diagnosticoreparo.usecase.saga.MensagemSaga;
import br.com.oficina.diagnosticoreparo.usecase.saga.TipoMensagemSaga;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class SqsMensageriaTest {

  private final SqsClient sqs = mock(SqsClient.class);
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final ExecucaoService servico = mock(ExecucaoService.class);

  @AfterEach
  void limparMdc() {
    MDC.clear();
  }

  @Test
  void enviaEventoParaOsEventosComCorrelationIdDoMdc() throws Exception {
    MDC.put("correlationId", "corr-mdc");
    new SqsMensageriaGateway(sqs, mapper, "http://fila/os-eventos")
        .enviar(MensagemSaga.evento(TipoMensagemSaga.REPARO_CONCLUIDO, "OS-1", null, null));
    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
    verify(sqs).sendMessage(captor.capture());
    assertThat(captor.getValue().queueUrl()).isEqualTo("http://fila/os-eventos");
    assertThat(captor.getValue().messageAttributes().get("correlationId").stringValue())
        .isEqualTo("corr-mdc");
    assertThat(mapper.readValue(captor.getValue().messageBody(), MensagemSaga.class).tipo())
        .isEqualTo(TipoMensagemSaga.REPARO_CONCLUIDO);
  }

  @Test
  void falhaSemFilaConfigurada() {
    SqsMensageriaGateway gateway = new SqsMensageriaGateway(sqs, mapper, " ");
    MensagemSaga m = MensagemSaga.evento(TipoMensagemSaga.REPARO_CONCLUIDO, "OS-1", "c", null);
    assertThatThrownBy(() -> gateway.enviar(m)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new SqsMensageriaGateway(sqs, mapper, null).enviar(m))
        .isInstanceOf(IllegalStateException.class);
  }

  @SuppressWarnings("unchecked")
  @Test
  void consumidorApagaSoMensagensProcessadas() throws Exception {
    String ok =
        mapper.writeValueAsString(
            MensagemSaga.evento(TipoMensagemSaga.INICIAR_DIAGNOSTICO, "OS-1", "c", null));
    String erro =
        mapper.writeValueAsString(
            MensagemSaga.evento(TipoMensagemSaga.INICIAR_REPARO, "OS-2", null, null));
    when(servico.processar(any()))
        .thenAnswer(
            inv -> {
              MensagemSaga m = inv.getArgument(0);
              assertThat(MDC.get("correlationId")).isNotBlank();
              if (m.numeroOs().equals("OS-2")) {
                throw new IllegalStateException("falha");
              }
              return true;
            });
    when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(
            ReceiveMessageResponse.builder()
                .messages(
                    Message.builder().messageId("1").receiptHandle("r1").body(ok).build(),
                    Message.builder().messageId("2").receiptHandle("r2").body(erro).build(),
                    Message.builder().messageId("3").receiptHandle("r3").body("{lixo").build())
                .build());
    new SqsComandosConsumidor(sqs, mapper, servico, "http://fila/comandos", 10, 1).consumir();
    ArgumentCaptor<DeleteMessageRequest> captor =
        ArgumentCaptor.forClass(DeleteMessageRequest.class);
    verify(sqs).deleteMessage(captor.capture());
    assertThat(captor.getValue().receiptHandle()).isEqualTo("r1");
    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void consumidorSemFilaNaoFazNada() {
    new SqsComandosConsumidor(sqs, mapper, servico, "", 10, 1).consumir();
    new SqsComandosConsumidor(sqs, mapper, servico, null, 10, 1).consumir();
    verifyNoInteractions(sqs);
  }

  @Test
  void memoriaGuardaNoMaximoOLimite() {
    MemoriaMensageriaGateway memoria = new MemoriaMensageriaGateway();
    for (int i = 0; i <= MemoriaMensageriaGateway.LIMITE; i++) {
      memoria.enviar(MensagemSaga.evento(TipoMensagemSaga.REPARO_CONCLUIDO, "OS-" + i, null, null));
    }
    assertThat(memoria.enviadas()).hasSize(MemoriaMensageriaGateway.LIMITE);
    assertThat(memoria.enviadasPara("OS-0")).isEmpty();
    assertThat(memoria.enviadas().get(0).correlationId()).isNotBlank();
  }
}
