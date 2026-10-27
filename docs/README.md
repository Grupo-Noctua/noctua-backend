# Documentação do backend

## Executar e configurar

- [Execução local e referência das variáveis](EXECUCAO_LOCAL.md)
- [Exemplos de configuração por ambiente](../examples/env/README.md)
- [MySQL na EC2: preparação e teste manual](GUIA_MYSQL_EC2.md)
- [Teste integrado: MySQL na EC2 + Athena + backend local](GUIA_TESTE_MYSQL_ATHENA.md)
- [Teste Athena com AWS e Insomnia](TESTE_ATHENA_INSOMNIA.md)
- [Requisições HTTP](../requests/dashboard.http)

## Contratos e decisões

- [Contrato REST e integração com o frontend](CONTRATO_API.md)
- [Especificação técnica v3](Especificacao_Tecnica_API_Dashboard_Noctua_v3.md)
- [Plano de implementação da API](PLANO_IMPLEMENTACAO.md)
- [Plano de testes e resultados registrados](PLANO_TESTES.md)

A especificação registra requisitos e trechos preliminares. Para o comportamento implementado, consulte o contrato e as decisões do plano de implementação. Resultados de testes documentados são registros anteriores, não evidência de uma nova execução.

## Dados e infraestrutura

- [DDL MySQL](../database/script-noctua.sql)
- [Consultas Athena e regras dos indicadores](../database/athena/README.md)
- [Plano de automação da implantação](PLANO_AUTOMACAO.md)

O backend mantém código, SQLs e documentação. Os templates CloudFormation e o workflow n8n ficam no repositório `noctua-infraestructure`. Os guias de teste manual não substituem o fluxo de deploy planejado.

[Voltar ao README principal](../README.md)
