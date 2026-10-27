# Teste integrado — MySQL na EC2 e Athena com backend local

Este roteiro continua o [guia MySQL na EC2](GUIA_MYSQL_EC2.md) após os testes JDBC da etapa 9. Não é necessário executar a etapa 10 (hospedar o backend em outra EC2) para testar Athena.

## Configuração consolidada

O [exemplo do laboratório](../examples/env/docker-lab.env.example) combina os dados do guia MySQL com os recursos Athena já informados no projeto. O `.env.example` da raiz é o exemplo básico, com Athena desabilitado:

| Configuração | Valor |
|---|---|
| Execução do backend | Docker Desktop no Windows, com comandos na raiz do projeto |
| Banco MySQL | `NOCTUA`, hospedado na EC2 |
| Acesso JDBC | `jdbc:mysql://host.docker.internal:13306/NOCTUA`, por túnel SSH no Windows |
| Usuário MySQL | `noctua_api`, com permissão de leitura |
| Pool JDBC | 3 conexões |
| API local | `http://localhost:8080` |
| Athena habilitado | `true` |
| Região | `us-east-1` |
| Database Athena | `noctua_analytics` |
| Catálogo | `AwsDataCatalog` |
| Workgroup | `noctua-athena-wg` |
| Resultados | Destino imposto pelo workgroup; `ATHENA_OUTPUT_LOCATION` vazio |
| SQLs Athena | Arquivos de `database/athena/` incluídos na imagem em `/app/config` |

Os nomes Athena foram preservados da configuração existente; sua disponibilidade e permissões ainda precisam ser confirmadas na conta AWS.

```text
                          ┌── túnel SSH → MySQL na EC2
Insomnia → Backend local ─┤
                          └── AWS SDK → Athena → S3
```

## 1. Preparar o arquivo .env

Pelo editor, copie `examples/env/docker-lab.env.example` para `.env` na raiz do projeto. Caso já exista um `.env`, atualize os campos necessários sem sobrescrever configurações locais inadvertidamente.

Preencha no `.env`:

- `DB_PASSWORD`: senha definida para `noctua_api` no MySQL.
- `AWS_ACCESS_KEY_ID`: chave da sessão atual do laboratório.
- `AWS_SECRET_ACCESS_KEY`: segredo da mesma sessão.
- `AWS_SESSION_TOKEN`: token da mesma sessão.

Obtenha as credenciais em **AWS Details → AWS CLI**, ou opção equivalente do Learner Lab. Use valores sem aspas adicionais, um por linha, no formato `CHAVE=valor`. Não compartilhe ou versione o `.env`.

As credenciais foram removidas do arquivo de exemplo. Se os valores anteriormente presentes ainda estiverem válidos, renove/revogue a sessão pelo mecanismo disponível no laboratório.

## 2. Manter o túnel MySQL aberto

Com o laboratório ativo e a EC2 ligada, execute no PowerShell, substituindo o caminho da chave e o IP público:

```powershell
ssh -i "C:\caminho\noctua.pem" -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -L 127.0.0.1:13306:127.0.0.1:3306 ubuntu@IP_PUBLICO_EC2
```

Se esse túnel já estiver funcionando, mantenha-o aberto em vez de iniciar outro na mesma porta.

Este roteiro pressupõe a configuração MySQL das etapas 1 a 9 do guia. Caso já tenha alterado o `bind-address` na etapa 10, siga a subseção “Manter acesso local depois da mudança” daquele guia para ajustar o túnel e a conta MySQL.

## 3. Conferir o Athena no console

Selecione a região `us-east-1`, o workgroup `noctua-athena-wg`, o catálogo `AwsDataCatalog` e o database `noctua_analytics` (com underscore, não hífen).

Execute separadamente:

```sql
SHOW TABLES;
```

```sql
DESCRIBE gold;
```

```sql
SELECT nivel, cargo, linguagens FROM gold LIMIT 5;
```

Depois execute, um por vez, os quatro arquivos de `database/athena/` e anote seus resultados. Confira se o workgroup e a identidade utilizada podem acessar os dados e o destino S3 dos resultados. Se o workgroup impuser outro destino de resultados, sua configuração prevalecerá sobre a enviada pelo backend.

As consultas esperam `nivel`, `cargo` e `linguagens` como strings. As duas últimas usam pares separados por `|`, como `sql=1|python=0`. O roteiro [TESTE_ATHENA_INSOMNIA.md](TESTE_ATHENA_INSOMNIA.md) detalha a validação de formatos, partições e contagens.

## 4. Iniciar o backend

Escolha a opção correspondente à execução: **4.1 para Java/Maven diretamente no Windows** ou **4.2 para Docker Desktop com containers Linux**.

### 4.1. Java/Maven no computador

Pare a execução anterior do backend com `Ctrl+C`, preservando o túnel SSH.

Em outro PowerShell, na raiz do projeto, carregue o `.env` no ambiente do processo:

```powershell
Get-Content -LiteralPath '.env' -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Z_][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
```

Esse comando ignora comentários e linhas vazias e preserva sinais `=` dentro de senhas e tokens. Ele atende ao formato simples do exemplo: valores sem aspas adicionais e sem comentários no fim da linha. Não executa o conteúdo do arquivo como comandos.

O exemplo do laboratório utiliza Docker como padrão. Para executar Java diretamente no Windows, após carregar o arquivo ajuste apenas neste terminal:

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:13306/NOCTUA'
$env:ATHENA_SQL_TOTAL = 'file:./database/athena/funcionarios-total.sql'
$env:ATHENA_SQL_TECNOLOGIA = 'file:./database/athena/funcionarios-tecnologia.sql'
$env:ATHENA_SQL_SENIORIDADE = 'file:./database/athena/funcionarios-senioridade.sql'
$env:ATHENA_SQL_AREA = 'file:./database/athena/funcionarios-area.sql'
```

Se tiver AWS CLI, confirme a validade da sessão no mesmo terminal:

```powershell
aws sts get-caller-identity
```

Esse teste confirma a identidade, mas não as permissões Athena/Glue/S3.

Inicie o backend nesse mesmo PowerShell:

```powershell
mvn spring-boot:run
```

Ou, se já tiver o JAR da versão atual compilado:

```powershell
java -jar ".\target\noctua-backend-0.0.1-SNAPSHOT.jar"
```

O Spring Boot não lê `.env` automaticamente. Ao mudar o arquivo, pare o backend, repita o carregamento das variáveis e inicie novamente. Faça isso também quando renovar as credenciais do laboratório.

### 4.2. Docker Desktop no Windows

O Dockerfile inclui o JAR e os quatro SQLs na imagem final. Use os caminhos `file:/app/config/...` do exemplo, sem montagem. Os caminhos `file:./database/athena/...` são destinados à execução Java na raiz do projeto, não ao container.

Mantenha o `.env` preenchido conforme a etapa 1 e o túnel SSH da etapa 2 aberto. Não é necessário carregar as variáveis no PowerShell: o Docker recebe o arquivo por `--env-file`.

Na raiz do projeto, confirme que os arquivos existem:

```powershell
Test-Path -LiteralPath '.env'
Test-Path -LiteralPath 'database/athena/funcionarios-total.sql'
Test-Path -LiteralPath 'database/athena/funcionarios-tecnologia.sql'
Test-Path -LiteralPath 'database/athena/funcionarios-senioridade.sql'
Test-Path -LiteralPath 'database/athena/funcionarios-area.sql'
```

Todos devem retornar `True`. Construa a imagem com os SQLs atuais (inclusive se a imagem anterior exigia montagem):

```powershell
docker build -t noctua-backend .
```

Se já houver um container chamado `noctua-backend`, pare-o antes de executar o novo comando:

```powershell
docker stop noctua-backend
```

Se ele foi criado sem `--rm`, remova o container parado para liberar o nome:

```powershell
docker rm noctua-backend
```

Inicie com os caminhos internos e a URL JDBC apropriados para Docker Desktop:

```powershell
docker run --rm --name noctua-backend `
  -p 127.0.0.1:8080:8080 `
  --env-file .env `
  noctua-backend
```

No PowerShell, o acento grave no fim de cada linha indica continuação; não coloque espaços depois dele.

- A imagem disponibiliza os quatro SQLs em `/app/config`, legíveis pelo usuário `noctua`.
- A URL JDBC, os quatro caminhos SQL, as credenciais e as demais opções vêm do `.env`, já configurado para Docker.
- `host.docker.internal:13306` aponta para a porta do túnel no computador; `127.0.0.1` dentro do container apontaria para o próprio container.
- O comando pressupõe Docker Desktop no Windows. Em Docker Engine no Linux ou em uma EC2, o acesso ao host precisa de configuração específica.

Mudanças nas variáveis exigem recriar o container com o comando completo; apenas `docker restart` não aplica novas variáveis. Mudanças nos SQLs incluídos exigem reconstruir a imagem e recriar o container.

Se os SQLs forem carregados, mas o health acusar falha JDBC, confira se o túnel está acessível pelo Docker Desktop e se o firewall do Windows permite esse tráfego. Isso é um diagnóstico separado do arquivo SQL ausente; não abra a porta 3306 da EC2 para a internet.

## 5. Testar no Insomnia

Use método GET, sem autenticação HTTP, sem corpo e com timeout de pelo menos 60 segundos. Não coloque credenciais AWS nas requisições.

| Ordem | URL | Resultado esperado |
|---|---|---|
| 1 | `http://localhost:8080/actuator/health` | HTTP 200 e `status: UP` |
| 2 | `http://localhost:8080/api/dashboard/projetos/total` | Total do MySQL; 1 se houver apenas o exemplo do guia |
| 3 | `http://localhost:8080/api/dashboard/funcionarios/total` | Total igual ao SQL Athena |
| 4 | `http://localhost:8080/api/dashboard/funcionarios/tecnologia` | Lista com `tecnologia` e `quantidade` |
| 5 | `http://localhost:8080/api/dashboard/funcionarios/senioridade` | Lista com `senioridade` e `quantidade` |
| 6 | `http://localhost:8080/api/dashboard/funcionarios/area` | Lista com `area` e `quantidade` |

O health verifica JDBC, não Athena. Compare as respostas de funcionários com os quatro SQLs, usando a mesma base sem alterações entre as consultas.

## 6. Diagnóstico rápido

| Sintoma | Verificação |
|---|---|
| Health 503 / erro JDBC | EC2 ligada, túnel aberto, senha MySQL e porta local 13306 |
| SQL ausente no startup | Java local: raiz do projeto e quatro caminhos `ATHENA_SQL_*`; Docker: imagem reconstruída e caminhos `/app/config` da etapa 4.2 |
| Credenciais inválidas ou expiradas | Renovar os três valores, recarregar `.env` e reiniciar o backend |
| `AccessDenied` | Permissões Athena, Glue, S3 e, se utilizados, KMS/Lake Formation |
| Tabela não encontrada | Região, database, catálogo e tabela `gold` |
| Erro no destino dos resultados | Bucket configurado, permissões e configurações do workgroup |
| Contagens inesperadas | Formato dos pares, duplicidade entre partições e filtros de senioridade |

Em HTTP 500, localize o `errorId` nos logs do backend. Se houver um query ID, consulte a execução no histórico Athena.

**Conclusão:** MySQL e Athena estão integrados quando os endpoints de ambas as fontes retornam HTTP 200 e os números correspondem às consultas executadas diretamente nos bancos.
