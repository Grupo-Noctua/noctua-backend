# Noctua — API do dashboard

Backend em **Kotlin 2.1, Spring Boot 3.5 e Java 21**, com oito endpoints REST. Os indicadores de projetos consultam MySQL via JDBC; os indicadores de funcionários consultam Amazon Athena.

## Estrutura

```text
src/                 Código da aplicação e testes
database/            DDL MySQL, consultas Athena e bases de referência
docs/                Contrato, especificação, guias e planos
examples/env/        Exemplos de configuração por ambiente
requests/            Requisições HTTP e coleção para Insomnia
.env.example         Configuração básica para desenvolvimento
Dockerfile           Build com testes e execução em Java 21
pom.xml              Dependências e configuração Maven
```

O `.env` é local e ignorado pelo Git. `target/` contém artefatos gerados pelo Maven e também é ignorado.

## Começar com Docker Desktop

1. Prepare um MySQL 8+ com o banco `NOCTUA`. Consulte o [guia de execução](docs/EXECUCAO_LOCAL.md) antes de aplicar o [DDL](database/script-noctua.sql).
2. Copie `.env.example` para `.env` pelo editor e preencha a conexão e a senha. O exemplo básico usa MySQL no host e Athena desabilitado.
3. Execute na raiz, em PowerShell:

```powershell
docker build -t noctua-backend .
```

Após o build concluir com sucesso:

```powershell
docker run --rm --name noctua-backend -p 127.0.0.1:8080:8080 --env-file .env noctua-backend
```

4. Verifique `http://localhost:8080/actuator/health` e `http://localhost:8080/api/dashboard/projetos/total`.

Para **MySQL na EC2 por túnel SSH + Athena**, utilize o [exemplo do laboratório](examples/env/docker-lab.env.example) e siga o [guia integrado](docs/GUIA_TESTE_MYSQL_ATHENA.md).

## Compilar e testar

Com JDK 21 e Maven 3.9+:

```sh
mvn -B verify
```

Os testes usam H2 e mocks do Athena, sem depender de credenciais AWS ou banco externo. O Dockerfile executa esse mesmo comando no build e inclui o JAR na imagem. Para executar os testes via Docker sem iniciar a aplicação, veja o [guia de execução](docs/EXECUCAO_LOCAL.md).

## Documentação

- [Índice de todos os documentos](docs/README.md)
- [Execução local e variáveis de configuração](docs/EXECUCAO_LOCAL.md)
- [Exemplos de ambiente: local, laboratório e EC2](examples/env/README.md)
- [Contrato da API](docs/CONTRATO_API.md)
- [Implantação atual: Docker Hub e AWS Academy](docs/IMPLANTACAO_DOCKERHUB.md)
- [Plano de automação anterior e evidências locais](docs/PLANO_AUTOMACAO.md)

## Estado da integração

JDBC e Athena estão implementados; a homologação das fontes reais está descrita nos [planos de testes](docs/PLANO_TESTES.md). O health verifica conectividade JDBC, mas não consulta Athena nem valida todo o schema. Com Athena desabilitado, endpoints de funcionários retornam erro, não dados simulados.

O DDL está em `database/script-noctua.sql`, inclui `orcamento` e não é aplicado pelo startup da API. A nova automação de infraestrutura possui bootstrap controlado para esse schema. Os quatro SQLs Athena estão incluídos na imagem em `/app/config`, sem montagem. O empacotamento foi validado localmente; publicação e implantação na AWS continuam pendentes no [guia de implantação](docs/IMPLANTACAO_DOCKERHUB.md).
