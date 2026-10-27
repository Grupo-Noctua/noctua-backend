# Execução local e referência de configuração

Backend em **Kotlin 2.1 / Spring Boot 3.5 / Java 21**, com JDBC para projetos e AWS SDK v2 para Amazon Athena. Implementação baseada na [especificação v3](Especificacao_Tecnica_API_Dashboard_Noctua_v3.md), priorizando as regras da seção final “Consultas JDBC — definição consolidada”. Execute os comandos deste guia na raiz do repositório.

## Entregas

- Oito endpoints REST com DTOs e arquitetura Controller → Service → Repository.
- Consultas JDBC consolidadas, empates de ferramentas e agrupamento dos profissionais por processamento.
- Orçamento e score com `BigDecimal`; datas com `LocalDate`; contagens com `Long`.
- Integração Athena com polling, paginação, timeout, cancelamento e SQL externo configurável.
- Erros HTTP padronizados em `application/problem+json` e identificador correlacionado aos logs.
- DDL MySQL com orçamento, Dockerfile e testes automatizados. Resultados registrados no [plano de testes](PLANO_TESTES.md).

**Estado da integração:** JDBC implementado com os campos documentados; conexão ao banco real ainda precisa de homologação. As quatro consultas em `database/athena/` usam a tabela `gold`, com `linguagens` do tipo string no formato `sql=1|python=1|...` do CSV. O esquema foi informado; falta homologar as consultas e a granularidade das partições no Athena. O modo padrão mantém Athena desabilitado; seus endpoints retornam 500 padronizado, sem simular dados.

## Documentação

- [Índice da documentação](README.md)
- [Plano de implementação](PLANO_IMPLEMENTACAO.md)
- [Plano de testes e resultados](PLANO_TESTES.md)
- [Contrato HTTP e decisões](CONTRATO_API.md)
- [Requisições de exemplo](../requests/dashboard.http)
- [Consultas Athena e regras dos indicadores](../database/athena/README.md)
- [Teste completo: AWS, backend e Insomnia](TESTE_ATHENA_INSOMNIA.md)

## Requisitos

- JDK 21 e Maven 3.9+, **ou Docker** com containers Linux.
- Banco NOCTUA em MySQL 8+ (necessário para CTEs), preparado conforme a seção abaixo.
- Para funcionários: database/tabelas Athena, workgroup e acesso aos buckets de origem e resultados.

## Compilar e testar

Com JDK 21 e Maven instalados:

```sh
mvn -B verify
```

No PowerShell, usando Docker (também funciona quando o Java local é 8):

```powershell
docker run --rm --mount "type=bind,source=$($PWD.Path),target=/app" --mount "type=volume,source=noctua-maven-cache,target=/root/.m2" -w /app maven:3.9.11-eclipse-temurin-21 mvn -B verify
```

Os testes usam H2 em memória e mocks do cliente Athena; não precisam de credenciais AWS nem do banco real. O JAR executável fica em `target/noctua-backend-0.0.1-SNAPSHOT.jar`; os relatórios ficam em `target/surefire-reports/`.

## Preparar o banco

1. Para uma base nova, revisar e aplicar o [DDL MySQL](../database/script-noctua.sql), que já inclui `Documento_Projeto.orcamento` com tipo `DECIMAL(15,2) NULL`.
2. O script contém `CREATE DATABASE` e `CREATE TABLE` sem controle de versão: não o reaplique sobre uma base existente. Nesse caso, compare o schema e prepare uma migração específica para as diferenças.
3. Confirmar nomes, tipos e relações utilizados em `DashboardJdbcRepository.kt`, incluindo a cardinalidade documento/processamento.
4. Configurar o usuário da API com acesso de leitura e a conexão apropriada ao ambiente. O [guia MySQL na EC2](GUIA_MYSQL_EC2.md) detalha o teste manual.

O DDL não roda automaticamente. `src/test/resources/schema.sql` é somente uma fixture de teste: contém comandos de limpeza e não deve ser aplicado ao banco real.

## Executar

Exemplo no PowerShell após configurar uma base existente:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/NOCTUA'
$env:DB_USERNAME = 'usuario_da_api'
$env:DB_PASSWORD = '<senha-do-ambiente>'
java -jar target/noctua-backend-0.0.1-SNAPSHOT.jar
```

Também é possível executar `mvn spring-boot:run` com as mesmas variáveis. Um arquivo `.env` não é carregado automaticamente pelo Spring Boot ao executar localmente; exporte as variáveis no processo.

Para executar em container:

```powershell
docker build -t noctua-backend .
docker run --rm -p 127.0.0.1:8080:8080 --env-file .env noctua-backend
```

Crie `.env` a partir do [exemplo básico](../.env.example), preenchendo os valores do ambiente. Ele usa Docker Desktop com MySQL no host e Athena desabilitado. Para MySQL na EC2 por túnel SSH e Athena, use o [exemplo do laboratório](../examples/env/docker-lab.env.example) e o [guia integrado](GUIA_TESTE_MYSQL_ATHENA.md). Veja as diferenças entre os ambientes em [examples/env](../examples/env/README.md).

O Dockerfile executa `mvn -B verify` antes de gerar a imagem final, que roda com o usuário não root `noctua`. Os quatro SQLs Athena já estão incluídos em `/app/config`, com permissão de leitura, sem necessidade de montagem. Mudanças nesses SQLs exigem reconstruir a imagem.

### Validar o empacotamento para automação

Com Docker e Python 3.10+, execute na raiz do projeto:

```powershell
docker build --no-cache-filter build --progress=plain -t noctua-backend:local-20261014 .
```

Após o build concluir com sucesso:

```powershell
python scripts/verify_image.py noctua-backend:local-20261014
```

O filtro força a execução dos testes no estágio `build`, mesmo quando há cache. O verificador compara os quatro SQLs com os arquivos de origem, confirma leitura por `noctua` sem bind mounts, presença do JAR e ausência de arquivos `.env`, `.env.*` e `*.env` no filesystem final e nas entradas do JAR. Ele usa containers temporários, removidos ao terminar, sem iniciar a API ou fornecer configurações externas. A exclusão desses arquivos do contexto continua definida em `.dockerignore`; a inspeção do filesystem final não é uma auditoria de camadas históricas.

A tag `local-20261014` identifica apenas esta validação local. Para publicação, será necessário usar a versão/commit e registrar o digest do ECR conforme o [plano de automação](PLANO_AUTOMACAO.md). A cópia local atual do backend não contém metadados Git para obter um commit. Esses testes não homologam conectividade MySQL, Athena ou permissões AWS.

## Variáveis de configuração

| Variável | Padrão | Uso |
|---|---|---|
| `PORT` | `8080` | Porta HTTP |
| `DB_URL` | `jdbc:mysql://localhost:3306/NOCTUA` | Conexão JDBC |
| `DB_USERNAME` / `DB_PASSWORD` | vazios | Credenciais externas |
| `DB_POOL_SIZE` | `10` | Máximo de conexões |
| `ATHENA_ENABLED` | `false` | Ativa a integração |
| `AWS_REGION` | `us-east-1` | Região Athena |
| `ATHENA_DATABASE` | vazio | Database do catálogo |
| `ATHENA_CATALOG` | `AwsDataCatalog` | Catálogo |
| `ATHENA_WORKGROUP` | `primary` | Workgroup |
| `ATHENA_OUTPUT_LOCATION` | vazio | URI `s3://.../`; se vazia, resultados devem estar configurados no workgroup |
| `ATHENA_QUERY_TIMEOUT` | `30s` | Orçamento de tempo de execução e leitura das páginas |
| `ATHENA_POLL_INTERVAL` | `500ms` | Intervalo entre consultas de status |
| `ATHENA_MAX_ROWS` | `10000` | Limite de linhas agregadas; exceder resulta em erro, sem resposta parcial |
| `ATHENA_SQL_TOTAL` | vazio | Recurso SQL para total de funcionários |
| `ATHENA_SQL_TECNOLOGIA` | vazio | Recurso SQL para funcionários por tecnologia |
| `ATHENA_SQL_SENIORIDADE` | vazio | Recurso SQL para funcionários por senioridade |
| `ATHENA_SQL_AREA` | vazio | Recurso SQL para funcionários por área |

JDBC tem timeout de consulta de 15 segundos e espera pelo pool de 5 segundos. O cancelamento Athena após falha pode acrescentar até 2 segundos ao orçamento da consulta. O limite de linhas Athena se refere ao resultado agregado, não à quantidade de funcionários na origem.

## Ativar Athena

1. Selecionar o database que contém `gold` e conferir `nivel`, `cargo` e `linguagens` (strings). Confirmar os pares de linguagem e cargo separados por `|`; apenas valores 1 entram nas respectivas distribuições. As consultas pressupõem uma linha por funcionário e consideram todas as partições; verificar se `partition_0` contém populações distintas ou versões repetidas.
2. Executar os quatro arquivos em `database/athena/` e homologar os resultados. Se os nomes das tabelas no catálogo forem diferentes, ajustar o `FROM`. As regras estão no README desse diretório e a execução completa no [guia Athena](TESTE_ATHENA_INSOMNIA.md).
3. Informar os caminhos `file:/.../arquivo.sql` (ou `classpath:...` para recursos incluídos no build), ativar Athena e reiniciar a aplicação. Os arquivos são lidos no startup.

| Consulta | Colunas exigidas | Resultado |
|---|---|---|
| total | `total` | Exatamente uma linha com inteiro não negativo, inclusive zero |
| tecnologia | `tecnologia`, `quantidade` | Zero ou mais linhas |
| senioridade | `senioridade`, `quantidade` | Zero ou mais linhas |
| area | `area`, `quantidade` | Zero ou mais linhas |

Dimensões podem ser nulas. Quantidades precisam ser inteiros não negativos representáveis em `Long`. Ordenação das distribuições de funcionários deve ser definida no SQL validado. Ausência de alias, contagem inválida e falhas da fonte geram erro, não zero artificial.

Exemplo de caminhos em um container:

```dotenv
ATHENA_ENABLED=true
ATHENA_DATABASE=noctua
ATHENA_SQL_TOTAL=file:/app/config/funcionarios-total.sql
ATHENA_SQL_TECNOLOGIA=file:/app/config/funcionarios-tecnologia.sql
ATHENA_SQL_SENIORIDADE=file:/app/config/funcionarios-senioridade.sql
ATHENA_SQL_AREA=file:/app/config/funcionarios-area.sql
```

Execute usando os SQLs incluídos na imagem:

```powershell
docker run --rm -p 127.0.0.1:8080:8080 --env-file .env noctua-backend
```

Os caminhos acima apontam para os arquivos incluídos pelo Dockerfile. Para experimentar SQLs específicos durante o desenvolvimento, é possível substituir `/app/config` por uma montagem somente leitura; a pasta local `config/` tem seus arquivos SQL ignorados pelo Git. Na imagem de deploy, versione os SQLs em `database/athena/` e reconstrua a imagem. As consultas constantes em `src/test/resources/athena` são usadas apenas nos testes.

As credenciais seguem a cadeia padrão do AWS SDK (variáveis de ambiente, perfil local ou IAM role). São necessários acessos a `StartQueryExecution`, `GetQueryExecution`, `GetQueryResults`, `StopQueryExecution`, metadados Glue e leitura/escrita dos buckets correspondentes; configurações de KMS/Lake Formation dependem do ambiente. Perfis locais precisam estar disponíveis ao processo/container.

## Verificar execução

```sh
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/dashboard/projetos/total
curl http://localhost:8080/api/dashboard/projetos
```

`/actuator/health` verifica a conectividade JDBC, mas não executa consultas pagas no Athena. Validar funcionários exige chamar os próprios endpoints com a integração configurada.

Autenticação/autorização e paginação de projetos estão pendentes na especificação. Os oito endpoints desta versão não exigem autenticação nem parâmetros. O contrato e as decisões para a próxima versão estão nos planos.
