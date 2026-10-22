package br.com.oficina.diagnosticoreparo.usecase.saga;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Envelope JSON trafegado nas filas SQS da Saga. */
public record MensagemSaga(
    String messageId,
    TipoMensagemSaga tipo,
    String correlationId,
    String numeroOs,
    String origem,
    Instant ocorridoEm,
    Map<String, Object> payload) {

  public static final String ORIGEM = "oficina-diagnostico-reparo-service";

  public MensagemSaga {
    Objects.requireNonNull(messageId, "messageId é obrigatório");
    Objects.requireNonNull(tipo, "tipo é obrigatório");
    Objects.requireNonNull(numeroOs, "numeroOs é obrigatório");
    payload =
        payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
  }

  public static MensagemSaga evento(
      TipoMensagemSaga tipo, String numeroOs, String correlationId, Map<String, Object> payload) {
    return new MensagemSaga(
        UUID.randomUUID().toString(),
        tipo,
        correlationId,
        numeroOs,
        ORIGEM,
        Instant.now(),
        payload);
  }

  /** Monta o payload a partir de pares chave/valor, ignorando valores nulos. */
  public static Map<String, Object> dados(Object... chaveValor) {
    if (chaveValor.length % 2 != 0) {
      throw new IllegalArgumentException("Informe pares chave/valor");
    }
    Map<String, Object> dados = new LinkedHashMap<>();
    for (int i = 0; i < chaveValor.length; i += 2) {
      if (chaveValor[i + 1] != null) {
        dados.put(String.valueOf(chaveValor[i]), chaveValor[i + 1]);
      }
    }
    return dados;
  }

  public MensagemSaga comCorrelationId(String novoCorrelationId) {
    return new MensagemSaga(
        messageId, tipo, novoCorrelationId, numeroOs, origem, ocorridoEm, payload);
  }

  public String texto(String chave) {
    Object valor = payload.get(chave);
    return valor == null ? null : valor.toString();
  }

  public Long numero(String chave) {
    String valor = texto(chave);
    return valor == null ? null : Long.valueOf(valor);
  }

  public boolean verdadeiro(String chave) {
    return Boolean.parseBoolean(texto(chave));
  }
}
