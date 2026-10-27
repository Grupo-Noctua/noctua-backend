# Plano de testes — Noctua Dashboard v3

## 1. Objetivo

Verificar os contratos HTTP, a fidelidade das contagens, o agrupamento por projeto, a preservação das recomendações, os valores monetários e o comportamento diante de falhas nas fontes.

## 2. Execução realizada

- Comando: `mvn -B verify`, executado em Docker com `maven:3.9.11-eclipse-temurin-21`.
- Resultado: **BUILD SUCCESS — 24 testes, 0 falhas, 0 erros, 0 ignorados**.
- JAR gerado: `target/noctua-backend-0.0.1-SNAPSHOT.jar`.
- Evidências locais: `target/surefire-reports/TEST-*.xml` e relatórios `.txt`.
- Fontes utilizadas: H2 em memória (modo MySQL), fixtures determinísticas e mocks do AWS SDK. Nenhum acesso ao NOCTUA ou Athena real foi realizado.

| Classe | Métodos de teste | Escopo |
|---|---:|---|
| `DashboardIntegrationTest` | 9 | Spring Boot + MockMvc + Service + JDBC/H2 |
| `AthenaEndpointsTest` | 2 | Contratos HTTP dos quatro endpoints de funcionários |
| `DashboardAthenaRepositoryTest` | 4 | Leitura de SQL configurado e mapeamento/validação de resultados |
| `AthenaQueryExecutorTest` | 9 | Ciclo de consulta, falhas, paginação, limite e timeout |

Alguns métodos exercitam múltiplos cenários e rotas, por isso a quantidade de casos abaixo supera a de métodos.

### Verificação do empacotamento — 14/10/2026

- Build: `docker build --no-cache-filter build --progress=plain -t noctua-backend:local-20261014 .`.
- Nova execução de `mvn -B verify` dentro do estágio `build`: **24 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS**. Os relatórios desta execução ficam no estágio de build, não atualizam o `target/` do host.
- Verificação: `python scripts/verify_image.py noctua-backend:local-20261014` — **PASS**. JAR presente, usuário não root, quatro SQLs idênticos aos originais e legíveis por `noctua` sem mounts; nenhum arquivo de ambiente no filesystem final ou nas entradas do JAR.
- Identificação da imagem e pendências de publicação/homologação: [fase 2 do plano de automação](PLANO_AUTOMACAO.md#fase-2--imagem-versionada).
- Estes resultados são locais, com H2/mocks; os casos de homologação AWS da seção 5 continuam pendentes.

## 3. Dados de teste

Fixture relacional recriada antes de cada teste de integração:

- Quatro processamentos, dos quais um não possui documento.
- Três documentos, com orçamento `350000.00`, nulo e `9999999999999.99` (limite positivo de `DECIMAL(15,2)`).
- Três vagas: duas no primeiro projeto e uma no segundo.
- SQL repetido na mesma vaga e presente em outra vaga do mesmo projeto.
- Java presente em vagas de projetos diferentes, empatando com SQL em número de vagas.
- Primeiro projeto com duas execuções de matching e três recomendações.
- `PROF-001` recomendado em duas vagas/execuções distintas, com ranking próprio.
- Projeto sem matching, projeto com vaga sem candidato e processamento sem documento.
- Linhas inseridas fora da ordem do ranking para verificar a ordenação final.

Fixtures em `src/test/resources/schema.sql` e `data.sql` derivam apenas dos campos documentados. Não substituem o DDL oficial.

## 4. Matriz de testes automatizados

| ID | Cenário / entrada | Resultado esperado | Cobertura |
|---|---|---|---|
| J01 | Total com quatro processamentos, inclusive incompletos | 200, `total: 4` | Integração |
| J02 | Competência duplicada na mesma vaga | Duplicidade não aumenta o número de vagas | Integração |
| J03 | Java e SQL empatados em duas vagas | Lista com ambos e quantidade 2 | Integração |
| J04 | SQL em duas vagas do mesmo processamento | Quantidade de projetos para SQL = 1 | Integração |
| J05 | Java em dois processamentos | Quantidade de projetos para Java = 2 | Integração |
| P01 | Consulta achatada com três recomendações | Um objeto do projeto com três profissionais | Integração |
| P02 | Mesmo profissional em vagas/execuções distintas | Duas ocorrências de `PROF-001` mantidas | Integração |
| P03 | Candidatos inseridos fora de ordem | Ranking 1, 2 na primeira vaga; ranking 1 na segunda | Integração |
| P04 | Projeto sem matching/candidato | Projeto presente, lista de profissionais vazia | Integração |
| P05 | Processamento sem documento | Processamento presente; ID de documento e dados nulos | Integração |
| P06 | Orçamento informado, nulo e valor máximo | Preservação dos valores via `BigDecimal` e JSON nullable | Integração |
| P07 | Score com centavos e data prevista | Decimal preservado e data ISO `2026-10-15` | Integração |
| P08 | Mais de um documento por processamento | 500; inconsistência registrada sem escolha arbitrária | Integração |
| E01 | Banco vazio | Total zero e listas `[]`, com 200 | Integração |
| E02 | Tabela de competências indisponível | 500 ProblemDetail, mensagem genérica e `errorId` | Integração |
| E03 | Athena desabilitado | Os quatro endpoints retornam erro, sem indicadores fictícios | Integração |
| H01 | Respostas dos quatro indicadores Athena | JSON com nomes dos DTOs e 200 | MVC |
| H02 | Total > `Int.MAX_VALUE` | Valor `3000000000` preservado | MVC + repositório Athena |
| H03 | Distribuições vazias e total zero | `[]` e `{"total":0}` | MVC |
| H04 | Rota inexistente / POST em rota GET | 404 / 405 | Integração |
| A01 | SQL fornecido por recurso configurado | Arquivo lido em UTF-8 e enviado ao executor | Repositório Athena |
| A02 | Total nulo, negativo, texto, fracionário ou overflow | Exceção, sem coerção para zero | Repositório Athena |
| A03 | Total sem linha ou com múltiplas linhas | Exceção por formato inválido | Repositório Athena |
| A04 | Dimensão nula e lista vazia | Nulo preservado / lista vazia aceita | Repositório Athena |
| A05 | Alias incorreto | Exceção de mapeamento | Repositório Athena |
| A06 | QUEUED → RUNNING → SUCCEEDED | Polling seguido de leitura dos resultados | Executor Athena |
| A07 | Duas páginas, inclusive célula nula | Cabeçalho removido só na primeira página; todos os dados preservados | Executor Athena |
| A08 | FAILED ou CANCELLED | Exceção, sem buscar resultados | Executor Athena |
| A09 | Timeout durante execução | Tentativa de cancelamento; falha de cancelamento não mascara timeout | Executor Athena |
| A10 | Thread interrompida | Flag de interrupção preservada e cancelamento tentado | Executor Athena |
| A11 | Exceção do SDK durante polling | Exceção padronizada internamente e cancelamento tentado | Executor Athena |
| A12 | Mais linhas que limite configurado | Erro, sem retornar lista parcial | Executor Athena |
| A13 | Página apenas com cabeçalho | Lista vazia | Executor Athena |
| A14 | Timeout durante leitura de páginas | Interrupção da leitura dentro do orçamento compartilhado | Executor Athena |
| A15 | Token de paginação repetido | Erro, evitando repetição infinita | Executor Athena |

## 5. Casos de homologação pendentes

| ID | Pré-condição e procedimento | Critério de aceite | Responsável sugerido |
|---|---|---|---|
| R01 | Obter DDL/versão real e executar quatro queries JDBC em amostra controlada | Nomes, tipos, CTE e junções compatíveis; resultados iguais aos valores de referência | Backend + DBA |
| R02 | Verificar coluna; preparar migração onde ausente (o DDL atual já a inclui em bases novas) | Coluna nullable `DECIMAL(15,2)`; dados anteriores preservados | DBA |
| R03 | Conferir cardinalidade de documento/processamento | Um documento por processamento ou política de versão definida e implementada | Backend + produto |
| R04 | Configurar os quatro SQLs Athena com base em amostra conhecida | Totais/dimensões conferidos, sem duplicar funcionários indevidamente | Engenharia de dados |
| R05 | Executar Athena com IAM real e origem/resultados S3 | Consulta completa; leitura do catálogo e resultados autorizada | Infra + dados |
| R06 | Usar amostra Athena com mais de uma página e abaixo de `ATHENA_MAX_ROWS` | Nenhuma linha perdida, duplicada ou interpretada como cabeçalho | Backend |
| R07 | Configuração ativa com arquivo ausente, timeout inválido ou database vazio | Startup rejeitado com causa identificável no log | Backend |
| R08 | Retirar permissão Athena/S3 em ambiente isolado | HTTP 500 genérico; log permite diagnóstico | Infra + backend |
| R09 | Executar consulta que ultrapasse o timeout | Resposta de erro e tentativa de cancelamento observada no Athena | Backend |
| R10 | Consumir respostas no frontend | Cards/gráficos/lista aceitam zero, vazio, nulo, empates e recomendações repetidas | Frontend + produto |
| R11 | Publicar JAR/imagem com variáveis do ambiente | Startup, health JDBC e oito rotas verificadas | Infra + backend |

O status 400 é reservado a requisições malformadas/validação do Spring MVC. Os endpoints atuais não recebem parâmetros obrigatórios nem corpo, portanto não há regra de negócio de parâmetro inválido a testar nesta versão. Não há cenário 204: ausência de dados tem contrato explícito 200.

## 6. Plano de volume e desempenho

Executar após dispor de dados representativos e de metas acordadas:

1. Medir cada endpoint separadamente com 1, 10 e 50 clientes concorrentes por 5 minutos, após aquecimento.
2. Testar listagens com 100, 1.000 e 10.000 projetos, variando a quantidade de recomendações por projeto.
3. Registrar p50/p95/p99, taxa de erros, bytes por resposta, memória, CPU, utilização do pool e duração das queries.
4. Para Athena, registrar consultas iniciadas, bytes varridos, duração e custo estimado; confirmar limites do workgroup.
5. Confirmar ausência de truncamento ou perda de recomendações. Interromper a carga se o ambiente atingir os limites acordados.
6. Definir metas de latência e limites de volume com a equipe; decidir paginação, cache ou consultas assíncronas a partir das medições.

Esses ensaios não foram executados; a especificação não define SLA nem volumetria. O fluxo Athena atual é síncrono e ocupa a requisição HTTP durante o polling.

## 7. Critérios de entrada e saída

**Entrada para regressão automatizada:** Java 21/Maven ou Docker, acesso ao Maven Central para o primeiro build.

**Entrada para homologação:** DDL real, conexão de leitura, amostra com resultados esperados, esquema S3/Glue, quatro queries aprovadas e acesso ao ambiente AWS.

**Saída da regressão:** `mvn -B verify` aprovado e relatórios sem falhas. Atingido nesta entrega.

**Saída da homologação:** R01–R11 aprovados, decisões de contrato aceitas pelo frontend e limitações de volume registradas. Ainda pendente das fontes reais.

## 8. Como repetir os testes

```sh
mvn -B verify
mvn -B -Dtest=DashboardIntegrationTest test
mvn -B -Dtest=AthenaQueryExecutorTest test
```

No ambiente sem Java 21/Maven, usar o comando Docker do [guia de execução](EXECUCAO_LOCAL.md). Falhas provocadas pelos testes geram logs de erro intencionalmente; o resultado válido é o resumo do Surefire e o código de saída do Maven.
