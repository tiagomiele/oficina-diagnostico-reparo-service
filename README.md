# oficina-diagnostico-reparo-service
oficina-diagnostico-reparo-service

# Serviço de Diagnóstico e Reparo

Fila do técnico, diagnóstico e reparo do veículo com DynamoDB (Tech Challenge Fase 4 - SOAT).

Participa da Saga orquestrada pelo OS Service (contrato em [`oficina-os-service/docs/saga/contrato-mensagens.md`](https://github.com/tiagomiele/oficina-os-service/blob/homolog/docs/saga/contrato-mensagens.md)).

## Saga

| Recebe (fila `diagnostico-reparo-comandos`) | Efeito |
|---|---|
| `INICIAR_DIAGNOSTICO` | cria a execução em `NA_FILA_DIAGNOSTICO`; com `refazer=true` volta para a fila de diagnóstico |
| `INICIAR_REPARO` | `DIAGNOSTICO_CONCLUIDO` → `NA_FILA_REPARO` |
| `CANCELAR_EXECUCAO` | compensação: execução `CANCELADA` |

| Ação do técnico | Publica (fila `os-eventos`) |
|---|---|
| `POST /execucoes/{numeroOs}/iniciar-diagnostico` | `DIAGNOSTICO_INICIADO` (não repete no retrabalho) |
| `POST /execucoes/{numeroOs}/concluir-diagnostico` `{"diagnostico": "..."}` | `DIAGNOSTICO_CONCLUIDO` |
| `POST /execucoes/{numeroOs}/iniciar-reparo` | — |
| `POST /execucoes/{numeroOs}/concluir-reparo` | `REPARO_CONCLUIDO` |

Também: `GET /execucoes/fila?etapa=NA_FILA_DIAGNOSTICO` (fila do técnico, ordem de chegada) e `GET /execucoes/{numeroOs}`. Exigem o token de funcionário ou técnico emitido pelo OS Service (`JWT_SECRET` compartilhado).

- **DynamoDB:** tabela `oficina-<env>-execucoes` (chave `numeroOs`, índice `etapa-entradaFilaEm-index`). A escrita é condicional pela `versao` (controle otimista).
- **Idempotência:** o item guarda os últimos 20 `messageId` processados.
- **Erros:** mensagem com falha não é apagada e vai para a DLQ após 5 tentativas.
- **`correlationId`:** vem do comando, fica gravado na execução e vai em todos os eventos e no MDC dos logs.

## Configuração

| Variável | Padrão | Uso |
|---|---|---|
| `MENSAGERIA_TIPO` | `memoria` | `sqs` na AWS |
| `SQS_DIAGNOSTICO_REPARO_COMANDOS_URL` / `SQS_OS_EVENTOS_URL` | — | filas (gravadas pelo `configure-environment.ps1`) |
| `PERSISTENCIA_TIPO` | `memoria` | `dynamodb` na AWS |
| `DYNAMODB_TABLE_NAME` | `oficina-local-execucoes` | tabela |
| `AWS_SQS_ENDPOINT` / `AWS_DYNAMODB_ENDPOINT` | — | só LocalStack |
| `JWT_SECRET` | — | mesmo valor do OS Service |

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

`verify` roda testes (incluindo DynamoDB e SQS no LocalStack via Testcontainers, precisa de Docker), ArchUnit e o gate do JaCoCo (80% de linhas e branches). O relatório fica em `target/site/jacoco/index.html`.

## Endpoints

- `GET /actuator/health`
- `GET /swagger-ui.html`

## CI

`.github/workflows/ci.yml`: validação dos workflows, build com testes e JaCoCo, SonarCloud (quality gate, quando o secret `SONAR_TOKEN` existir), SBOM CycloneDX e Trivy + Gitleaks.
