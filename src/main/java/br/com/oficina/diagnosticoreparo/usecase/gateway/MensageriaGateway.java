package br.com.oficina.diagnosticoreparo.usecase.gateway;

import br.com.oficina.diagnosticoreparo.usecase.saga.MensagemSaga;

/** Publica os eventos da Saga na fila os-eventos do OS Service. */
public interface MensageriaGateway {
  void enviar(MensagemSaga mensagem);
}
