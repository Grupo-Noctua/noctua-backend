# Contrato REST — Dashboard

Base: `/api/dashboard`. Todos os endpoints usam `GET`, não exigem parâmetros e retornam JSON. Contagens são inteiros de 64 bits; listas vazias são `[]`.

| Endpoint | Fonte | Resposta 200 |
|---|---|---|
| `/funcionarios/total` | Athena | `{"total":42}` |
| `/projetos/total` | JDBC | `{"total":25}` |
| `/ferramentas/mais-utilizada` | JDBC | `[{"ferramenta":"Java","quantidade":12},{"ferramenta":"SQL","quantidade":12}]` |
| `/funcionarios/tecnologia` | Athena | `[{"tecnologia":"Kotlin","quantidade":7}]` |
| `/funcionarios/senioridade` | Athena | `[{"senioridade":"Senior","quantidade":5}]` |
| `/funcionarios/area` | Athena | `[{"area":"Dados","quantidade":4}]` |
| `/projetos/tecnologia` | JDBC | `[{"tecnologia":"SQL","quantidade":12}]` |
| `/projetos` | JDBC | Lista hierárquica abaixo |

Os valores são exemplos de contrato, não dados consultados em produção. `ferramenta`, `tecnologia`, `senioridade` e `area` podem ser nulos se a fonte devolver um agrupamento nulo. `quantidade` e `total` não são nulos.

## Listagem de projetos

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

- `idProcessamento`: número inteiro obrigatório, chave do agrupamento e referência para contagem.
- `idProjeto`: `Documento_Projeto.id_documento`, nullable quando não há documento.
- Campos documentais textuais são strings nullable; `dataInicioPrevista` é `YYYY-MM-DD` ou null.
- `orcamento`: número decimal ou null; sem conversão para ponto flutuante no backend.
- `profissionaisRecomendados`: sempre uma lista, vazia quando não há candidatos.
- O identificador externo do profissional é string; dados ausentes do candidato permanecem nulos. Ranking é inteiro e score é decimal.
- Repetições de profissional são intencionais e mantidas; não usar o ID externo isoladamente como chave única na renderização da lista.
- Projetos são ordenados por processamento e profissionais por ocorrência de vaga/ranking. O ranking reinicia por vaga.
- A resposta não é paginada nesta versão.

## Erros

Uma falha interna, JDBC ou Athena retorna 500 com `Content-Type: application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Erro interno",
  "status": 500,
  "detail": "Não foi possível consultar os dados do dashboard.",
  "instance": "/api/dashboard/funcionarios/total",
  "errorId": "9c97c9d5-5a75-42ad-9be7-5ee3218bb0d7"
}
```

O `errorId` permite localizar a exceção nos logs. SQL, credenciais e stack trace não são expostos na resposta. Athena desabilitado/sem SQL não equivale a base vazia: responde com erro.

Erros reconhecidos pelo Spring MVC preservam seu status (400 para requisição inválida quando aplicável, 404 para rota inexistente, 405 para método não suportado). Eles usam o tratamento `ProblemDetail` do framework e não necessariamente possuem `errorId`.

## Integração com o frontend

- Usar 200 com zero/lista vazia como ausência legítima de dados; 500 como falha de carregamento.
- Suportar mais de uma ferramenta empatada.
- Exibir um estado de informação ausente para orçamento/dados documentais nulos.
- Preservar as recomendações repetidas e o contexto de vaga.
- Para implantação em mesma origem, rotear `/api` por proxy para a API. Não há liberação CORS global configurada; uma origem separada requer configuração conforme o ambiente acordado.
