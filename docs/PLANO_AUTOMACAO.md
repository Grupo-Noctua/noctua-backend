# Plano de automação — backend Noctua

> **Atualização de 23/10/2026:** a decisão atual é usar Docker Hub e uma nova stack
> de aplicação (MySQL/backend/frontend), com deploy via SSM. A implementação e os
> comandos estão no [guia atual](IMPLANTACAO_DOCKERHUB.md). As referências a ECR abaixo
> registram a proposta anterior; publicação e homologação AWS não foram executadas
> nesta implementação local.

**Status: implementação parcial — empacotamento local da fase 2 concluído.** O Dockerfile inclui os quatro SQLs, e a imagem foi construída e verificada localmente. Publicação no ECR, provisionamento e deploy AWS permanecem pendentes. Base: backend atual e templates/workflow disponíveis em `noctua-infraestructure`.

### Decisões desta execução — 14/10/2026

- Escopo confirmado: **preparar localmente**, avançando primeiro com empacotamento e verificações da fase 2.
- Conta/região, perfil AWS, key pair, permissões, executor CI, armazenamento da senha, entrada HTTPS e dimensionamento serão definidos depois. A fase 1 ainda não atingiu seu aceite.
- O repositório irmão `noctua-infraestructure` está disponível; alterações de rede/banco, registry e workflow aguardam essas definições.
- A cópia local do backend não possui metadados Git. Foi usada uma tag de validação local, sem atribuir um commit ou digest ECR inexistente.

## 1. Objetivo

Entregar o backend em Docker na AWS, com MySQL e Athena configurados, sem copiar o `.env` de desenvolvimento e sem editar o IP do banco manualmente. A implantação deve poder ser repetida e a versão da aplicação deve poder ser revertida.

### Estado atual

| Item | Disponível | Trabalho necessário |
|---|---|---|
| Build | Dockerfile com Java 21 e `mvn -B verify`; 24 testes aprovados no build local | Publicar imagem versionada no ECR |
| JAR | Gerado dentro do build Docker | Distribuir a imagem que contém o JAR |
| SQL Athena | Quatro arquivos incluídos na imagem em `/app/config`, legíveis por `noctua` | Homologar consultas no Athena real |
| Configuração | Variáveis no `application.yml` e exemplos por ambiente | Gerar configuração real no deploy |
| MySQL | `database/script-noctua.sql`, incluindo `orcamento` | Homologar DDL e implementar inicialização/migrações controladas |
| CloudFormation | Rede, buckets, Athena/Glue e EC2s de banco, Grafana e n8n | Adicionar backend, registry e configuração de acesso |
| n8n | Criação de stacks e carga inicial de dados | Atualização, acompanhamento de estado e deploy com validação |
| Diagnóstico | Health JDBC, logs e `errorId` | Usar as verificações como condição de sucesso do deploy |

## 2. Arquitetura proposta

```text
Código do backend → Build/testes → ECR (imagem com tag de commit/digest)
                                      ↓
n8n → CloudFormation + SSM → EC2 backend na PRIVATE-BACKEND
                                      ├── MySQL via rede privada
                                      └── Athena / Glue Catalog / S3 via IAM role

Navegador → Entrada HTTPS / proxy → Backend:8080
```

Manter os templates e o workflow no repositório de infraestrutura. O backend mantém código, SQLs, build, exemplos e contrato de configuração. O n8n coordena a implantação; o build precisa de um executor com Docker, como um runner de CI.

A rede atual já possui NAT para saída das subnets privadas. A entrada HTTP do frontend ainda precisa ser definida. Um ALB público exige subnets públicas em pelo menos duas zonas; atualmente há uma única subnet pública. Definir essa entrada na fase de rede.

## 3. Configuração e credenciais

### 3.1. Origem de cada valor

| Configuração | Origem na automação |
|---|---|
| Imagem | URI ECR com tag imutável de commit ou digest publicado |
| `DB_URL` | Endereço privado da `EC2Database`, schema `NOCTUA` e porta 3306 |
| `DB_USERNAME` | Usuário de leitura criado na preparação do banco |
| `DB_PASSWORD` | SSM Parameter Store `SecureString` ou Secrets Manager, conforme permissões do laboratório |
| `AWS_REGION` | Região da stack |
| `ATHENA_DATABASE` | Recurso `GlueDatabaseNoctua` / output `GlueDatabaseName` |
| `ATHENA_WORKGROUP` | Recurso `AthenaWorkGroup` / output `AthenaWorkGroupName` |
| `ATHENA_OUTPUT_LOCATION` | Vazio para usar o destino imposto pelo workgroup atual |
| `ATHENA_SQL_*` | Caminhos fixos dos quatro SQLs incluídos na imagem |
| Credenciais AWS da aplicação | `LabInstanceProfile`, pela cadeia padrão do SDK |

O [modelo EC2](../examples/env/ec2.env.example) documenta os campos. Ele contém placeholders, não valores executáveis. Docker `--env-file` não resolve expressões CloudFormation ou referências a segredos.

### 3.2. Endereço do banco

No mesmo template que define `EC2Database`, o CloudFormation pode resolver:

```yaml
!Sub "jdbc:mysql://${EC2Database.PrivateIp}:3306/NOCTUA"
```

Isso é uma expressão para uso dentro de uma propriedade do template, não um template completo nem uma linha para copiar literalmente ao `.env`.

Exportar o endereço privado como output para o workflow de deploy. Se backend e banco estiverem em stacks diferentes, passar o valor como parâmetro ou usar um output/export apropriado. O deploy deve consultar o endereço atual em cada execução.

A referência cria uma dependência de provisionamento, mas a EC2 existir não significa que o MySQL/schema estão prontos. Aguardar a inicialização e testar uma consulta com o usuário da aplicação antes do aceite.

Se apenas o banco for substituído e seu IP mudar, executar novamente a configuração e recriar o container. Atualizar `UserData` ou o arquivo de ambiente isoladamente não garante isso. DNS privado estável pode ser uma evolução posterior.

### 3.3. Sessão do estudante e role da EC2

- **Desenvolvimento e n8n local:** credenciais temporárias do laboratório continuam exigindo renovação. Atualizar chave, segredo e token juntos na credencial do n8n/ambiente local. A sessão de um runner de publicação também precisa ser válida.
- **Backend na EC2:** não injetar `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` ou `AWS_SESSION_TOKEN`. O SDK obtém e renova credenciais da role enquanto o ambiente estiver disponível.
- Confirmar permissões da role para Athena, metadados Glue, dados/resultados S3, leitura do segredo e pull do ECR. Se houver KMS/Lake Formation, conferir os acessos correspondentes.
- Para IMDSv2 com Docker bridge, configurar e validar acesso ao metadata service:

```yaml
IamInstanceProfile: LabInstanceProfile
MetadataOptions:
  HttpEndpoint: enabled
  HttpTokens: required
  HttpPutResponseHopLimit: 2
```

As permissões e serviços autorizados variam por laboratório. Confirmar acesso a ECR, SSM, ao armazenamento de segredos escolhido e às operações CloudFormation antes de implementar essas etapas. A role não mantém um laboratório encerrado em funcionamento.

### 3.4. Arquivo de execução

O script de deploy deverá buscar o segredo e gerar `/opt/noctua/backend.env` na EC2, com acesso restrito ao usuário administrativo que inicia o container. Não inserir o valor da senha em texto no template, no `UserData` ou na saída do workflow.

O container recebe esse arquivo via `--env-file`. Mudanças de senha, endereço ou parâmetros exigem sua recriação. O arquivo não é incluído no JAR nem na imagem e não é copiado do computador do desenvolvedor.

## 4. Etapas de implementação

### Fase 1 — Contrato do ambiente e permissões

- [ ] Confirmar conta/região, `LabInstanceProfile`, key pair e capacidades permitidas no laboratório.
- [ ] Definir executor de build/CI e como o n8n receberá a URI da imagem publicada.
- [ ] Escolher o armazenamento da senha e validar o acesso a partir da EC2.
- [ ] Definir entrada HTTPS do frontend e caminho de rede para os smoke tests.
- [ ] Definir instância/limites de memória para a API a partir do consumo observado.

**Aceite:** permissões e parâmetros necessários conhecidos; nenhum valor real de credencial precisa entrar no repositório.

### Fase 2 — Imagem versionada

- [ ] Criar o repositório ECR numa stack de registry, disponível antes da publicação da imagem.
- [x] No Dockerfile do backend, copiar os quatro SQLs de `database/athena/` para `/app/config/`, com leitura pelo usuário `noctua`.
- [x] Construir a imagem; manter `mvn -B verify` como condição de sucesso.
- [ ] Publicar no ECR com tag da versão/commit e registrar o digest. Não usar apenas `latest` como identificador do deploy.
- [x] Validar que os quatro SQLs existem na imagem sem bind mount e que nenhum `.env` foi incorporado.
- [x] Atualizar os comandos dos guias para a imagem autossuficiente em SQLs após essa alteração.

**Evidências locais:**

```powershell
docker build --no-cache-filter build --progress=plain -t noctua-backend:local-20261014 .
```

Resultado: `BUILD SUCCESS`, **24 testes, 0 falhas, 0 erros, 0 ignorados**, com execução de `mvn -B verify` sem cache do estágio `build`.

```powershell
python scripts/verify_image.py noctua-backend:local-20261014
```

Resultado: `PASS`. O [verificador](../scripts/verify_image.py) confirmou usuário não root, leitura e conteúdo idêntico dos quatro SQLs sem mounts, presença do JAR e ausência de arquivos de ambiente no filesystem final e nas entradas do JAR. `.dockerignore` exclui os arquivos de ambiente do contexto; o Dockerfile copia apenas `pom.xml`/`src` no build e JAR/SQLs na imagem final. A inspeção não audita camadas históricas.

ID da imagem local validada: `sha256:35c656d4fd99b463e6e3150d60ef1c69b204b9b453f29f79cdc39a563159b63c`. Esse ID é evidência local, não um digest publicado no ECR. Comandos e requisitos para repetir a validação estão no [guia de execução](EXECUCAO_LOCAL.md#validar-o-empacotamento-para-automação).

**Aceite:** imagem identificável, com JAR e SQLs, disponível para pull pela EC2. O registry deve existir antes de uma stack tentar iniciar o backend com essa imagem.

### Fase 3 — Rede e banco

Alterações em `noctua-infraestructure/infra/network.yaml` e `infra/compute.yaml`:

- [ ] Criar Security Groups específicos: API em 8080 a partir da entrada pública/proxy; MySQL em 3306 somente a partir do backend e de outros consumidores internos efetivamente necessários.
- [ ] Usar `PRIVATE-DB` para novas instâncias de banco. Hoje `EC2Database` está em `PRIVATE-BACKEND`; avaliar substituição e preservar dados antes de mudar uma instância existente.
- [ ] Configurar MySQL para aceitar conexão pela interface privada e criar `noctua_api` com permissão de leitura.
- [ ] Transformar a aplicação do DDL em bootstrap/migração controlada, com registro de versão e tratamento de falhas. O script atual contém `CREATE DATABASE`/`CREATE TABLE` e não deve rodar indiscriminadamente a cada deploy.
- [ ] Usar a mesma origem de senha no provisionamento do usuário e na configuração do backend. Migrações usam uma identidade administrativa separada do usuário de leitura.
- [ ] Definir persistência, backup e comportamento de retenção/substituição do banco; deploy de aplicação não deve apagar/recriar seus dados.
- [ ] Exportar o endereço privado do banco e IDs necessários às etapas de deploy.

**Aceite:** bootstrap concluído e consulta JDBC real autorizada; nova execução preserva dados e não falha por objetos já existentes.

### Fase 4 — Execução e atualização do backend

- [ ] Adicionar `EC2Backend` na `PRIVATE-BACKEND`, com role, configuração IMDSv2 e saída de rede.
- [ ] Instalar/iniciar Docker, ferramentas necessárias ao pull/deploy e SSM Agent. Confirmar que a instância aparece gerenciada no SSM e alcança seus endpoints via NAT ou endpoints privados.
- [ ] Implementar script de deploy versionado no repositório de infraestrutura, invocado por SSM Run Command com permissões apropriadas.
- [ ] Obter configuração atual, buscar a senha sem imprimi-la e gerar o arquivo de execução.
- [ ] Autenticar no ECR, baixar a imagem exata e iniciar/recriar o container com `--restart unless-stopped` e porta 8080.
- [ ] Aguardar o backend com limite de tentativas/tempo e registrar versão/digest implantado e resultado dos testes.
- [ ] Preservar a identificação da versão anterior para rollback. Ao revertê-la, resolver novamente endereço do banco e segredo atual, em vez de restaurar configuração expirada.

Usar `UserData` para o bootstrap da instância, não como único mecanismo de atualização: ele normalmente roda na primeira inicialização. Nem `CREATE_COMPLETE` da stack nem um `docker run -d` bem-sucedido comprovam que a API ficou saudável. Sem sinalização explícita, CloudFormation não aguarda o sucesso do bootstrap.

**Aceite:** implantar uma segunda versão na mesma EC2, com configuração atual, sem reconstruir rede/banco. Após reiniciar a EC2, o serviço volta e passa pelo health.

### Fase 5 — Orquestração no n8n

Alterar `noctua-infraestructure/infra-n8n-noctua.json`:

- [ ] Adicionar provisionamento/atualização do registry e receber a identificação da imagem publicada pelo executor de build.
- [ ] Distinguir primeira criação de atualização: `CreateStack`/`UpdateStack` ou change sets, incluindo o caso sem mudanças.
- [ ] Substituir esperas fixas como critério de sucesso por polling de `DescribeStacks`, com timeout, tratamento de falhas/rollback e leitura dos eventos relevantes.
- [ ] Manter ordem de dependências: registry antes do push; network/storage antes de compute; banco pronto antes do aceite da API.
- [ ] Executar o deploy via SSM e acompanhar `GetCommandInvocation` até estado final, com timeout.
- [ ] Rodar smoke tests no host via SSM ou por uma entrada acessível. O n8n local não alcança o IP privado da VPC diretamente.
- [ ] Quando a carga de dados fizer parte do fluxo, aguardar jobs/crawlers e validar a tabela Gold antes de testar Athena.
- [ ] Tratar erros como falha do workflow e apresentar a etapa, versão e diagnóstico sem expor segredos.
- [ ] Manter a rotina de exclusão de recursos fora do caminho normal de atualização da aplicação.

**Aceite:** workflow pode ser repetido, reconhece falhas reais e só informa sucesso após as verificações da aplicação.

### Fase 6 — Homologação e rollback

- [ ] `GET /actuator/health`: HTTP 200 e `status: UP` (conectividade JDBC).
- [ ] `GET /api/dashboard/projetos/total`: HTTP 200 e total coerente com o MySQL (valida consulta/schema).
- [ ] `GET /api/dashboard/funcionarios/total`: HTTP 200 e total coerente com o Athena.
- [ ] Executar também os demais endpoints da [coleção HTTP](../requests/dashboard.http) e comparar resultados com as fontes.
- [ ] Confirmar integração frontend → entrada pública → API.
- [ ] Validar redeploy após troca de endereço do banco, renovação da sessão de implantação e reinício da EC2.
- [ ] Exercitar uma imagem/configuração inválida: workflow deve falhar e permitir restauração da versão anterior compatível com o schema atual.

O health não consulta Athena e não prova que todas as tabelas da aplicação existem. Se os dados Gold ainda não estiverem disponíveis, registrar a homologação Athena como pendente, sem declarar o ambiente completo.

## 5. Fluxo final esperado

```text
Garantir registry → Build/testes → Push da imagem → Registrar digest
                                                    ↓
Garantir network/storage → Criar/atualizar compute → Aguardar stacks
                                                    ↓
                               Aguardar bootstrap/banco e SSM prontos
                                                    ↓
                             Resolver configuração → Implantar imagem
                                                    ↓
                      Validar JDBC + Athena + frontend → Registrar resultado
```

Atualizações apenas de código reutilizam os recursos existentes e passam pela publicação/deploy. Alterações de infraestrutura passam também pelas atualizações de stacks. Não é necessário recompilar o JAR apenas para trocar a URL do banco ou a senha.

## 6. Próxima entrega sugerida

Concluir as definições da **fase 1** e a criação/publicação no ECR da **fase 2**. O empacotamento local já foi validado. Com as permissões e o registry definidos, implementar banco/rede e o script de deploy antes de conectá-los ao workflow n8n. Os aceites AWS, smoke tests JDBC/Athena, integração frontend e rollback continuam pendentes; a validação local não declara o ambiente completo.

[Voltar ao índice](README.md)
