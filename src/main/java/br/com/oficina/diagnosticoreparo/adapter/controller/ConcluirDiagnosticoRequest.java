package br.com.oficina.diagnosticoreparo.adapter.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    description = "Laudo do técnico; vai no evento DIAGNOSTICO_CONCLUIDO para gerar o orçamento")
public record ConcluirDiagnosticoRequest(
    @Schema(
            description = "Diagnóstico do veículo (obrigatório, até 2000 caracteres)",
            example = "Pastilhas de freio gastas; trocar pastilhas dianteiras",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 2000)
        @NotBlank
        @Size(max = 2000)
        String diagnostico) {}
