# Especificação técnica — API do dashboard Noctua

*Kotlin + Spring Boot | JDBC + Amazon Athena*

# 1. Objetivo

Definir a especificação inicial da API REST
responsável por fornecer ao frontend os dados necessários para o dashboard do
projeto Noctua. A implementação será realizada em Kotlin com Spring Boot,
utilizando JDBC para consultas ao banco relacional e Amazon Athena para
consultas sobre a base de dados disponibilizada no Amazon S3.

# 2. Escopo

Esta versão contempla os endpoints
necessários para os oito indicadores e informações levantados para o dashboard.
As queries SQL não são apresentadas neste documento. Elas serão definidas
posteriormente com base no modelo real das fontes de dados.

# 3. Fontes de dados

|     |
| --- |

Fonte

|     |
| --- |

Acesso

|     |
| --- |

Responsabilidade

|     |
| --- |

Banco de dados relacional NOCTUA

|     |
| --- |

JDBC

|     |
| --- |

Consultar as informações persistidas nas tabelas relacionais.

|     |
| --- |

Base de dados no Amazon S3

|     |
| --- |

Amazon Athena

|     |
| --- |

Consultar os dados armazenados no S3 por meio das tabelas
&#x20; disponibilizadas no Athena.

# 4. Modelo de dados do banco relacional

O script fornecido define o banco NOCTUA e
as seguintes entidades. As relações entre elas deverão ser consideradas na
construção dos repositórios JDBC.

|     |
| --- |

Tabela

|     |
| --- |

Finalidade

|     |
| --- |

Relacionamentos principais

|     |
| --- |

Processamento

|     |
| --- |

Registra cada processamento realizado e seus parâmetros.

|     |
| --- |

Referenciada por Processamento_Log_JSON, Documento_Projeto,
&#x20; Vaga_Processada e Matching_Execucao.

|     |
| --- |

Processamento_Log_JSON

|     |
| --- |

Armazena logs e conteúdos JSON das etapas do processamento.

|     |
| --- |

fk_processamento → Processamento.

|     |
| --- |

Documento_Projeto

|     |
| --- |

Armazena informações extraídas do documento do projeto.

|     |
| --- |

fk_processamento → Processamento.

|     |
| --- |

Vaga_Processada

|     |
| --- |

Registra as vagas processadas em cada processamento.

|     |
| --- |

fk_processamento → Processamento.

|     |
| --- |

Vaga_Competencia

|     |
| --- |

Armazena competências associadas às vagas.

|     |
| --- |

fk_vaga_processada → Vaga_Processada.

|     |
| --- |

Matching_Execucao

|     |
| --- |

Registra uma execução de matching e a quantidade de vagas
&#x20; solicitadas/processadas.

|     |
| --- |

fk_processamento → Processamento.

|     |
| --- |

Matching_Execucao_Vaga

|     |
| --- |

Registra o resultado/status do matching por vaga.

|     |
| --- |

Relaciona Matching_Execucao e Vaga_Processada.

|     |
| --- |

Candidato_Ranqueado

|     |
| --- |

Armazena candidatos classificados para uma vaga.

|     |
| --- |

fk_matching_execucao_vaga → Matching_Execucao_Vaga.

|     |
| --- |

Candidato_Score_Detalhado

|     |
| --- |

Armazena a composição do score do candidato.

|     |
| --- |

fk_candidato_ranqueado → Candidato_Ranqueado.

|     |
| --- |

Candidato_Competencia_Match

|     |
| --- |

Registra competências do candidato compatíveis com a vaga.

|     |
| --- |

fk_candidato_ranqueado → Candidato_Ranqueado.

|     |
| --- |

Candidato_Lacuna

|     |
| --- |

Registra competências que representam lacunas do candidato.

|     |
| --- |

fk_candidato_ranqueado → Candidato_Ranqueado.

# 5. Arquitetura do backend

·      
Frontend → API REST

·      
API REST → DashboardController

·      
DashboardController →
DashboardService

·      
DashboardService → Repository

·      
DashboardJdbcRepository → Banco
de dados relacional

·      
DashboardAthenaRepository →
Amazon Athena → Amazon S3

# 6. Responsabilidade das camadas

·      
Controller: expor os endpoints
HTTP e encaminhar as requisições para o Service.

·      
Service: orquestrar a obtenção
dos dados e aplicar regras de negócio quando necessário.

·      
Repository JDBC: executar
consultas exclusivamente no banco relacional.

·      
Repository Athena: executar
consultas exclusivamente no Athena.

·      
DTO: definir o contrato de
dados devolvido pela API ao frontend.

# 7. Endpoints do dashboard

|     |
| --- |

\#

|     |
| --- |

Método

|     |
| --- |

Endpoint

|     |
| --- |

Fonte

|     |
| --- |

Acesso

|     |
| --- |

1

|     |
| --- |

GET

|     |
| --- |

/api/dashboard/funcionarios/total

|     |
| --- |

Base de dados S3

|     |
| --- |

Athena

|     |
| --- |

2

|     |
| --- |

GET

|     |
| --- |

/api/dashboard/projetos/total

|     |
| --- |

Banco de dados

|     |
| --- |

JDBC

|     |
| --- |

3

|     |
| --- |

GET

|     |
| --- |

/api/dashboard/ferramentas/mais-utilizada

|     |
| --- |

Banco de dados

|     |
| --- |

JDBC

|     |
| --- |

4

|     |
| --- |

GET

|     |
| --- |

/api/dashboard/funcionarios/tecnologia

|     |
| --- |

Base de dados S3

|     |
| --- |

Athena

|     |
| --- |

5

|     |
| --- |

GET

|     |
| --- |

/api/dashboard/funcionarios/senioridade

|     |
| --- |

Base de dados S3

|     |
| --- |

Athena

|     |
| --- |

6

|     |
| --- |

GET

|     |
| --- |

/api/dashboard/funcionarios/area

|     |
| --- |

Base de dados S3

|     |
| --- |

Athena

|     |
| --- |

7

|     |
| --- |

GET

|     |
| --- |

/api/dashboard/projetos/tecnologia

|     |
| --- |

Banco de dados

|     |
| --- |

JDBC

|     |
| --- |

8

|     |
| --- |

GET

|     |
| --- |

/api/dashboard/projetos

|     |
| --- |

Banco de dados

|     |
| --- |

JDBC

# 8. Especificação funcional dos endpoints

## 8.1 — Número de funcionários

|     |
| --- |

Método

|     |
| --- |

GET

|     |
| --- |

Endpoint

|     |
| --- |

/api/dashboard/funcionarios/total

|     |
| --- |

Fonte

|     |
| --- |

Base de dados no S3

|     |
| --- |

Tecnologia

|     |
| --- |

Athena

|     |
| --- |

Objetivo

|     |
| --- |

Retornar a quantidade total de funcionários existente na base de
&#x20; dados.

|     |
| --- |

Parâmetros

|     |
| --- |

Nenhum parâmetro obrigatório definido nesta versão.

|     |
| --- |

Resposta esperada

|     |
| --- |

Resposta simples contendo o total.

|     |
| --- |

Query

|     |
| --- |

A definir. Não faz parte desta versão.

## 8.2 — Número de projetos analisados

|     |
| --- |

Método

|     |
| --- |

GET

|     |
| --- |

Endpoint

|     |
| --- |

/api/dashboard/projetos/total

|     |
| --- |

Fonte

|     |
| --- |

Banco de dados NOCTUA

|     |
| --- |

Tecnologia

|     |
| --- |

JDBC

|     |
| --- |

Objetivo

|     |
| --- |

Retornar a quantidade de projetos/processamentos de projetos
&#x20; registrada no banco, conforme a regra de negócio que será validada.

|     |
| --- |

Parâmetros

|     |
| --- |

Nenhum parâmetro obrigatório definido nesta versão.

|     |
| --- |

Resposta esperada

|     |
| --- |

Resposta simples contendo o total.

|     |
| --- |

Query

|     |
| --- |

A definir. Não faz parte desta versão.

## 8.3 — Ferramenta mais utilizada

|     |
| --- |

Método

|     |
| --- |

GET

|     |
| --- |

Endpoint

|     |
| --- |

/api/dashboard/ferramentas/mais-utilizada

|     |
| --- |

Fonte

|     |
| --- |

Banco de dados NOCTUA

|     |
| --- |

Tecnologia

|     |
| --- |

JDBC

|     |
| --- |

Objetivo

|     |
| --- |

Retornar a ferramenta ou tecnologia com maior ocorrência na fonte
&#x20; de dados utilizada pelo indicador.

|     |
| --- |

Parâmetros

|     |
| --- |

Nenhum parâmetro obrigatório definido nesta versão.

|     |
| --- |

Resposta esperada

|     |
| --- |

Objeto contendo a ferramenta e sua quantidade.

|     |
| --- |

Query

|     |
| --- |

A definir. Não faz parte desta versão.

## 8.4 — Funcionários × Tecnologia

|     |
| --- |

Método

|     |
| --- |

GET

|     |
| --- |

Endpoint

|     |
| --- |

/api/dashboard/funcionarios/tecnologia

|     |
| --- |

Fonte

|     |
| --- |

Base de dados no S3

|     |
| --- |

Tecnologia

|     |
| --- |

Athena

|     |
| --- |

Objetivo

|     |
| --- |

Retornar a distribuição de funcionários por tecnologia para
&#x20; composição de gráfico no dashboard.

|     |
| --- |

Parâmetros

|     |
| --- |

Nenhum parâmetro obrigatório definido nesta versão.

|     |
| --- |

Resposta esperada

|     |
| --- |

Lista contendo tecnologia e quantidade.

|     |
| --- |

Query

|     |
| --- |

A definir. Não faz parte desta versão.

## 8.5 — Funcionários por senioridade

|     |
| --- |

Método

|     |
| --- |

GET

|     |
| --- |

Endpoint

|     |
| --- |

/api/dashboard/funcionarios/senioridade

|     |
| --- |

Fonte

|     |
| --- |

Base de dados no S3

|     |
| --- |

Tecnologia

|     |
| --- |

Athena

|     |
| --- |

Objetivo

|     |
| --- |

Retornar a quantidade de funcionários agrupada por senioridade.

|     |
| --- |

Parâmetros

|     |
| --- |

Nenhum parâmetro obrigatório definido nesta versão.

|     |
| --- |

Resposta esperada

|     |
| --- |

Lista contendo senioridade e quantidade.

|     |
| --- |

Query

|     |
| --- |

A definir. Não faz parte desta versão.

## 8.6 — Funcionários por área

|     |
| --- |

Método

|     |
| --- |

GET

|     |
| --- |

Endpoint

|     |
| --- |

/api/dashboard/funcionarios/area

|     |
| --- |

Fonte

|     |
| --- |

Base de dados no S3

|     |
| --- |

Tecnologia

|     |
| --- |

Athena

|     |
| --- |

Objetivo

|     |
| --- |

Retornar a quantidade de funcionários agrupada por área.

|     |
| --- |

Parâmetros

|     |
| --- |

Nenhum parâmetro obrigatório definido nesta versão.

|     |
| --- |

Resposta esperada

|     |
| --- |

Lista contendo área e quantidade.

|     |
| --- |

Query

|     |
| --- |

A definir. Não faz parte desta versão.

## 8.7 — Projetos × Tecnologia

|     |
| --- |

Método

|     |
| --- |

GET

|     |
| --- |

Endpoint

|     |
| --- |

/api/dashboard/projetos/tecnologia

|     |
| --- |

Fonte

|     |
| --- |

Banco de dados NOCTUA

|     |
| --- |

Tecnologia

|     |
| --- |

JDBC

|     |
| --- |

Objetivo

|     |
| --- |

Retornar a distribuição dos projetos por tecnologia, conforme os
&#x20; relacionamentos e dados disponíveis no banco.

|     |
| --- |

Parâmetros

|     |
| --- |

Nenhum parâmetro obrigatório definido nesta versão.

|     |
| --- |

Resposta esperada

|     |
| --- |

Lista contendo tecnologia e quantidade de projetos.

|     |
| --- |

Query

|     |
| --- |

A definir. Não faz parte desta versão.

## 8.8 — Listagem de projetos

|     |
| --- |

Método

|     |
| --- |

GET

|     |
| --- |

Endpoint

|     |
| --- |

/api/dashboard/projetos

|     |
| --- |

Fonte

|     |
| --- |

Banco de dados NOCTUA

|     |
| --- |

Tecnologia

|     |
| --- |

JDBC

|     |
| --- |

Objetivo

|     |
| --- |

Retornar os projetos e as informações necessárias para a listagem
&#x20; do dashboard.

|     |
| --- |

Parâmetros

|     |
| --- |

Nenhum parâmetro obrigatório definido nesta versão.

|     |
| --- |

Resposta esperada

|     |
| --- |

Lista de projetos. Campos definitivos dependem da regra de
&#x20; negócio.

|     |
| --- |

Query

|     |
| --- |

A definir. Não faz parte desta versão.

# 9. Mapeamento inicial com o banco NOCTUA

Com base no script fornecido, o backend
JDBC possui como principais entidades relacionadas ao dashboard de projetos e
matching:

·      
Processamento: controle dos
processamentos realizados.

·      
Documento_Projeto: dados do
projeto extraídos do documento.

·      
Vaga_Processada e
Vaga_Competencia: informações das vagas e respectivas competências.

·      
Matching_Execucao e
Matching_Execucao_Vaga: execução e resultado do matching.

·      
Candidato_Ranqueado: candidatos
classificados, ranking e score.

·      
Candidato_Score_Detalhado:
composição detalhada do score.

·      
Candidato_Competencia_Match e
Candidato_Lacuna: competências encontradas e lacunas identificadas.

Observação: o banco fornecido não possui,
de forma explícita, uma tabela de funcionários com campos de área e tecnologia.
Portanto, os endpoints de funcionários que foram definidos como Athena devem
utilizar a estrutura existente na base do S3. A correspondência entre os dados
do S3 e os indicadores do dashboard deverá ser validada antes da implementação.

# 10. Campos de projeto

O requisito da listagem de projetos contempla as informações do projeto, incluindo nome, cliente, gestor responsável, data de início prevista, duração estimada, orçamento e profissionais recomendados.

## 10.1 — Orçamento

O campo de orçamento será adicionado à tabela `Documento_Projeto`, pois o orçamento é uma informação pertencente ao projeto.

```sql
ALTER TABLE Documento_Projeto
ADD COLUMN orcamento DECIMAL(15,2) NULL;
```

O tipo `DECIMAL(15,2)` será utilizado para representar valores monetários sem perda de precisão.

## 10.2 — Profissionais recomendados

Os profissionais recomendados **não serão armazenados diretamente em `Documento_Projeto`**. Eles serão obtidos por meio dos relacionamentos existentes no banco de dados.

A relação utilizada pela API será:

```text
Processamento
    ↓
Matching_Execucao
    ↓
Matching_Execucao_Vaga
    ↓
Candidato_Ranqueado
```

Um `Processamento` representa um único projeto. Dessa forma, os candidatos ranqueados associados às execuções de matching daquele processamento serão considerados profissionais recomendados para o respectivo projeto.

Um mesmo profissional poderá aparecer mais de uma vez no resultado quando estiver associado a diferentes vagas ou ocorrências de matching. A API não deverá remover essas duplicidades nesta versão.

# 11. Consulta de projetos e profissionais recomendados

A API deverá ser capaz de retornar todos os projetos juntamente com os profissionais recomendados associados a cada projeto.

## 11.1 — Consulta SQL principal

```sql
SELECT
    p.id_processamento,
    dp.id_documento,
    dp.nome_projeto,
    dp.cliente,
    dp.gestor_responsavel,
    dp.data_inicio_prevista,
    dp.duracao_estimada,
    dp.orcamento,
    dp.descricao,
    dp.objetivo_geral,
    me.id_matching_execucao,
    mev.id_matching_execucao_vaga,
    mev.titulo_vaga,
    mev.senioridade_vaga,
    cr.id_candidato_ranqueado,
    cr.id_profissional_externo,
    cr.nome,
    cr.senioridade,
    cr.posicao_ranking,
    cr.score_total,
    cr.justificativa_curta
FROM Processamento p
INNER JOIN Documento_Projeto dp
    ON dp.fk_processamento = p.id_processamento
INNER JOIN Matching_Execucao me
    ON me.fk_processamento = p.id_processamento
INNER JOIN Matching_Execucao_Vaga mev
    ON mev.fk_matching_execucao = me.id_matching_execucao
INNER JOIN Candidato_Ranqueado cr
    ON cr.fk_matching_execucao_vaga = mev.id_matching_execucao_vaga
ORDER BY
    p.id_processamento,
    mev.id_matching_execucao_vaga,
    cr.posicao_ranking;
```

### 11.2 — Informações retornadas

A consulta retorna, para cada ocorrência de profissional recomendado:

- identificação do processamento/projeto;
- informações básicas do projeto;
- orçamento;
- identificação da execução de matching;
- identificação e informações da vaga;
- identificação do candidato ranqueado;
- identificador externo do profissional;
- nome;
- senioridade;
- posição no ranking;
- score total;
- justificativa curta.

O resultado SQL será tabular. A camada Service será responsável por agrupar os registros por projeto antes de devolvê-los ao frontend.

## 11.3 — Exemplo de resposta da API

O contrato REST deverá apresentar os dados em estrutura hierárquica, agrupando os profissionais recomendados dentro de cada projeto.

```json
[
  {
    "idProcessamento": 1,
    "idProjeto": 101,
    "nomeProjeto": "Implantação de Data Lake",
    "cliente": "Empresa ABC",
    "gestorResponsavel": "João Silva",
    "dataInicioPrevista": "2026-10-15",
    "duracaoEstimada": "180 dias",
    "orcamento": 350000.00,
    "descricao": "Implantação de uma plataforma de dados corporativa.",
    "objetivoGeral": "Modernizar a plataforma de dados.",
    "profissionaisRecomendados": [
      {
        "idProfissional": "PROF-001",
        "nome": "Carlos Oliveira",
        "senioridade": "Senior",
        "vaga": "Engenheiro de Dados",
        "posicaoRanking": 1,
        "scoreTotal": 95.40,
        "justificativa": "Alta compatibilidade com as competências principais da vaga."
      },
      {
        "idProfissional": "PROF-002",
        "nome": "João Santos",
        "senioridade": "Senior",
        "vaga": "Engenheiro de Dados",
        "posicaoRanking": 2,
        "scoreTotal": 91.70,
        "justificativa": "Boa aderência técnica à vaga."
      },
      {
        "idProfissional": "PROF-001",
        "nome": "Carlos Oliveira",
        "senioridade": "Senior",
        "vaga": "Arquiteto de Dados",
        "posicaoRanking": 1,
        "scoreTotal": 93.20,
        "justificativa": "Alta compatibilidade com os requisitos da vaga."
      }
    ]
  }
]
```

A duplicidade do profissional `PROF-001` no exemplo é intencional: o mesmo profissional pode ser recomendado para mais de uma vaga dentro do projeto.

## 11.4 — Consultas complementares do profissional

Caso o dashboard necessite apresentar o detalhamento do profissional recomendado, poderão ser executadas consultas complementares utilizando `id_candidato_ranqueado`.

### Score detalhado

```sql
SELECT
    score_core,
    score_correlatas,
    score_suporte,
    score_complementares
FROM Candidato_Score_Detalhado
WHERE fk_candidato_ranqueado = ?;
```

### Competências compatíveis

```sql
SELECT
    nome_competencia,
    categoria
FROM Candidato_Competencia_Match
WHERE fk_candidato_ranqueado = ?
ORDER BY categoria, nome_competencia;
```

### Lacunas

```sql
SELECT
    nome_competencia
FROM Candidato_Lacuna
WHERE fk_candidato_ranqueado = ?
ORDER BY nome_competencia;
```

Exemplo de estrutura complementar:

```json
{
  "idProfissional": "PROF-001",
  "nome": "Carlos Oliveira",
  "senioridade": "Senior",
  "posicaoRanking": 1,
  "scoreTotal": 95.40,
  "scoreDetalhado": {
    "core": 50,
    "correlatas": 20,
    "suporte": 15,
    "complementares": 10
  },
  "competenciasMatch": ["Python", "SQL", "AWS", "Spark"],
  "lacunas": ["Databricks"]
}
```

# 12. DTOs previstos

|     |
| --- |

DTO

|     |
| --- |

Finalidade

|     |
| --- |

Campos preliminares

|     |
| --- |

TotalDTO

|     |
| --- |

Indicadores de quantidade

|     |
| --- |

total

|     |
| --- |

FerramentaDTO

|     |
| --- |

Ferramenta mais utilizada

|     |
| --- |

ferramenta, quantidade

|     |
| --- |

TecnologiaDTO

|     |
| --- |

Distribuições por tecnologia

|     |
| --- |

tecnologia, quantidade

|     |
| --- |

SenioridadeDTO

|     |
| --- |

Distribuição por senioridade

|     |
| --- |

senioridade, quantidade

|     |
| --- |

AreaDTO

|     |
| --- |

Distribuição por área

|     |
| --- |

area, quantidade

|     |
| --- |

ProjetoDTO

|     |
| --- |

Listagem de projetos

|     |
| --- |

idProcessamento, idProjeto, nomeProjeto, cliente, gestorResponsavel, dataInicioPrevista, duracaoEstimada, orcamento, descricao, objetivoGeral, profissionaisRecomendados

## 12.1 — Estrutura do DTO de projeto

O `ProjetoDTO` deverá representar o projeto e conter a coleção de profissionais recomendados:

```text
ProjetoDTO
├── idProcessamento
├── idProjeto
├── nomeProjeto
├── cliente
├── gestorResponsavel
├── dataInicioPrevista
├── duracaoEstimada
├── orcamento
├── descricao
├── objetivoGeral
└── profissionaisRecomendados: List<ProfissionalRecomendadoDTO>
```

O `ProfissionalRecomendadoDTO` deverá conter, no mínimo:

```text
ProfissionalRecomendadoDTO
├── idProfissional
├── nome
├── senioridade
├── vaga
├── posicaoRanking
├── scoreTotal
└── justificativa
```

# 13. Estrutura sugerida do projeto Kotlin

src/main/kotlin

└── br.com.noctua

    ├── controller

    │  
└── DashboardController.kt

    ├── service

    │  
└── DashboardService.kt

    ├── repository

    │  
├── DashboardJdbcRepository.kt

    │  
└── DashboardAthenaRepository.kt

    ├── dto

    │  
├── TotalDTO.kt

    │  
├── FerramentaDTO.kt

    │  
├── TecnologiaDTO.kt

    │  
├── SenioridadeDTO.kt

    │  
├── AreaDTO.kt

    │  
└── ProjetoDTO.kt

    └── config

        ├── DatabaseConfig.kt

        └── AthenaConfig.kt

# 14. Tratamento HTTP

|     |
| --- |

Status

|     |
| --- |

Situação

|     |
| --- |

Comportamento

|     |
| --- |

200 OK

|     |
| --- |

Consulta realizada com sucesso

|     |
| --- |

Retornar o objeto ou lista solicitada.

|     |
| --- |

204 No Content

|     |
| --- |

Sem dados, quando definido pela regra

|     |
| --- |

Retornar resposta sem conteúdo.

|     |
| --- |

400 Bad Request

|     |
| --- |

Requisição inválida

|     |
| --- |

Retornar erro de validação.

|     |
| --- |

500 Internal Server Error

|     |
| --- |

Falha interna ou na fonte de dados

|     |
| --- |

Registrar o erro e retornar resposta padronizada.

# 15. Pendências para a próxima versão

1. Identificar a estrutura das tabelas disponibilizadas no Athena.
2. Identificar os campos de funcionários, tecnologia e área existentes na base S3.
3. Validar as queries JDBC dos endpoints 2, 3, 7 e 8 e incorporar a consulta de projetos + profissionais recomendados definida nesta versão.
4. Definir as queries Athena dos endpoints 1, 4, 5 e 6.
5. Validar a definição de “número de projetos analisados” (projetos, processamentos ou outra métrica).
6. Implementar a alteração de banco para adicionar `Documento_Projeto.orcamento`.
7. Definir os DTOs finais e o agrupamento dos profissionais por projeto na camada Service.
8. Definir paginação para a listagem de projetos caso o volume de dados exija.
9. Definir autenticação, autorização, logs, timeout e tratamento de falhas do Athena.
10. Validar os critérios de aceite dos endpoints com o frontend.

# 16. Critérios de aceite

## 16.1 — Listagem de projetos e profissionais recomendados

- A API deve retornar todos os projetos disponíveis no banco NOCTUA.
- Cada projeto deve retornar seu orçamento quando informado.
- O orçamento deve ser obtido do campo `Documento_Projeto.orcamento`.
- Cada projeto deve conter os profissionais recomendados relacionados ao seu `Processamento`.
- A relação utilizada para os profissionais deve seguir `Processamento → Matching_Execucao → Matching_Execucao_Vaga → Candidato_Ranqueado`.
- Um mesmo profissional poderá aparecer mais de uma vez quando possuir diferentes associações de vaga/matching.
- A API não deve eliminar essas ocorrências por meio de `DISTINCT` ou regra equivalente nesta versão.
- O resultado final deve ser agrupado por projeto antes de ser enviado ao frontend.
- Os profissionais devem ser ordenados pelo campo `posicao_ranking` dentro de cada ocorrência de vaga.

## 16.2 — Orçamento

- A tabela `Documento_Projeto` deve possuir a coluna `orcamento DECIMAL(15,2) NULL`.
- O valor deve ser tratado como monetário.
- O campo poderá ser nulo quando o orçamento ainda não estiver informado.

# 17. Critérios de implementação

·      
O Controller não deverá conter
SQL.

·      
O Service não deverá executar
SQL diretamente.

·      
Consultas JDBC deverão ficar no DashboardJdbcRepository. A consulta de projetos e profissionais recomendados deverá ser executada nessa camada e seus resultados agrupados na camada Service.

·      
Consultas Athena deverão ficar
no DashboardAthenaRepository.

·      
Credenciais e configurações de
infraestrutura deverão ficar fora do código-fonte.

·      
O contrato REST deverá utilizar
DTOs.

·      
As queries somente deverão ser
implementadas após validação dos campos e relacionamentos reais.


# 8. Consultas JDBC — definição consolidada

## 8.1 Regra de negócio — Projeto

Cada `Processamento` representa um único projeto.

Portanto, para os indicadores de projetos, `Processamento` é a referência para a contagem de projetos analisados.

## 8.2 Regra de negócio — Tecnologia/Ferramenta

Neste dashboard, **Tecnologia** e **Ferramenta** possuem o mesmo conceito: correspondem ao campo `Vaga_Competencia.nome_competencia`.

A diferença está apenas na unidade de agregação:

- **Ferramenta mais utilizada:** quantidade de vagas que possuem determinada competência.
- **Projetos por tecnologia:** quantidade de projetos que possuem ao menos uma vaga com determinada competência.

A utilização de `COUNT(DISTINCT ...)` evita contar a mesma vaga ou o mesmo projeto mais de uma vez quando a competência aparece em múltiplos registros relacionados.

## 8.3 Alteração do banco — orçamento

A tabela `Documento_Projeto` deverá receber a coluna:

```sql
ALTER TABLE Documento_Projeto
ADD COLUMN orcamento DECIMAL(15,2) NULL;
```

O campo será utilizado na resposta da listagem de projetos.

---

## 8.4 Projetos analisados

### Endpoint

```http
GET /api/dashboard/projetos/total
```

### Objetivo

Retornar a quantidade total de projetos analisados.

### Regra

Como cada `Processamento` representa um projeto:

```sql
SELECT COUNT(*) AS total
FROM Processamento;
```

### Exemplo de retorno SQL

```json
{
  "total": 25
}
```

### Resposta da API

```json
{
  "total": 25
}
```

---

## 8.5 Ferramenta mais utilizada

### Endpoint

```http
GET /api/dashboard/ferramentas/mais-utilizada
```

### Objetivo

Retornar a competência mais utilizada entre as vagas processadas.

### Regra

`Vaga_Competencia.nome_competencia` representa a ferramenta/tecnologia.

A unidade de contagem é a vaga.

Para tratar empates corretamente, devem ser retornadas todas as competências que possuam a maior quantidade de vagas.

### Query

```sql
WITH contagem AS (
    SELECT
        vc.nome_competencia AS ferramenta,
        COUNT(DISTINCT vc.fk_vaga_processada) AS quantidade
    FROM Vaga_Competencia vc
    GROUP BY vc.nome_competencia
)
SELECT
    ferramenta,
    quantidade
FROM contagem
WHERE quantidade = (
    SELECT MAX(quantidade)
    FROM contagem
);
```

### Exemplo de retorno

```json
[
  {
    "ferramenta": "SQL",
    "quantidade": 12
  },
  {
    "ferramenta": "Java",
    "quantidade": 12
  }
]
```

Caso não existam registros de competências, a API deverá retornar uma lista vazia:

```json
[]
```

---

## 8.6 Projetos × Tecnologia

### Endpoint

```http
GET /api/dashboard/projetos/tecnologia
```

### Objetivo

Retornar a quantidade de projetos associados a cada tecnologia/ferramenta.

### Regra

A tecnologia/ferramenta é representada por `Vaga_Competencia.nome_competencia`.

A contagem deve ser feita por projeto (`Processamento`), e não por vaga.

Uma mesma tecnologia presente em duas ou mais vagas do mesmo projeto deve contar apenas uma vez para aquele projeto.

### Query

```sql
SELECT
    vc.nome_competencia AS tecnologia,
    COUNT(DISTINCT vp.fk_processamento) AS quantidade
FROM Vaga_Competencia vc
INNER JOIN Vaga_Processada vp
    ON vp.id_vaga_processada = vc.fk_vaga_processada
GROUP BY vc.nome_competencia
ORDER BY quantidade DESC;
```

### Exemplo de retorno

```json
[
  {
    "tecnologia": "SQL",
    "quantidade": 12
  },
  {
    "tecnologia": "Java",
    "quantidade": 8
  },
  {
    "tecnologia": "AWS",
    "quantidade": 7
  },
  {
    "tecnologia": "Python",
    "quantidade": 5
  }
]
```

Caso não existam registros, retornar:

```json
[]
```

---

## 8.7 Listagem de projetos + profissionais recomendados

### Endpoint

```http
GET /api/dashboard/projetos
```

### Objetivo

Retornar todos os projetos e, para cada projeto, os profissionais recomendados associados às vagas do matching.

### Relacionamento

```text
Processamento
    │
    ├── Documento_Projeto
    │
    └── Matching_Execucao
            │
            └── Matching_Execucao_Vaga
                    │
                    └── Candidato_Ranqueado
```

### Regra de profissionais

Um profissional poderá aparecer mais de uma vez no retorno.

Não deverá ser aplicada deduplicação global dos candidatos, pois o mesmo profissional pode ser recomendado para vagas diferentes dentro do mesmo projeto.

### Query

```sql
SELECT
    p.id_processamento,

    dp.id_documento,
    dp.nome_projeto,
    dp.cliente,
    dp.gestor_responsavel,
    dp.data_inicio_prevista,
    dp.duracao_estimada,
    dp.orcamento,
    dp.descricao,
    dp.objetivo_geral,

    mev.id_matching_execucao_vaga,
    mev.titulo_vaga,
    mev.senioridade_vaga,

    cr.id_candidato_ranqueado,
    cr.id_profissional_externo,
    cr.nome,
    cr.senioridade,
    cr.posicao_ranking,
    cr.score_total,
    cr.justificativa_curta

FROM Processamento p

INNER JOIN Documento_Projeto dp
    ON dp.fk_processamento = p.id_processamento

INNER JOIN Matching_Execucao me
    ON me.fk_processamento = p.id_processamento

INNER JOIN Matching_Execucao_Vaga mev
    ON mev.fk_matching_execucao = me.id_matching_execucao

INNER JOIN Candidato_Ranqueado cr
    ON cr.fk_matching_execucao_vaga =
       mev.id_matching_execucao_vaga

ORDER BY
    p.id_processamento,
    mev.id_matching_execucao_vaga,
    cr.posicao_ranking;
```

### Exemplo de resultado SQL

| Projeto | Vaga | Profissional | Ranking | Score |
|---|---|---|---:|---:|
| Data Lake | Eng. Dados | Carlos Oliveira | 1 | 95.40 |
| Data Lake | Eng. Dados | João Santos | 2 | 91.70 |
| Data Lake | Arquiteto de Dados | Carlos Oliveira | 1 | 93.20 |
| Cloud | DevOps | Pedro Oliveira | 1 | 94.10 |

### Regra do Service

O `DashboardService` deverá transformar o resultado achatado da consulta em uma estrutura hierárquica:

```text
Projeto
 ├── dados do projeto
 └── profissionaisRecomendados
       ├── profissional
       ├── profissional
       └── profissional
```

### Exemplo de resposta

```json
[
  {
    "idProcessamento": 1,
    "idProjeto": 101,
    "nomeProjeto": "Implantação de Data Lake",
    "cliente": "Empresa ABC",
    "gestorResponsavel": "João Silva",
    "dataInicioPrevista": "2026-10-15",
    "duracaoEstimada": "180 dias",
    "orcamento": 350000.00,
    "descricao": "Implantação de uma plataforma de dados.",
    "objetivoGeral": "Modernizar a plataforma de dados.",
    "profissionaisRecomendados": [
      {
        "idProfissional": "PROF-001",
        "nome": "Carlos Oliveira",
        "senioridade": "Senior",
        "vaga": "Engenheiro de Dados",
        "posicaoRanking": 1,
        "scoreTotal": 95.40,
        "justificativa": "Alta compatibilidade."
      },
      {
        "idProfissional": "PROF-002",
        "nome": "João Santos",
        "senioridade": "Senior",
        "vaga": "Engenheiro de Dados",
        "posicaoRanking": 2,
        "scoreTotal": 91.70,
        "justificativa": "Boa aderência."
      },
      {
        "idProfissional": "PROF-001",
        "nome": "Carlos Oliveira",
        "senioridade": "Senior",
        "vaga": "Arquiteto de Dados",
        "posicaoRanking": 1,
        "scoreTotal": 93.20,
        "justificativa": "Alta compatibilidade."
      }
    ]
  }
]
```

---

# 9. Status das consultas JDBC

| Endpoint | Fonte | Status |
|---|---|---|
| `/api/dashboard/projetos/total` | NOCTUA / JDBC | Definida |
| `/api/dashboard/ferramentas/mais-utilizada` | NOCTUA / JDBC | Definida |
| `/api/dashboard/projetos/tecnologia` | NOCTUA / JDBC | Definida |
| `/api/dashboard/projetos` | NOCTUA / JDBC | Definida |

As consultas Athena permanecem dependentes da estrutura efetiva da base no S3/Athena.
