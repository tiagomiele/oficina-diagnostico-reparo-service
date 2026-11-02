package br.com.oficina.diagnosticoreparo.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String DESCRICAO =
      "Fila do técnico, diagnóstico e reparo da OS na Saga orquestrada pelo OS Service.\n\n"
          + "**Token (botão Authorize):** token de funcionário ou técnico do `POST /auth/login` do"
          + " OS Service. Token de cliente recebe 403.\n\n"
          + "**Fluxo do técnico:** `GET /execucoes/fila` → `iniciar-diagnostico` →"
          + " `concluir-diagnostico` → (cliente aprova o orçamento) → `GET"
          + " /execucoes/fila?etapa=NA_FILA_REPARO` → `iniciar-reparo` → `concluir-reparo`.\n\n"
          + "**Erros:** 400 corpo ou etapa inválidos, 401 sem token ou token expirado (15 min),"
          + " 403 perfil sem acesso, 404 OS sem execução, 409 etapa fora de ordem ou alteração"
          + " concorrente. 404/409 seguem o formato Problem Details (RFC 7807).";

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Serviço de Diagnóstico e Reparo")
                .version("v1")
                .description(DESCRICAO))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
