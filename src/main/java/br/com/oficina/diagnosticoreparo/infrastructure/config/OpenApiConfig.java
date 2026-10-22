package br.com.oficina.diagnosticoreparo.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI openApi() {
    return new OpenAPI().info(new Info().title("Serviço de Diagnóstico e Reparo").version("v1"));
  }
}
