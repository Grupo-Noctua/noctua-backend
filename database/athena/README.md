# Consultas Athena — funcionários

Os quatro SQLs atuais usam a tabela `silver`, conforme a camada escolhida para os indicadores de funcionários. O job `glue-silver-job` grava o dataset principal em Parquet no prefixo `s3://noctua-2026-bucket-silver/silver/`, catalogado por `noctua-silver-crawler` no database `noctua_analytics`. Parquet preserva os tipos e os limites das colunas, inclusive quando as respostas textuais contêm vírgulas.

## Fontes e contrato

| Arquivo | Fonte | Colunas retornadas |
|---|---|---|
| `funcionarios-total.sql` | `silver` | `total` |
| `funcionarios-tecnologia.sql` | Colunas numéricas `sql`, `r`, `python`, `c_c_c`, etc. de `silver` | `tecnologia`, `quantidade` |
| `funcionarios-senioridade.sql` | `silver.nivel` | `senioridade`, `quantidade` |
| `funcionarios-area.sql` | `silver.atuacao`, com contagem distinta de `id` | `area`, `quantidade` |

A variável `ATHENA_DATABASE` deve apontar para `noctua_analytics`. A consulta de tecnologia também qualifica esse database explicitamente. A tabela deve existir antes do deploy com Athena habilitado; criar a stack não executa nem valida todo o processamento dos dados.

## Regras adotadas

- Total usa `COUNT(*)`: cada linha conta, inclusive duplicidades da origem. Não usa `numero_de_funcionarios`, que representa o tamanho da empresa.
- Tecnologia usa `UNNEST` sobre as 13 colunas numéricas listadas no SQL e `COUNT_IF(utiliza = 1)`. Confirme no catálogo que essas colunas têm tipo numérico. Um funcionário pode usar várias linguagens, então a soma pode superar o total.
- A opção “não utilizo nenhuma linguagem” não está na lista de tecnologias, mas esses funcionários continuam no total.
- Nomes como `c/c++/c#` e `visual_basic/vba` são rótulos da resposta; as colunas de origem normalizadas são `c_c_c` e `visual_basic_vba`.
- Senioridade considera somente `Sênior`, `Pleno` e `Júnior`, após remover espaços externos. Outros níveis e nulos não entram nessa distribuição.
- Área agrupa `TRIM(atuacao)` e usa `COUNT(DISTINCT id)`. Atuações vazias/nulas são excluídas; IDs nulos não contam. Essa regra de deduplicação difere do total.
- A saída auxiliar `silver_gerais/`, incluindo `cargo_map` em JSON textual, não alimenta essas consultas. Ela fica fora do prefixo catalogado para não misturar esquemas.

## Execução e integração

Os aliases são compatíveis com `DashboardAthenaRepository`, sem alteração no contrato REST. As consultas são carregadas de arquivos externos no startup; reiniciar o backend após editá-las.

Depois de executar o job Silver com sucesso, execute o crawler e confira a tabela com `SHOW COLUMNS IN noctua_analytics.silver`. Valide os quatro SQLs atuais no Athena e compare seus resultados com os endpoints HTTP. O [roteiro de testes](../../docs/TESTE_ATHENA_INSOMNIA.md) contém também exemplos anteriores baseados em Gold; para esta implantação, use os SQLs desta pasta e o contrato Silver descrito acima.
