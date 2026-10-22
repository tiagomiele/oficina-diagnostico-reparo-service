package br.com.oficina.diagnosticoreparo.adapter.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oficina.diagnosticoreparo.support.Tokens;
import br.com.oficina.diagnosticoreparo.usecase.ExecucaoService;
import br.com.oficina.diagnosticoreparo.usecase.saga.MensagemSaga;
import br.com.oficina.diagnosticoreparo.usecase.saga.TipoMensagemSaga;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ExecucaoControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ExecucaoService servico;

  private final String tecnico = "Bearer " + Tokens.interno("TECNICO_DA_OFICINA");
  private final String funcionario = "Bearer " + Tokens.interno("FUNCIONARIO_DA_OFICINA");

  private void abrir(String os) {
    servico.processar(
        new MensagemSaga(
            "m-" + os,
            TipoMensagemSaga.INICIAR_DIAGNOSTICO,
            "c",
            os,
            "oficina-os-service",
            Instant.now(),
            null));
  }

  @Test
  void fluxoDoTecnico() throws Exception {
    abrir("OS-HTTP-1");
    mvc.perform(get("/execucoes/fila").header("Authorization", tecnico))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.numeroOs=='OS-HTTP-1')]", hasSize(1)));
    mvc.perform(post("/execucoes/OS-HTTP-1/iniciar-diagnostico").header("Authorization", tecnico))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.etapa").value("EM_DIAGNOSTICO"));
    mvc.perform(
            post("/execucoes/OS-HTTP-1/concluir-diagnostico")
                .header("Authorization", funcionario)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnostico\":\"trocar óleo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diagnostico").value("trocar óleo"));
    servico.processar(
        new MensagemSaga(
            "r-1",
            TipoMensagemSaga.INICIAR_REPARO,
            "c",
            "OS-HTTP-1",
            "oficina-os-service",
            Instant.now(),
            null));
    mvc.perform(post("/execucoes/OS-HTTP-1/iniciar-reparo").header("Authorization", tecnico))
        .andExpect(status().isOk());
    mvc.perform(post("/execucoes/OS-HTTP-1/concluir-reparo").header("Authorization", tecnico))
        .andExpect(jsonPath("$.etapa").value("REPARO_CONCLUIDO"));
    mvc.perform(get("/execucoes/OS-HTTP-1").header("Authorization", tecnico))
        .andExpect(jsonPath("$.etapa").value("REPARO_CONCLUIDO"));
  }

  @Test
  void errosEAutorizacao() throws Exception {
    mvc.perform(get("/execucoes/fila")).andExpect(status().isUnauthorized());
    mvc.perform(get("/execucoes/fila").header("Authorization", "Bearer invalido"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/execucoes/fila").header("Authorization", "Bearer " + Tokens.cliente(1L)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/execucoes/OS-NAO-EXISTE").header("Authorization", tecnico))
        .andExpect(status().isNotFound());
    abrir("OS-HTTP-2");
    mvc.perform(post("/execucoes/OS-HTTP-2/concluir-reparo").header("Authorization", tecnico))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/execucoes/OS-HTTP-2/concluir-diagnostico")
                .header("Authorization", tecnico)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnostico\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
