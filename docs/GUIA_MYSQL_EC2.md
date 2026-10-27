# MySQL na EC2 — instalação, teste e integração com o Noctua

Este guia permite hospedar o banco NOCTUA em uma instância EC2, sem RDS, e testar a integração com o backend local antes de hospedar a API na AWS.

## 1. Plano e arquitetura

Na primeira etapa, o backend roda no seu computador e acessa o MySQL por um túnel SSH:

```text
Seu computador
├── Insomnia → Backend Spring Boot :8080
└── Backend → 127.0.0.1:13306
                   │
                Túnel SSH
                   │
                   ▼
             EC2 Ubuntu
             MySQL :3306
             Banco NOCTUA
```

| Etapa | Resultado |
|---|---|
| Criar a EC2 | Servidor disponível para o banco |
| Instalar o MySQL | Serviço iniciando automaticamente |
| Importar o SQL | Estrutura NOCTUA criada |
| Criar usuário da API | Acesso de leitura para o backend |
| Configurar túnel e variáveis | Backend local conectado à AWS |
| Inserir dados e chamar endpoints | Integração validada |

O backend já recebe a conexão por `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`; não é necessário alterar o código para utilizar MySQL em uma EC2.

**Fontes de dados do projeto:**

- Projetos e ferramentas: MySQL, via JDBC.
- Funcionários: Amazon Athena.

Neste teste, mantenha `ATHENA_ENABLED=false`. Os endpoints de funcionários precisam ser validados separadamente, conforme [Teste completo: AWS, backend e Insomnia](TESTE_ATHENA_INSOMNIA.md).

## 2. Pré-requisitos

- Laboratório AWS ativo e permissão para criar EC2.
- PowerShell com os comandos `ssh` e `scp` disponíveis.
- JDK 21 e Maven 3.9+ para executar o backend localmente.
- Insomnia ou outro cliente HTTP.
- Projeto disponível no computador.

Substitua todos os marcadores, como `IP_PUBLICO_EC2`, `IP_PRIVADO_EC2_BANCO` e os caminhos da chave, pelos valores do seu ambiente.

## 3. Criar a instância EC2

Se estiver usando AWS Academy Learner Lab, inicie o laboratório e abra o console AWS.

Em **EC2 → Instances → Launch instances**, configure:

| Campo | Sugestão |
|---|---|
| Nome | `noctua-mysql` |
| Sistema | Ubuntu Server 24.04 LTS |
| Arquitetura | x86_64 |
| Tipo | `t3.small`, se permitido; `t3.micro` para uma base pequena |
| Chave SSH | Crie ou selecione uma chave `.pem` e guarde-a no computador |
| Disco | 20 GiB, gp3 |
| VPC | VPC padrão ou VPC do projeto |
| Sub-rede | Pública, com rota para Internet Gateway |
| IP público automático | Habilitado |

Os tipos disponíveis e o consumo de créditos dependem das regras do laboratório. Uma instância com 2 GiB de RAM oferece mais folga para o MySQL.

### Security Group

Crie um grupo chamado `noctua-db-sg` com esta regra de entrada:

| Tipo | Porta | Origem |
|---|---:|---|
| SSH | 22 | Meu IP (`seu-ip/32`) |

Não abra a porta 3306 para este teste. O acesso ao banco passará pelo SSH.

Mantenha a saída de rede necessária para instalar os pacotes do Ubuntu. Aguarde as verificações de status da EC2 e copie o IP público.

## 4. Acessar a EC2 e instalar o MySQL

No **PowerShell do seu computador**:

```powershell
ssh -i "C:\caminho\noctua.pem" ubuntu@IP_PUBLICO_EC2
```

Os comandos a seguir são executados **no terminal Ubuntu da EC2**:

```bash
sudo apt update
sudo apt install -y mysql-server
sudo systemctl enable --now mysql
sudo systemctl status mysql --no-pager
```

Confira a versão:

```bash
sudo mysql -e "SELECT VERSION();"
```

O resultado deve indicar MySQL 8.x. O backend precisa de MySQL 8 ou superior porque utiliza CTEs nas consultas.

Confira o endereço de escuta:

```bash
sudo ss -ltnp | grep 3306
```

Para o teste por túnel, o MySQL pode permanecer em `127.0.0.1:3306`.

## 5. Criar a estrutura NOCTUA

O script completo está em [`database/script-noctua.sql`](../database/script-noctua.sql). Ele cria o banco e as tabelas, incluindo `Documento_Projeto.orcamento`.

> O trecho “Preparar o banco” do README contém orientações antigas. Para uma instalação nova, utilize `database/script-noctua.sql`. Não importe `src/test/resources/schema.sql`: ele é uma fixture de testes e contém comandos de limpeza.

Em **outro PowerShell**, aberto na pasta raiz do backend, envie o arquivo:

```powershell
scp -i "C:\caminho\noctua.pem" ".\database\script-noctua.sql" ubuntu@IP_PUBLICO_EC2:/home/ubuntu/script-noctua.sql
```

No **Ubuntu da EC2**, importe:

```bash
sudo mysql < /home/ubuntu/script-noctua.sql
```

Confira as tabelas:

```bash
sudo mysql -e "USE NOCTUA; SHOW TABLES;"
```

Execute a importação uma vez em um banco novo. O script usa `CREATE DATABASE` e `CREATE TABLE` sem `IF NOT EXISTS`; não deve ser reaplicado sobre uma instalação existente sem avaliar seu estado.

### Criar o usuário do backend

No Ubuntu, abra o MySQL:

```bash
sudo mysql
```

Execute, substituindo a senha:

```sql
CREATE USER 'noctua_api'@'localhost'
IDENTIFIED BY 'SUBSTITUA_POR_UMA_SENHA_FORTE';

GRANT SELECT ON NOCTUA.* TO 'noctua_api'@'localhost';

SHOW GRANTS FOR 'noctua_api'@'localhost';

EXIT;
```

O acesso somente de leitura corresponde ao backend atual, cujo pool JDBC está configurado com `read-only: true`. Importações e inserções administrativas devem usar outra conta, como nos exemplos deste guia.

Teste o usuário por TCP:

```bash
mysql -h 127.0.0.1 -u noctua_api -p NOCTUA
```

Digite a senha quando solicitado e execute:

```sql
SELECT COUNT(*) FROM Processamento;
EXIT;
```

Em um banco novo, o resultado esperado é `0`.

## 6. Inserir dados mínimos para testar

O script cria a estrutura, mas não popula o banco.

No **Ubuntu**, abra:

```bash
sudo mysql NOCTUA
```

Execute o exemplo abaixo **uma vez na base de testes**, mantendo todos os comandos na mesma sessão MySQL:

```sql
START TRANSACTION;

INSERT INTO Processamento (
    quantidade_consultores,
    senioridades_selecionadas,
    nome_arquivo_pdf
) VALUES (
    1,
    JSON_ARRAY('Pleno'),
    'projeto-teste.pdf'
);

SET @processamento = LAST_INSERT_ID();

INSERT INTO Documento_Projeto (
    fk_processamento,
    nome_projeto,
    cliente,
    gestor_responsavel,
    data_inicio_prevista,
    duracao_estimada,
    orcamento,
    descricao,
    objetivo_geral
) VALUES (
    @processamento,
    'Projeto de teste EC2',
    'Cliente demonstracao',
    'Gestor teste',
    CURRENT_DATE(),
    '3 meses',
    50000.00,
    'Validacao do backend com MySQL na AWS',
    'Testar a integracao JDBC'
);

INSERT INTO Vaga_Processada (
    fk_processamento,
    id_vaga_externa,
    titulo_vaga,
    senioridade
) VALUES (
    @processamento,
    1001,
    'Desenvolvedor Backend',
    'Pleno'
);

SET @vaga = LAST_INSERT_ID();

INSERT INTO Vaga_Competencia (
    fk_vaga_processada,
    nome_competencia,
    categoria
) VALUES
    (@vaga, 'Java', 'core'),
    (@vaga, 'MySQL', 'core');

COMMIT;

EXIT;
```

Se ocorrer erro antes do `COMMIT`, execute `ROLLBACK;` e corrija a causa antes de repetir a transação.

Esses dados permitem validar total, listagem de projetos e indicadores de ferramentas. A lista de profissionais recomendados ficará vazia, pois o exemplo não insere candidatos. Repetir o exemplo cria outro processamento e altera as contagens esperadas.

## 7. Abrir o túnel SSH

No **PowerShell do seu computador**:

```powershell
ssh -i "C:\caminho\noctua.pem" -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -L 127.0.0.1:13306:127.0.0.1:3306 ubuntu@IP_PUBLICO_EC2
```

Mantenha esse terminal aberto. É normal o comando ficar sem imprimir mensagens.

O encaminhamento é:

```text
127.0.0.1:13306 no seu computador
                  ↓ SSH
127.0.0.1:3306 dentro da EC2
```

A porta local `13306` evita conflito com um MySQL local na porta `3306`. Use `Ctrl+C` para encerrar o túnel ao terminar os testes.

## 8. Executar o backend conectado ao banco

Em **outro PowerShell**, na pasta raiz do backend:

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:13306/NOCTUA'
$env:DB_USERNAME = 'noctua_api'
$env:DB_PASSWORD = 'SUBSTITUA_PELA_SENHA_CRIADA'
$env:DB_POOL_SIZE = '3'
$env:ATHENA_ENABLED = 'false'
$env:PORT = '8080'

mvn spring-boot:run
```

Se já tiver compilado a versão atual, pode executar o JAR no lugar do Maven:

```powershell
java -jar ".\target\noctua-backend-0.0.1-SNAPSHOT.jar"
```

Para compilar e executar os testes automatizados antes de usar o JAR:

```powershell
mvn -B verify
```

Os testes automatizados usam H2 e mocks do Athena; não substituem a validação do MySQL real descrita na próxima seção.

### Sobre variáveis e Docker

- O Spring Boot não carrega `.env` automaticamente nessa execução. Defina as variáveis no mesmo terminal que inicia o backend.
- Para a primeira validação, execute o Java diretamente no Windows: o túnel está vinculado ao `localhost` do computador.
- Dentro de um container, `127.0.0.1` aponta para o próprio container. Não reutilize essa URL sem configurar o acesso do container ao túnel.
- As credenciais AWS não são necessárias para testar o MySQL com Athena desabilitado. Arquivos de exemplo devem conter apenas marcadores, nunca credenciais reais.

## 9. Validar no Insomnia

Crie requisições GET com base em `http://localhost:8080`. Os resultados abaixo pressupõem uma base nova com o exemplo inserido uma única vez.

### Saúde da conexão

```http
GET http://localhost:8080/actuator/health
```

Resultado esperado:

```json
{
  "status": "UP"
}
```

Esse endpoint verifica a conexão JDBC. As chamadas seguintes validam também as tabelas e consultas do projeto.

### Total de projetos

```http
GET http://localhost:8080/api/dashboard/projetos/total
```

Resultado esperado:

```json
{
  "total": 1
}
```

### Listagem de projetos

```http
GET http://localhost:8080/api/dashboard/projetos
```

Deve retornar o projeto `Projeto de teste EC2`, com orçamento equivalente a `50000.00` e `profissionaisRecomendados` vazio.

### Ferramentas mais utilizadas

```http
GET http://localhost:8080/api/dashboard/ferramentas/mais-utilizada
```

Resultado esperado:

```json
[
  {
    "ferramenta": "Java",
    "quantidade": 1
  },
  {
    "ferramenta": "MySQL",
    "quantidade": 1
  }
]
```

### Projetos por tecnologia

```http
GET http://localhost:8080/api/dashboard/projetos/tecnologia
```

Resultado esperado:

```json
[
  {
    "tecnologia": "Java",
    "quantidade": 1
  },
  {
    "tecnologia": "MySQL",
    "quantidade": 1
  }
]
```

Os endpoints `/api/dashboard/funcionarios/...` dependem do Athena e retornarão erro enquanto ele estiver desabilitado. Isso não indica falha no MySQL.

**Critério de conclusão:** `/actuator/health` em `UP` e os quatro endpoints JDBC retornando os dados inseridos confirmam a integração do backend local com o MySQL da EC2.

## 10. Próxima etapa: backend em outra EC2

Quando hospedar o backend em outra EC2, use a rede privada da VPC:

```text
EC2 Backend ── rede privada da VPC ── EC2 MySQL
```

### Rede

1. Coloque as duas instâncias na mesma VPC.
2. No `noctua-db-sg`, adicione uma entrada TCP `3306` cuja origem seja o Security Group associado à EC2 do backend.
3. Não libere `3306` para `0.0.0.0/0`.
4. Confirme que regras de saída e eventuais ACLs de rede permitem a comunicação.

### MySQL

Na EC2 do banco, edite:

```bash
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf
```

Ajuste `bind-address` para o IP privado real da EC2 do banco:

```ini
bind-address = IP_PRIVADO_EC2_BANCO
```

Reinicie e confira:

```bash
sudo systemctl restart mysql
sudo ss -ltnp | grep 3306
```

Abra `sudo mysql` e crie uma conta permitida a partir do IP privado real da EC2 do backend:

```sql
CREATE USER 'noctua_api'@'IP_PRIVADO_EC2_BACKEND'
IDENTIFIED BY 'SUBSTITUA_POR_UMA_SENHA_FORTE';

GRANT SELECT ON NOCTUA.* TO
'noctua_api'@'IP_PRIVADO_EC2_BACKEND';

SHOW VARIABLES LIKE 'have_ssl';

EXIT;
```

Confirme `have_ssl = YES` para usar a conexão TLS abaixo. Se não estiver habilitado, configure o TLS no MySQL antes de prosseguir.

### Variáveis do backend

Configure estas variáveis no processo ou container do backend:

```dotenv
DB_URL=jdbc:mysql://IP_PRIVADO_EC2_BANCO:3306/NOCTUA?sslMode=REQUIRED
DB_USERNAME=noctua_api
DB_PASSWORD=SENHA_DESSA_CONTA
DB_POOL_SIZE=3
ATHENA_ENABLED=false
```

`sslMode=REQUIRED` exige transporte criptografado, mas não verifica a identidade do servidor. Para validar também o certificado e o nome do servidor, configure uma CA confiável e `VERIFY_IDENTITY`.

O backend passa a utilizar o IP privado e não depende mais do túnel. Reinicie a API e repita os testes HTTP usando o endereço pelo qual você acessa o backend.

### Manter acesso local depois da mudança

Depois de alterar o `bind-address`, o MySQL deixa de escutar no endereço de loopback usado pelo túnel inicial. Para acesso administrativo, ajuste o destino:

```powershell
ssh -i "C:\caminho\noctua.pem" -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -L 127.0.0.1:13306:IP_PRIVADO_EC2_BANCO:3306 ubuntu@IP_PUBLICO_EC2
```

Nesse túnel, a conexão ao banco se origina na própria EC2 do MySQL. Para continuar usando `noctua_api`, crie também uma conta restrita ao IP privado da EC2 do banco, com `GRANT SELECT`, seguindo o padrão anterior. A conta `noctua_api@localhost` não deve ser presumida válida para essa nova origem.

## 11. Diagnóstico de problemas

| Sintoma | O que conferir |
|---|---|
| SSH dá timeout | Laboratório ativo, EC2 ligada, IP público atual, rota da sub-rede e porta 22 liberada para seu IP |
| `Permission denied (publickey)` | Chave `.pem` correta e usuário `ubuntu` |
| Túnel informa `Address already in use` | Outro processo usa a porta local `13306`; encerre-o ou escolha outra porta e ajuste a URL JDBC |
| JDBC dá `Communications link failure` | Túnel aberto, porta `13306`, endereço de escuta e serviço MySQL ativo |
| `Access denied for user` | Senha e origem da conta MySQL: `localhost` no túnel inicial; IP privado no acesso entre EC2s |
| `Unknown database 'NOCTUA'` | Importação do script e nome do banco |
| `Table ... doesn't exist` | Importação completa e nomes com maiúsculas/minúsculas preservados |
| Saúde `UP`, mas indicadores vazios | Estrutura existe, mas faltam dados |
| Endpoints de funcionários falham | Configuração separada do Athena |
| Backend não inicia por versão do Java | Confira `java -version` e `mvn -version`; ambos devem utilizar JDK 21 |

Logs do MySQL na EC2:

```bash
sudo journalctl -u mysql -n 100 --no-pager
```

Para diagnosticar falhas JDBC, consulte também os logs do terminal em que o backend está rodando.

## 12. Persistência e backup

O MySQL instalado dessa forma guarda os dados no disco EBS da instância.

- Parar e iniciar a EC2 normalmente preserva os dados.
- Encerrar a instância pode excluir o disco, conforme a opção **Delete on termination**.
- O laboratório pode ter regras próprias de limpeza de recursos.
- O IP público pode mudar após parar e iniciar a instância. Atualize os comandos SSH e SCP.

Antes de encerrar os testes, gere um backup **no Ubuntu da EC2**:

```bash
sudo mysqldump --single-transaction --no-tablespaces \
  --databases NOCTUA > /home/ubuntu/noctua-backup.sql
```

Baixe o backup **no PowerShell do computador**:

```powershell
scp -i "C:\caminho\noctua.pem" ubuntu@IP_PUBLICO_EC2:/home/ubuntu/noctua-backup.sql ".\noctua-backup.sql"
```

Guarde o arquivo fora do controle de versão se ele contiver dados reais.

Para restaurar em uma instalação MySQL nova, envie o backup para a nova EC2 e execute nela:

```bash
sudo mysql < /home/ubuntu/noctua-backup.sql
```

O backup contém estrutura e dados do NOCTUA, mas não as contas MySQL. Recrie os usuários e permissões conforme o modo de acesso escolhido. Não restaure sobre uma base com dados que deseja preservar sem avaliar antes o conteúdo do dump.

## 13. Checklist final

- [ ] EC2 criada e acessível por SSH.
- [ ] MySQL 8 instalado e habilitado no boot.
- [ ] Banco NOCTUA e tabelas importados.
- [ ] Usuário `noctua_api` criado com acesso de leitura.
- [ ] Dados de exemplo inseridos uma vez.
- [ ] Túnel SSH aberto na porta local `13306`.
- [ ] Backend executando com as variáveis de conexão corretas.
- [ ] `/actuator/health` retorna `UP`.
- [ ] Os quatro endpoints JDBC retornam os dados esperados.
- [ ] Backup baixado antes de encerrar os testes.
