package br.com.oficina.diagnosticoreparo.adapter.messaging;

import br.com.oficina.diagnosticoreparo.usecase.gateway.MensageriaGateway;
import br.com.oficina.diagnosticoreparo.usecase.saga.MensagemSaga;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Envia os eventos da Saga para a fila os-eventos, depois que a execução foi gravada. */
@Component
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "sqs")
public class SqsMensageriaGateway implements MensageriaGateway {

  private static final Logger log = LoggerFactory.getLogger(SqsMensageriaGateway.class);

  private final SqsClient sqs;
  private final ObjectMapper mapper;
  private final String fila;

  public SqsMensageriaGateway(
      SqsClient sqs,
      ObjectMapper mapper,
      @Value("${oficina.mensageria.sqs.os-eventos-url:}") String fila) {
    this.sqs = sqs;
    this.mapper = mapper;
    this.fila = fila;
  }

  @Override
  public void enviar(MensagemSaga mensagem) {
    MensagemSaga comCorrelacao = CorrelationIds.aplicar(mensagem);
    publicar(comCorrelacao);
  }

  void publicar(MensagemSaga mensagem) {
    if (fila == null || fila.isBlank()) {
      throw new IllegalStateException("SQS_OS_EVENTOS_URL não configurada");
    }
    sqs.sendMessage(
        SendMessageRequest.builder()
            .queueUrl(fila)
            .messageBody(json(mensagem))
            .messageAttributes(
                Map.of(
                    "tipo", atributo(mensagem.tipo().name()),
                    "correlationId", atributo(mensagem.correlationId())))
            .build());
    log.info(
        "Evento {} da OS {} enviado (messageId {})",
        mensagem.tipo(),
        mensagem.numeroOs(),
        mensagem.messageId());
  }

  private String json(MensagemSaga mensagem) {
    try {
      return mapper.writeValueAsString(mensagem);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Falha ao serializar mensagem da Saga", e);
    }
  }

  private static MessageAttributeValue atributo(String valor) {
    return MessageAttributeValue.builder().dataType("String").stringValue(valor).build();
  }
}
