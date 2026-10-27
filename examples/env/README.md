# Configuração por ambiente

Os arquivos `*.env.example` são modelos versionados, sem credenciais reais. Nenhum deles é carregado automaticamente pelo Spring Boot ou gera recursos AWS.

| Modelo | Execução | Banco | Credenciais AWS |
|---|---|---|---|
| [Básico](../../.env.example) | Docker Desktop | MySQL no host, porta 3306 | Athena desabilitado; não exige credenciais |
| [Laboratório](docker-lab.env.example) | Docker Desktop | EC2 por túnel no host, porta 13306 | Sessão temporária do estudante, preenchida no `.env` local |
| [EC2](ec2.env.example) | Container na EC2, deploy planejado | Endereço privado resolvido pela automação | `LabInstanceProfile`, sem chaves no arquivo |

## Desenvolvimento

1. Escolha o modelo básico ou do laboratório e copie pelo editor para `.env` na raiz do projeto.
2. Preencha a senha e as configurações necessárias. Se já existir `.env`, compare os valores antes de substituí-lo.
3. Execute Docker com `--env-file .env`. Use valores sem aspas adicionais e sem comentários no fim da linha.
4. Ao mudar o arquivo, recrie o container. `docker restart` não reaplica variáveis.

O modelo do laboratório mantém o túnel `13306 → EC2:3306`. Siga o [guia integrado](../../docs/GUIA_TESTE_MYSQL_ATHENA.md). Renove as três credenciais AWS juntas quando a sessão expirar.

Ao executar Java/Maven diretamente no Windows, exporte as variáveis no processo, use `localhost` para o banco/túnel e caminhos SQL como `file:./database/athena/funcionarios-total.sql`. Veja o [guia de execução](../../docs/EXECUCAO_LOCAL.md).

## Implantação na EC2

O modelo EC2 documenta o contrato esperado. Os valores entre `<...>` precisam ser substituídos pelo deploy; não existe interpolação automática de outputs CloudFormation no `.env`.

- O deploy deve obter o endereço privado do MySQL e montar `DB_URL`.
- A senha deve vir do armazenamento de segredos escolhido para o laboratório.
- A role da EC2 fornece credenciais temporárias que o SDK obtém e renova; o container precisa alcançar o metadata service.
- A credencial do n8n local é independente da role da EC2 e continua sujeita à expiração da sessão do estudante.
- Os quatro SQLs são incluídos pelo Dockerfile em `/app/config`, com leitura pelo usuário `noctua`, sem montagem. Para alterá-los, reconstrua a imagem.

Detalhes e critérios de aceite: [plano de automação](../../docs/PLANO_AUTOMACAO.md).

## Arquivos locais

`.env`, `.env.*` e `*.env` são ignorados pelo Git, com exceções para os exemplos `*.env.example`, que permanecem versionáveis. Utilize os nomes ignorados para arquivos preenchidos. O contexto Docker exclui os arquivos de ambiente e a pasta `examples/`, incluindo os modelos. `target/` contém apenas saídas de build e também é ignorado.
