# ADR 0001 — DynamoDB como banco do Diagnóstico e Reparo

- **Status:** aceita
- **Contexto:** Fase 4, divisão do backend em 3 microsserviços com banco próprio

## Contexto

O enunciado da Fase 4 exige que cada microsserviço tenha o próprio banco e que a solução use pelo menos um banco SQL e um NoSQL. O OS Service e o Orçamento e Pagamento já usam PostgreSQL (RDS), porque têm dados relacionais e transações (estoque, valores e pagamentos).

O Diagnóstico e Reparo guarda o andamento do trabalho do técnico. Esse dado tem estas características:

- é um registro por OS, lido e gravado sempre inteiro, pela chave `numeroOs`;
- não tem relacionamento com outras tabelas: os dados do cliente e do veículo chegam prontos no comando `INICIAR_DIAGNOSTICO` da Saga;
- tem só 2 consultas: buscar uma OS e listar a fila do técnico por etapa, em ordem de chegada;
- recebe muitas atualizações pequenas (mudança de etapa) disparadas por mensagens SQS, que podem chegar repetidas.

## Decisão

Usar o **Amazon DynamoDB** como banco do Diagnóstico e Reparo, com uma tabela `oficina-<env>-execucoes`:

- chave primária `numeroOs`;
- índice global `etapa-entradaFilaEm-index` para a fila do técnico;
- cobrança `PAY_PER_REQUEST`;
- gravação condicional por `versao` (controle otimista);
- lista `mensagensProcessadas` no próprio item (idempotência).

O modelo completo está em [modelo-dados-dynamodb.md](../modelo-dados-dynamodb.md).

## Motivos

1. **Atende o NoSQL obrigatório** no serviço em que o dado já tem formato de documento, sem forçar um modelo relacional.
2. **Os acessos são todos por chave.** `GetItem` por `numeroOs` e `Query` por `etapa` cobrem o serviço inteiro, sem joins.
3. **A fila sai pronta do índice.** O GSI `etapa` + `entradaFilaEm` devolve a fila já ordenada, sem tabela auxiliar.
4. **Consistência e idempotência num item só.** A escrita condicional por `versao` e os `messageId` guardados no item resolvem mensagens repetidas ou concorrentes, sem transação entre tabelas.
5. **Serviço gerenciado e sem servidor.** Não há instância, VPC, sub-rede, túnel ou senha para manter, ao contrário do RDS. O acesso usa as credenciais IAM do ambiente.
6. **Custo e AWS Academy.** O modo sob demanda só cobra pelo uso, cabe no orçamento do Learner Lab e a tabela sobe e é destruída em segundos com Terraform.
7. **Isolamento.** A tabela é usada só por este serviço. Cada ambiente (`homolog`, `production`) tem a sua tabela e o seu workspace Terraform.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| PostgreSQL (RDS), como os outros 2 serviços | não atenderia o NoSQL obrigatório; mais uma instância, sub-rede e senha para manter, para dados sem relacionamento |
| MongoDB (Amazon DocumentDB ou MongoDB Atlas) | seria mais um cluster para manter, com rede, senha e custo por hora; o Atlas ainda traria um provedor externo à AWS |
| Banco em memória ou Redis | não garante durabilidade do andamento do trabalho |

## Consequências

- **Positivas:** banco isolado, sem servidor, barato e rápido de provisionar; idempotência e concorrência resolvidas no próprio item; testes de integração com DynamoDB no LocalStack ([`LocalStackIntegrationTest`](../../src/test/java/br/com/oficina/diagnosticoreparo/integration/LocalStackIntegrationTest.java)).
- **Negativas:** novas consultas exigem planejar um índice antes; não há SQL completo (o PartiQL do console serve só para inspeção); a consulta pelo GSI é eventualmente consistente.
- **Mitigação:** a porta `ExecucaoRepository` isola o DynamoDB no adaptador [`DynamoDbExecucaoRepository`](../../src/main/java/br/com/oficina/diagnosticoreparo/adapter/persistence/DynamoDbExecucaoRepository.java). Trocar o banco não muda o domínio nem os casos de uso.
