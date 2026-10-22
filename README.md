# oficina-diagnostico-reparo-service
oficina-diagnostico-reparo-service

# Serviço de Diagnóstico e Reparo

Fila do técnico, diagnóstico e reparo do veículo com DynamoDB (Tech Challenge Fase 4 - SOAT).

Esqueleto criado na Fase 0 (Fundação). As regras de negócio, persistência e mensageria entram nas próximas fases.

## Arquitetura

Clean Architecture com 4 camadas, validada por ArchUnit (`ArchitectureTest`):

| Pacote | Responsabilidade |
|---|---|
| `br.com.oficina.diagnosticoreparo.domain` | Regras de negócio puras (sem Spring) |
| `br.com.oficina.diagnosticoreparo.usecase` | Casos de uso e portas (gateways) |
| `br.com.oficina.diagnosticoreparo.adapter` | Controllers, persistência, mensageria e integrações |
| `br.com.oficina.diagnosticoreparo.infrastructure` | Configuração e composição |

## Build local

```powershell
./mvnw spotless:apply
./mvnw verify
```

`verify` roda testes, ArchUnit e o gate do JaCoCo (80% de linhas e branches). O relatório fica em `target/site/jacoco/index.html`.

## Endpoints

- `GET /actuator/health`
- `GET /swagger-ui.html`

## CI

`.github/workflows/ci.yml`: validação dos workflows, build com testes e JaCoCo, SonarCloud (quality gate, quando o secret `SONAR_TOKEN` existir), SBOM CycloneDX e Trivy + Gitleaks.
