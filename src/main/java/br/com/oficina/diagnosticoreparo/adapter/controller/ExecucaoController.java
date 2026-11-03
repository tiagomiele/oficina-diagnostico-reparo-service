package br.com.oficina.diagnosticoreparo.adapter.controller;

import br.com.oficina.diagnosticoreparo.domain.model.EtapaExecucao;
import br.com.oficina.diagnosticoreparo.usecase.ExecucaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ProblemDetail;
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
  @Operation(
      summary = "Fila do técnico por etapa, em ordem de chegada",
      description =
          "Use NA_FILA_DIAGNOSTICO para as OS que esperam diagnóstico e NA_FILA_REPARO para as que"
              + " já tiveram o orçamento aprovado.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Execuções da etapa, da mais antiga para a mais nova",
        content =
            @Content(
                array = @ArraySchema(schema = @Schema(implementation = ExecucaoResponse.class)),
                examples = @ExampleObject(value = Exemplos.FILA))),
    @ApiResponse(responseCode = "400", description = "Etapa inexistente", content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Sem token ou token inválido/expirado",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Token de cliente: só funcionário ou técnico",
        content = @Content)
  })
  public List<ExecucaoResponse> fila(
      @RequestParam(defaultValue = "NA_FILA_DIAGNOSTICO")
          @Parameter(description = "Etapa da fila", example = "NA_FILA_DIAGNOSTICO")
          EtapaExecucao etapa) {
    return servico.fila(etapa).stream().map(ExecucaoResponse::de).toList();
  }

  @GetMapping("/{numeroOs}")
  @Operation(summary = "Consulta a execução de uma OS")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Execução atual",
        content = @Content(examples = @ExampleObject(value = Exemplos.NA_FILA))),
    @ApiResponse(
        responseCode = "401",
        description = "Sem token ou token inválido/expirado",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Token de cliente: só funcionário ou técnico",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "OS sem execução (a Saga ainda não enviou INICIAR_DIAGNOSTICO)",
        content =
            @Content(
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = Exemplos.ERRO_404)))
  })
  public ExecucaoResponse buscar(
      @PathVariable @Parameter(description = "Número da OS", example = "OS-102026-000003")
          String numeroOs) {
    return ExecucaoResponse.de(servico.buscar(numeroOs));
  }

  @PostMapping("/{numeroOs}/iniciar-diagnostico")
  @Operation(
      summary = "Inicia o diagnóstico (evento DIAGNOSTICO_INICIADO)",
      description =
          "NA_FILA_DIAGNOSTICO → EM_DIAGNOSTICO. Publica DIAGNOSTICO_INICIADO na fila os-eventos"
              + " (não publica de novo no retrabalho).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Execução EM_DIAGNOSTICO",
        content = @Content(examples = @ExampleObject(value = Exemplos.EM_DIAGNOSTICO))),
    @ApiResponse(
        responseCode = "401",
        description = "Sem token ou token inválido/expirado",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Token de cliente: só funcionário ou técnico",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "OS sem execução (a Saga ainda não enviou INICIAR_DIAGNOSTICO)",
        content =
            @Content(
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = Exemplos.ERRO_404))),
    @ApiResponse(
        responseCode = "409",
        description = "Etapa fora de ordem ou alteração concorrente da mesma OS",
        content =
            @Content(
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = Exemplos.ERRO_409)))
  })
  public ExecucaoResponse iniciarDiagnostico(
      @PathVariable @Parameter(description = "Número da OS", example = "OS-102026-000003")
          String numeroOs) {
    return ExecucaoResponse.de(servico.iniciarDiagnostico(numeroOs));
  }

  @PostMapping("/{numeroOs}/concluir-diagnostico")
  @Operation(
      summary = "Conclui o diagnóstico (evento DIAGNOSTICO_CONCLUIDO)",
      description =
          "EM_DIAGNOSTICO → DIAGNOSTICO_CONCLUIDO. O laudo vai no evento; o OS Service monta os"
              + " itens e pede o orçamento ao serviço de Orçamento e Pagamento.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Execução DIAGNOSTICO_CONCLUIDO",
        content = @Content(examples = @ExampleObject(value = Exemplos.DIAGNOSTICO_CONCLUIDO))),
    @ApiResponse(
        responseCode = "400",
        description = "Laudo vazio ou com mais de 2000 caracteres",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Sem token ou token inválido/expirado",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Token de cliente: só funcionário ou técnico",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "OS sem execução (a Saga ainda não enviou INICIAR_DIAGNOSTICO)",
        content =
            @Content(
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = Exemplos.ERRO_404))),
    @ApiResponse(
        responseCode = "409",
        description = "Etapa fora de ordem ou alteração concorrente da mesma OS",
        content =
            @Content(
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = Exemplos.ERRO_409)))
  })
  public ExecucaoResponse concluirDiagnostico(
      @PathVariable @Parameter(description = "Número da OS", example = "OS-102026-000003")
          String numeroOs,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(examples = @ExampleObject(value = Exemplos.CONCLUIR_DIAGNOSTICO)))
          @Valid
          @RequestBody
          ConcluirDiagnosticoRequest request) {
    return ExecucaoResponse.de(servico.concluirDiagnostico(numeroOs, request.diagnostico()));
  }

  @PostMapping("/{numeroOs}/iniciar-reparo")
  @Operation(
      summary = "Inicia o reparo",
      description =
          "NA_FILA_REPARO → EM_REPARO (a OS chega à fila de reparo pelo comando INICIAR_REPARO).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Execução EM_REPARO",
        content = @Content(examples = @ExampleObject(value = Exemplos.EM_REPARO))),
    @ApiResponse(
        responseCode = "401",
        description = "Sem token ou token inválido/expirado",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Token de cliente: só funcionário ou técnico",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "OS sem execução (a Saga ainda não enviou INICIAR_DIAGNOSTICO)",
        content =
            @Content(
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = Exemplos.ERRO_404))),
    @ApiResponse(
        responseCode = "409",
        description = "Etapa fora de ordem ou alteração concorrente da mesma OS",
        content =
            @Content(
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = Exemplos.ERRO_409)))
  })
  public ExecucaoResponse iniciarReparo(
      @PathVariable @Parameter(description = "Número da OS", example = "OS-102026-000003")
          String numeroOs) {
    return ExecucaoResponse.de(servico.iniciarReparo(numeroOs));
  }

  @PostMapping("/{numeroOs}/concluir-reparo")
  @Operation(
      summary = "Conclui o reparo (evento REPARO_CONCLUIDO)",
      description = "EM_REPARO → REPARO_CONCLUIDO. O OS Service segue a Saga para o pagamento.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Execução REPARO_CONCLUIDO",
        content = @Content(examples = @ExampleObject(value = Exemplos.REPARO_CONCLUIDO))),
    @ApiResponse(
        responseCode = "401",
        description = "Sem token ou token inválido/expirado",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Token de cliente: só funcionário ou técnico",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "OS sem execução (a Saga ainda não enviou INICIAR_DIAGNOSTICO)",
        content =
            @Content(
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = Exemplos.ERRO_404))),
    @ApiResponse(
        responseCode = "409",
        description = "Etapa fora de ordem ou alteração concorrente da mesma OS",
        content =
            @Content(
                schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = Exemplos.ERRO_409)))
  })
  public ExecucaoResponse concluirReparo(
      @PathVariable @Parameter(description = "Número da OS", example = "OS-102026-000003")
          String numeroOs) {
    return ExecucaoResponse.de(servico.concluirReparo(numeroOs));
  }
}
