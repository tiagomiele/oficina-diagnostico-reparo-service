package br.com.oficina.diagnosticoreparo.usecase.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MensagemSagaTest {

  @Test
  void eventoTemIdOrigemEPayloadImutavel() {
    MensagemSaga m =
        MensagemSaga.evento(
            TipoMensagemSaga.REPARO_CONCLUIDO, "OS-1", "c", MensagemSaga.dados("a", 1, "b", null));
    assertThat(m.messageId()).isNotBlank();
    assertThat(m.origem()).isEqualTo(MensagemSaga.ORIGEM);
    assertThat(m.payload()).containsOnlyKeys("a");
    assertThat(m.numero("a")).isEqualTo(1L);
    assertThat(m.numero("x")).isNull();
    assertThat(m.texto("x")).isNull();
    assertThat(m.verdadeiro("x")).isFalse();
    assertThat(m.comCorrelationId("z").correlationId()).isEqualTo("z");
    assertThatThrownBy(() -> m.payload().put("c", 2))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(MensagemSaga.evento(TipoMensagemSaga.REPARO_CONCLUIDO, "OS-1", null, null).payload())
        .isEqualTo(Map.of());
  }

  @Test
  void validaCamposEPares() {
    assertThatThrownBy(() -> MensagemSaga.dados("a")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new MensagemSaga(
                    null, TipoMensagemSaga.REPARO_CONCLUIDO, null, "OS", null, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThat(TipoMensagemSaga.INICIAR_REPARO.destino())
        .isEqualTo(DestinoMensagem.DIAGNOSTICO_REPARO);
  }
}
