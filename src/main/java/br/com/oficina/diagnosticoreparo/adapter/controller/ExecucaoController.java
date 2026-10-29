package br.com.oficina.diagnosticoreparo.adapter.controller;

import br.com.oficina.diagnosticoreparo.domain.model.EtapaExecucao;
import br.com.oficina.diagnosticoreparo.usecase.ExecucaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/execucoes")
@Tag(name = "Técnico", description = "Fila, diagnóstico e reparo")
@SecurityRequirement(name = "bearerAuth")
public class ExecucaoController {

  private final ExecucaoService servico;

  public ExecucaoController(ExecucaoService servico) {
    this.servico = servico;
  }

  @GetMapping("/fila")
  @Operation(summary = "Fila do técnico por etapa, em ordem de chegada")
  public List<ExecucaoResponse> fila(
      @RequestParam(defaultValue = "NA_FILA_DIAGNOSTICO") EtapaExecucao etapa) {
    return servico.fila(etapa).stream().map(ExecucaoResponse::de).toList();
  }

  @GetMapping("/{numeroOs}")
  @Operation(summary = "Consulta a execução de uma OS")
  public ExecucaoResponse buscar(@PathVariable String numeroOs) {
    return ExecucaoResponse.de(servico.buscar(numeroOs));
  }

  @PostMapping("/{numeroOs}/iniciar-diagnostico")
  @Operation(summary = "Inicia o diagnóstico (evento DIAGNOSTICO_INICIADO)")
  public ExecucaoResponse iniciarDiagnostico(@PathVariable String numeroOs) {
    return ExecucaoResponse.de(servico.iniciarDiagnostico(numeroOs));
  }

  @PostMapping("/{numeroOs}/concluir-diagnostico")
  @Operation(summary = "Conclui o diagnóstico (evento DIAGNOSTICO_CONCLUIDO)")
  public ExecucaoResponse concluirDiagnostico(
      @PathVariable String numeroOs, @Valid @RequestBody ConcluirDiagnosticoRequest request) {
    return ExecucaoResponse.de(servico.concluirDiagnostico(numeroOs, request.diagnostico()));
  }

  @PostMapping("/{numeroOs}/iniciar-reparo")
  @Operation(summary = "Inicia o reparo")
  public ExecucaoResponse iniciarReparo(@PathVariable String numeroOs) {
    return ExecucaoResponse.de(servico.iniciarReparo(numeroOs));
  }

  @PostMapping("/{numeroOs}/concluir-reparo")
  @Operation(summary = "Conclui o reparo (evento REPARO_CONCLUIDO)")
  public ExecucaoResponse concluirReparo(@PathVariable String numeroOs) {
    return ExecucaoResponse.de(servico.concluirReparo(numeroOs));
  }
}
