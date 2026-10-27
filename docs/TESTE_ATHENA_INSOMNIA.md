# Teste completo — Athena, backend e Insomnia

Este roteiro valida os quatro indicadores de funcionários com AWS real. Execute os comandos PowerShell na raiz do projeto. Os valores entre `<...>` devem ser substituídos pelos valores do ambiente.

## 1. Conferir a tabela gold diretamente no Athena

No console AWS, abra Athena, selecione a região, o workgroup, o catálogo e o database que a API utilizará. Execute cada comando separadamente:

```sql
SHOW TABLES;
```

```sql
DESCRIBE gold;
```

```sql
SELECT id, nivel, cargo, linguagens, partition_0
FROM gold
LIMIT 5;
```

Esperado:

- `gold` expõe `id`, `nivel`, `cargo`, `linguagens` e `partition_0` como strings, conforme o esquema informado.
- `cargo` contém pares separados por `|`, com nome do cargo e valor, como `analytics_engineer=null|analista_de_dados/data_analyst=1`. Somente valores 1 indicam associação ao cargo; conferir os valores efetivos na fonte.
- `linguagens` contém pares separados por `|`, como `sql=1|r=0|python=1`, no formato observado no CSV.
- Cada linha representa um funcionário e cada linguagem aparece uma vez nessa linha.

Configure `ATHENA_DATABASE` com o database que contém a tabela `gold`. Não confunda nome da tabela com nome do database. O tipo string, sozinho, não confirma o formato: se a amostra contiver JSON ou outro texto diferente do CSV, será necessário adaptar o parser antes de validar a consulta por tecnologia.

Confira a quantidade de registros e de IDs:

```sql
SELECT
    COUNT(*) AS total_linhas,
    COUNT(DISTINCT NULLIF(TRIM(id), '')) AS ids_distintos,
    COUNT_IF(id IS NULL OR TRIM(id) = '') AS ids_ausentes
FROM gold;
```

As consultas seguem os exemplos fornecidos e contam linhas, sem deduplicar funcionários. Diferenças entre o total e IDs distintos precisam ser investigadas para confirmar a granularidade.

Confira também as partições:

```sql
SELECT partition_0, COUNT(*) AS quantidade
FROM gold
GROUP BY partition_0
ORDER BY partition_0;
```

As quatro consultas leem todas as partições. Se `partition_0` representar versões da mesma base, as pessoas podem ser contadas várias vezes. Nesse caso, defina o recorte com base no significado da partição e aplique o mesmo filtro aos quatro SQLs; não assuma que o maior valor textual corresponde ao snapshot atual.

Verifique os níveis presentes, inclusive os que o filtro não inclui:

```sql
SELECT nivel, COUNT(*) AS quantidade
FROM gold
GROUP BY nivel
ORDER BY quantidade DESC;
```

Verifique pares malformados ou indicadores não binários no texto:

```sql
WITH pares AS (
    SELECT
        entrada,
        TRIM(split_part(entrada, '=', 1)) AS tecnologia,
        TRIM(split_part(entrada, '=', 2)) AS utiliza
    FROM gold
    CROSS JOIN UNNEST(split(COALESCE(linguagens, ''), '|')) AS itens(entrada)
)
SELECT entrada, COUNT(*) AS ocorrencias
FROM pares
WHERE TRIM(entrada) <> ''
  AND (cardinality(split(entrada, '=')) <> 2
       OR tecnologia = ''
       OR TRY_CAST(utiliza AS INTEGER) IS NULL
       OR TRY_CAST(utiliza AS INTEGER) NOT IN (0, 1))
GROUP BY entrada
ORDER BY ocorrencias DESC;
```

Valores inválidos são ignorados pelo indicador de uso; investigue-os na origem para confirmar os números. `TRY_CAST` evita que textos como `null` derrubem toda a consulta.

## 2. Executar os quatro SELECTs e anotar os resultados

Abra e execute **um arquivo por vez** no editor Athena:

1. `database/athena/funcionarios-total.sql`
2. `database/athena/funcionarios-tecnologia.sql`
3. `database/athena/funcionarios-senioridade.sql`
4. `database/athena/funcionarios-area.sql`

O Athena deve retornar os aliases `total`, `tecnologia/quantidade`, `senioridade/quantidade` e `area/quantidade`. A API exige exatamente esses nomes.

Para conferir independentemente a quantidade de usuários das linguagens SQL e Python no texto:

```sql
SELECT
    COUNT_IF(regexp_like(COALESCE(linguagens, ''), '(^|\|)\s*sql\s*=\s*1\s*(\||$)')) AS total_sql,
    COUNT_IF(regexp_like(COALESCE(linguagens, ''), '(^|\|)\s*python\s*=\s*1\s*(\||$)')) AS total_python
FROM gold;
```

Com os indicadores 0/1 do CSV, esses números devem corresponder às linhas `sql` e `python` da consulta por tecnologia, quando essas linguagens existirem. A expressão regular verifica os limites do par para não confundir nomes ou contar valores como 10. Uma diferença pode indicar pares repetidos ou valores fora do formato esperado.

Critérios de conferência:

- Total corresponde a `COUNT(*) FROM gold`.
- Tecnologia conta somente o valor 1, excluindo a opção de não usar linguagem, com e sem acento.
- Uma linguagem presente no texto com todos os valores 0 aparece com quantidade 0.
- A soma por tecnologia pode superar o total, pois um funcionário pode usar várias linguagens.
- Senioridade inclui somente `Júnior`, `Pleno` e `Sênior`, como no exemplo fornecido. A soma pode ser menor que o total.
- Área conta os pares de `cargo` com valor 1 e mantém o nome do cargo no campo `area`. Valores 0 e `null` não contam. Cargos presentes sem associações retornam zero. A soma pode ser menor ou maior que o total, conforme existam funcionários sem cargo marcado ou com vários cargos.
- A categoria `c/c++/c#` continua única: os dados não distinguem essas linguagens individualmente.

### Conferir os valores da coluna cargo

Execute esta consulta para conferir quais valores realmente aparecem em cada cargo:

```sql
WITH pares AS (
    SELECT
        TRIM(split_part(entrada, '=', 1)) AS area,
        TRIM(split_part(entrada, '=', 2)) AS valor
    FROM gold
    CROSS JOIN UNNEST(split(COALESCE(cargo, ''), '|')) AS itens(entrada)
)
SELECT area, valor, COUNT(*) AS ocorrencias
FROM pares
WHERE area <> ''
GROUP BY area, valor
ORDER BY area, valor;
```

As ocorrências com valor 1 devem corresponder às quantidades do endpoint de área para os indicadores binários esperados. Se todos os valores forem `null`, os cargos presentes terão quantidade zero. Isso reflete os dados da coluna `cargo`; a consulta não usa `cargo_atual` ou `atuacao` como substituição.

## 3. Preparar as credenciais AWS

O **backend** autentica na AWS usando a cadeia padrão do SDK. O Insomnia chama sua API local e **não recebe as chaves AWS**.

Para este roteiro, use as variáveis:

```dotenv
AWS_ACCESS_KEY_ID=<access-key>
AWS_SECRET_ACCESS_KEY=<secret-key>
AWS_SESSION_TOKEN=<session-token>
```

`AWS_SESSION_TOKEN` é necessário somente para credenciais temporárias, incluindo as obtidas por SSO/STS. Com credenciais de longa duração, deixe-o vazio ou omita-o. Ao renovar uma sessão temporária, atualize as três credenciais e recrie o container ou reinicie o processo Java com o ambiente atualizado. Preencha apenas o ambiente local; o [exemplo do laboratório](../examples/env/docker-lab.env.example) contém campos de credenciais vazios.

A identidade usada pela API precisa ter acesso ao workgroup e às fontes:

- Athena: `StartQueryExecution`, `GetQueryExecution`, `GetQueryResults`, `StopQueryExecution`.
- Glue: leitura dos databases, tabelas e partições usados nas consultas, como `GetDatabase`, `GetTable`, `GetPartitions` e `BatchGetPartition`, conforme a fonte.
- S3: listar/ler os dados de origem e gravar/ler os resultados no bucket configurado; acesso à localização do bucket quando necessário.
- KMS e Lake Formation: permissões adicionais somente quando usados pelo ambiente.

Configure uma pasta de resultados, por exemplo `s3://<bucket-de-resultados>/noctua/`, em `ATHENA_OUTPUT_LOCATION`, ou deixe essa variável vazia se o workgroup já configurar os resultados. Use a região correta. O sucesso no console com outro usuário não garante acesso para as chaves da API.

### Verificação opcional com AWS CLI

Se o AWS CLI estiver instalado, configure **no mesmo PowerShell** as credenciais que usará no backend:

```powershell
$env:AWS_ACCESS_KEY_ID = '<access-key>'
$env:AWS_SECRET_ACCESS_KEY = '<secret-key>'
$env:AWS_SESSION_TOKEN = '<session-token-ou-vazio>'
$env:AWS_REGION = '<regiao>'
aws sts get-caller-identity
```

Esse comando confirma a identidade e a validade das credenciais, mas não valida permissões do Athena/S3. Para consultar metadados com essa identidade:

```powershell
aws glue get-table --database-name '<database>' --name gold --region $env:AWS_REGION
```

Essas variáveis no host não entram automaticamente no container. No caminho Docker abaixo, preencha os mesmos valores no `.env`, que será passado com `--env-file`.

## 4. Preparar o banco relacional

A aplicação também possui datasource JDBC. Configure o banco NOCTUA existente para executar o backend completo e validar `/actuator/health` e projetos.

- Confirme as tabelas oficiais e a coluna `Documento_Projeto.orcamento`.
- Use `jdbc:mysql://localhost:3306/NOCTUA` ao executar Java no host com banco local.
- Use `jdbc:mysql://host.docker.internal:3306/NOCTUA` ao executar o backend no Docker Desktop e o banco no host.
- Se o banco for remoto, informe seu hostname e a porta reais.

O arquivo `src/test/resources/schema.sql` é uma fixture H2 com limpeza de objetos; não é o DDL do banco real. O CSV também não é carregado no MySQL por este backend.

## 5. Rodar com Docker Desktop

Este caminho usa Java 21 e Maven dentro da imagem, independentemente do Java instalado no Windows.

1. Abra o Docker Desktop e aguarde o mecanismo de containers Linux iniciar.
2. Confira `docker version`: deve mostrar Client e Server sem erro.
3. Crie `.env` na raiz copiando `examples/env/docker-lab.env.example` pelo editor e ajuste os valores para seu ambiente. O exemplo usa túnel na porta 13306; a configuração abaixo ilustra MySQL no host na porta 3306:

```dotenv
DB_URL=jdbc:mysql://host.docker.internal:3306/NOCTUA
DB_USERNAME=<usuario-do-banco>
DB_PASSWORD=<senha-do-banco>
PORT=8080
ATHENA_ENABLED=true
AWS_REGION=<regiao>
ATHENA_DATABASE=<database-do-athena>
ATHENA_CATALOG=AwsDataCatalog
ATHENA_WORKGROUP=<workgroup>
ATHENA_OUTPUT_LOCATION=s3://<bucket-de-resultados>/noctua/
ATHENA_QUERY_TIMEOUT=30s
ATHENA_POLL_INTERVAL=500ms
ATHENA_MAX_ROWS=10000
ATHENA_SQL_TOTAL=file:/app/config/funcionarios-total.sql
ATHENA_SQL_TECNOLOGIA=file:/app/config/funcionarios-tecnologia.sql
ATHENA_SQL_SENIORIDADE=file:/app/config/funcionarios-senioridade.sql
ATHENA_SQL_AREA=file:/app/config/funcionarios-area.sql
AWS_ACCESS_KEY_ID=<access-key>
AWS_SECRET_ACCESS_KEY=<secret-key>
AWS_SESSION_TOKEN=<session-token-ou-vazio>
```

No `--env-file` do Docker, use os valores sem aspas adicionais. Para um token ausente, use `AWS_SESSION_TOKEN=`. Substitua todos os placeholders.

Na raiz do projeto, verifique os arquivos e construa a imagem:

```powershell
Test-Path -LiteralPath '.env'
Test-Path -LiteralPath 'database/athena'
docker build -t noctua-backend .
```

O build executa os testes H2/mocks antes de gerar a imagem. Somente após `docker build` concluir com sucesso, execute:

```powershell
docker run --rm --name noctua-backend -p 8080:8080 --env-file .env noctua-backend
```

Aguarde a mensagem de aplicação iniciada. Para consultar logs em outro terminal:

```powershell
docker logs -f noctua-backend
```

Para parar, use `Ctrl+C` no terminal principal ou `docker stop noctua-backend`. A imagem inclui os quatro SQLs em `/app/config`. Após mudar esses SQLs, reconstrua a imagem e recrie o container. Após mudar credenciais, recrie o container com o `--env-file` atualizado: as variáveis são fixadas na criação do container.

## 6. Rodar diretamente com Java 21 e Maven

Use este caminho quando `java -version` indicar Java 21 e `mvn -version` indicar Maven 3.9+ usando Java 21.

O Spring Boot não carrega `.env` automaticamente. Na raiz do projeto, configure no PowerShell que iniciará a aplicação:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/NOCTUA'
$env:DB_USERNAME = '<usuario-do-banco>'
$env:DB_PASSWORD = '<senha-do-banco>'
$env:PORT = '8080'
$env:ATHENA_ENABLED = 'true'
$env:AWS_REGION = '<regiao>'
$env:ATHENA_DATABASE = '<database-do-athena>'
$env:ATHENA_CATALOG = 'AwsDataCatalog'
$env:ATHENA_WORKGROUP = '<workgroup>'
$env:ATHENA_OUTPUT_LOCATION = 's3://<bucket-de-resultados>/noctua/'
$env:ATHENA_SQL_TOTAL = 'file:./database/athena/funcionarios-total.sql'
$env:ATHENA_SQL_TECNOLOGIA = 'file:./database/athena/funcionarios-tecnologia.sql'
$env:ATHENA_SQL_SENIORIDADE = 'file:./database/athena/funcionarios-senioridade.sql'
$env:ATHENA_SQL_AREA = 'file:./database/athena/funcionarios-area.sql'
$env:AWS_ACCESS_KEY_ID = '<access-key>'
$env:AWS_SECRET_ACCESS_KEY = '<secret-key>'
$env:AWS_SESSION_TOKEN = '<session-token-ou-vazio>'
```

Com credenciais não temporárias, atribua `''` ao token. Deixe o output vazio se configurado no workgroup. Então:

```powershell
mvn -B verify
```

Após `BUILD SUCCESS`:

```powershell
java -jar target/noctua-backend-0.0.1-SNAPSHOT.jar
```

Os testes automatizados usam H2 e mocks, não validam os dados AWS. A validação real acontece nas etapas seguintes.

## 7. Testar no Insomnia

1. Abra o Insomnia e use **Import / From File** (o nome do menu pode variar por versão).
2. Selecione `requests/noctua-insomnia.postman_collection.json`. É uma coleção Postman v2.1, formato aceito pelo importador do Insomnia.
3. Confira a variável importada `baseUrl = http://localhost:8080`. Se o importador não converter a variável, configure-a no ambiente ativo do Insomnia ou substitua `{{baseUrl}}` na URL pelo endereço literal.
4. Configure o timeout do cliente para pelo menos **60 segundos**: a consulta Athena tem orçamento padrão de 30 segundos e o cancelamento pode acrescentar até 2 segundos.
5. Use autenticação **None / No Auth**, sem corpo, com `Accept: application/json`. A API atual não exige autenticação HTTP. As credenciais AWS ficam no processo do backend.

A coleção inclui health e os oito endpoints. Para funcionários, execute:

| Ordem | Método e URL | Verificação |
|---|---|---|
| 1 | `GET http://localhost:8080/actuator/health` | 200 e `status: UP` com o JDBC disponível |
| 2 | `GET http://localhost:8080/api/dashboard/funcionarios/total` | 200 e objeto com `total` igual ao SQL |
| 3 | `GET http://localhost:8080/api/dashboard/funcionarios/tecnologia` | 200 e lista com `tecnologia` e `quantidade` |
| 4 | `GET http://localhost:8080/api/dashboard/funcionarios/senioridade` | 200 e lista com `senioridade` e `quantidade` |
| 5 | `GET http://localhost:8080/api/dashboard/funcionarios/area` | 200 e lista com `area` e `quantidade` |

Exemplos de formato, **não resultados medidos na sua base**:

```json
{"total": 100}
```

```json
[{"tecnologia": "sql", "quantidade": 80}, {"tecnologia": "python", "quantidade": 65}]
```

```json
[{"senioridade": "Pleno", "quantidade": 40}]
```

```json
[{"area": "engenharia_de_dados/data_engineer", "quantidade": 30}, {"area": "analytics_engineer", "quantidade": 0}]
```

Compare cada resposta com a execução do respectivo arquivo no Athena, usando o mesmo database/workgroup e uma base sem alterações entre as chamadas. O health verifica JDBC, **não Athena**; somente os endpoints de funcionários validam a integração AWS.

Se preferir criar as requisições manualmente, basta usar as URLs da tabela. Elas também estão em `requests/dashboard.http`.

## 8. Diagnóstico dos erros mais comuns

| Sintoma | O que conferir |
|---|---|
| Conexão recusada no Insomnia | Backend iniciado, porta 8080 disponível e mapeamento `-p 8080:8080` |
| Docker informa erro no pipe/daemon | Docker Desktop e mecanismo Linux iniciados |
| Java incompatível / Maven não encontrado | Usar Java 21/Maven 3.9+ ou o caminho Docker |
| Startup falha com SQL ausente | Quatro caminhos `ATHENA_SQL_*` em `/app/config` e imagem reconstruída com os SQLs |
| Startup rejeita configuração Athena | `ATHENA_DATABASE`, região, catálogo, workgroup e quatro caminhos preenchidos |
| 500 com SQL Athena não configurado no log | `ATHENA_ENABLED=true` no processo/container que está rodando |
| Credenciais não encontradas / token inválido ou expirado | Chave, segredo e token da mesma sessão, passados ao backend; renovar e reiniciar |
| `AccessDenied` | Permissões Athena, Glue, S3 e, quando aplicável, KMS/Lake Formation |
| Consulta termina em `FAILED` | Localizar o query ID no log e abrir a execução no Athena para ver a causa detalhada |
| Tabela não encontrada | Database, catálogo, região e existência de `gold` no Glue/Athena |
| Erro de tipo em `UNNEST` | Usar o SQL atualizado: `UNNEST(split(COALESCE(linguagens, ''), '|'))`, pois a coluna é string |
| Falha na localização dos resultados | Configurar bucket/prefixo S3 ou resultados no workgroup e conferir permissões |
| Timeout | Conferir fila/workgroup e duração da consulta; ajustar `ATHENA_QUERY_TIMEOUT` e timeout do Insomnia conforme a medição |
| Health 503 / falha JDBC | URL, usuário, senha, conectividade e disponibilidade do banco relacional |
| Área retorna somente zeros | Conferir os pares de `cargo`: apenas valores 1 contam; valores `null` e 0 não indicam associação |
| Contagens inesperadas | Granularidade, pares repetidos, duplicidades entre partições, recortes inconsistentes e filtro de senioridade; a soma por área não precisa coincidir com o total |

Falhas internas retornam HTTP 500 em `application/problem+json`, com um `errorId`. Copie esse identificador e procure-o nos logs do backend. A causa técnica fica no log, não no JSON retornado.

## 9. Critério de conclusão

- Quatro SQLs executados com sucesso diretamente no Athena.
- Credenciais usadas pelo backend com acesso às mesmas fontes.
- Quatro endpoints retornando HTTP 200, aliases corretos e números conferidos com os SQLs.
- Regras de uma linha por funcionário, um par por linguagem/cargo, filtro de nível e significado das partições confirmadas.
- Health JDBC e endpoints de projetos validados quando o objetivo for aceitar o dashboard completo.

Este roteiro prepara a homologação; não representa uma execução já realizada na sua conta AWS.
