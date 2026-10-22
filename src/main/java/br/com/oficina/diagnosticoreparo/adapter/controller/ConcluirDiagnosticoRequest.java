package br.com.oficina.diagnosticoreparo.adapter.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConcluirDiagnosticoRequest(@NotBlank @Size(max = 2000) String diagnostico) {}
