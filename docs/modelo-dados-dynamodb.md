# Modelo de dados no DynamoDB: Diagnóstico e Reparo

O serviço usa uma única tabela DynamoDB, que guarda o andamento do trabalho do técnico, com um item por OS. A tabela é isolada, e nenhum outro serviço a acessa. Os dados que chegam do OS Service vêm no `payload` dos comandos SQS da Saga.

- **Terraform:** [`oficina-database-infra/diagnostico-reparo/main.tf`](https://github.com/tiagomiele/oficina-database-infra/blob/homolog/diagnostico-reparo/main.tf)
- **Código de acesso:** [`DynamoDbExecucaoRepository`](../src/main/java/br/com/oficina/diagnosticoreparo/adapter/persistence/DynamoDbExecucaoRepository.java)
- **Decisão de usar DynamoDB:** [ADR 0001](adr/0001-uso-dynamodb.md)

## 1. Tabela `oficina-<env>-execucoes`

| Propriedade | Valor |
|---|---|
| Nome | `oficina-homolog-execucoes` ou `oficina-production-execucoes` (`oficina-local-execucoes` no ambiente local) |
| Chave primária | `numeroOs` (String), só partition key, sem sort key |
| Modo de cobrança | `PAY_PER_REQUEST` (sob demanda, sem capacidade provisionada) |
| Criptografia | ativada, com a chave gerenciada pela AWS (a LabRole do AWS Academy não administra uma chave KMS própria) |
| Backup contínuo (PITR) | ativado (`point_in_time_recovery = true`) |
| Proteção contra exclusão | desligada (`deletion_protection = false`), para permitir o destroy do ambiente acadêmico |
| Região | `us-west-2` |

A configuração da aplicação está em [`application.yml`](../src/main/resources/application.yml):

| Variável | Uso | Padrão |
|---|---|---|
| `PERSISTENCIA_TIPO` | `dynamodb` no EKS, `memoria` local e nos testes | `memoria` |
| `DYNAMODB_TABLE_NAME` | nome da tabela | `oficina-local-execucoes` |
| `DYNAMODB_FILA_INDEX` | nome do índice da fila | `etapa-entradaFilaEm-index` |
| `AWS_DYNAMODB_ENDPOINT` | endpoint alternativo (LocalStack nos testes) | vazio |

## 2. Atributos do item

O DynamoDB não tem esquema fixo: a tabela declara só os atributos usados em chaves (`numeroOs`, `etapa` e `entradaFilaEm`). Os demais são gravados pela aplicação.

| Atributo | Tipo DynamoDB | Obrigatório | Conteúdo |
|---|---|---|---|
| `numeroOs` | S | sim | número da OS (ex.: `OS-102026-000003`). É a chave primária |
| `etapa` | S | sim | etapa atual do trabalho (ver seção 4). É a partition key do índice da fila |
| `entradaFilaEm` | S (ISO-8601) | não | quando a OS entrou na fila de diagnóstico ou de reparo. É a sort key do índice da fila |
| `atualizadoEm` | S (ISO-8601) | não | última alteração do item |
| `idCliente` | N | não | id do cliente, recebido no `INICIAR_DIAGNOSTICO` |
| `placa` | S | não | placa do veículo, recebida no `INICIAR_DIAGNOSTICO` |
| `descricaoProblema` | S | não | relato do cliente, recebido no `INICIAR_DIAGNOSTICO` |
| `diagnostico` | S | não | laudo do técnico |
| `retrabalho` | BOOL | sim | `true` quando o cliente rejeitou o orçamento e pediu um novo diagnóstico |
| `correlationId` | S | não | id da Saga, para rastrear a OS nos logs e no New Relic |
| `versao` | N | sim | versão do item, para o controle otimista (seção 5) |
| `mensagensProcessadas` | L (lista de S) | sim | últimos 20 `messageId` de comandos já aplicados (idempotência, seção 5) |

Os atributos opcionais não são gravados quando estão vazios.

Exemplo de item:

```json
{
  "numeroOs": "OS-102026-000003",
  "etapa": "NA_FILA_REPARO",
  "entradaFilaEm": "2026-10-20T14:32:10.512Z",
  "atualizadoEm": "2026-10-20T14:32:10.512Z",
  "idCliente": 1,
  "placa": "ABC1D23",
  "descricaoProblema": "Barulho na suspensão dianteira",
  "diagnostico": "Trocar bieleta dianteira esquerda",
  "retrabalho": false,
  "correlationId": "hml-OS-102026-000003",
  "versao": 5,
  "mensagensProcessadas": ["5f0c...", "9a1e..."]
}
```

## 3. Índices

| Índice | Tipo | Partition key | Sort key | Projeção | Uso |
|---|---|---|---|---|---|
| tabela (primário) | chave primária | `numeroOs` (S) | — | — | buscar e gravar a execução de uma OS (`GetItem` com leitura consistente e `PutItem`) |
| `etapa-entradaFilaEm-index` | GSI | `etapa` (S) | `entradaFilaEm` (S) | `ALL` | montar a fila do técnico: `GET /execucoes/fila?etapa=NA_FILA_DIAGNOSTICO` faz um `Query` por etapa, em ordem de chegada (`ScanIndexForward = true`) |

Como `entradaFilaEm` está em ISO-8601, a ordem alfabética do texto é a mesma ordem cronológica. A projeção `ALL` devolve o item inteiro na consulta da fila, sem uma segunda leitura na tabela.

## 4. Valores de `etapa`

Definidos em [`EtapaExecucao`](../src/main/java/br/com/oficina/diagnosticoreparo/domain/model/EtapaExecucao.java):

| Etapa | Próximas etapas permitidas |
|---|---|
| `NA_FILA_DIAGNOSTICO` | `EM_DIAGNOSTICO`, `CANCELADA` |
| `EM_DIAGNOSTICO` | `DIAGNOSTICO_CONCLUIDO`, `CANCELADA` |
| `DIAGNOSTICO_CONCLUIDO` | `NA_FILA_REPARO`, `NA_FILA_DIAGNOSTICO` (retrabalho), `CANCELADA` |
| `NA_FILA_REPARO` | `EM_REPARO`, `CANCELADA` |
| `EM_REPARO` | `REPARO_CONCLUIDO`, `CANCELADA` |
| `REPARO_CONCLUIDO` | final |
| `CANCELADA` | final |

`entradaFilaEm` é atualizado sempre que a OS entra em `NA_FILA_DIAGNOSTICO` ou `NA_FILA_REPARO`.

## 5. Consistência e idempotência

- **Controle otimista:** a primeira gravação usa `attribute_not_exists(numeroOs)`, e as seguintes usam `versao = :versaoLida`. Se outra requisição alterou o item antes, o DynamoDB recusa a escrita e a mensagem volta para a fila SQS para nova tentativa.
- **Idempotência:** antes de aplicar um comando, o serviço confere se o `messageId` já está em `mensagensProcessadas`. Se estiver, o comando é ignorado. A lista guarda só os últimos 20 ids, para o item não crescer sem limite.
- **Leitura consistente:** a busca por `numeroOs` usa `ConsistentRead = true`. A consulta da fila pelo GSI é eventualmente consistente, o que é suficiente para a listagem do técnico.

## 6. Padrões de acesso

| Operação do serviço | Chamada DynamoDB |
|---|---|
| Receber `INICIAR_DIAGNOSTICO`, `INICIAR_REPARO` ou `CANCELAR_EXECUCAO` | `GetItem` por `numeroOs` e `PutItem` condicional |
| Técnico inicia ou conclui diagnóstico ou reparo | `GetItem` por `numeroOs` e `PutItem` condicional |
| Consultar uma execução (`GET /execucoes/{numeroOs}`) | `GetItem` por `numeroOs` |
| Listar a fila (`GET /execucoes/fila?etapa=...`) | `Query` no `etapa-entradaFilaEm-index` |

Não há `Scan` no código da aplicação. Como ver os dados pela CLI ou pelo console da AWS: [README, seção 7](../README.md#7-banco-de-dados-dynamodb).
