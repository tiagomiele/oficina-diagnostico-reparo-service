package br.com.oficina.diagnosticoreparo.adapter.messaging;

import br.com.oficina.diagnosticoreparo.usecase.saga.MensagemSaga;
import java.util.UUID;
import org.slf4j.MDC;

/** Coloca o correlationId da Saga no MDC (chaves requestId e correlationId) dos logs. */
final class CorrelationIds {

  static final String MDC_REQUEST_ID = "requestId";
  static final String MDC_CORRELATION_ID = "correlationId";

  private CorrelationIds() {}

  static MensagemSaga aplicar(MensagemSaga mensagem) {
    if (mensagem.correlationId() != null && !mensagem.correlationId().isBlank()) {
      return mensagem;
    }
    String atual = MDC.get(MDC_CORRELATION_ID);
    return mensagem.comCorrelationId(
        atual == null || atual.isBlank() ? UUID.randomUUID().toString() : atual);
  }

  static void abrir(MensagemSaga mensagem) {
    String id =
        mensagem.correlationId() == null || mensagem.correlationId().isBlank()
            ? mensagem.messageId()
            : mensagem.correlationId();
    MDC.put(MDC_REQUEST_ID, id);
    MDC.put(MDC_CORRELATION_ID, id);
  }

  static void fechar() {
    MDC.remove(MDC_REQUEST_ID);
    MDC.remove(MDC_CORRELATION_ID);
  }
}
