# Implantação com Docker Hub e AWS Academy

A implementação incremental da camada de aplicação está em
`../noctua-infraestructure`. A decisão atual usa **Docker Hub**, substituindo a
proposta anterior de ECR do `PLANO_AUTOMACAO.md`.

O [guia da infraestrutura](../../noctua-infraestructure/docs/IMPLANTACAO_APLICACAO.md)
contém os comandos para publicar imagens, criar as EC2s, preparar MySQL e implantar
backend/frontend via SSM ou pelo novo workflow n8n.

O backend utiliza o Dockerfile atual. Não recebe credenciais AWS estáticas no
container: usa o instance profile da EC2, com renovação pela cadeia padrão do SDK.
URL JDBC, senha e configurações Athena são resolvidas durante o deploy.

O schema oficial continua sendo `database/script-noctua.sql`. O bootstrap registra
seu checksum e não reaplica o DDL em releases posteriores. Alterações de schema
precisam de migrações explícitas.

O provisionamento e os aceites AWS continuam dependendo da configuração da conta,
imagens publicadas, permissões e homologação das fontes reais. A implementação local
não significa que esses recursos já foram criados.
