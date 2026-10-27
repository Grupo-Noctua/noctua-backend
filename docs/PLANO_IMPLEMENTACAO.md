# Plano de implementação — Noctua Dashboard v3

## 1. Objetivo e referência

Entregar os oito indicadores definidos na especificação usando Kotlin/Spring Boot, JDBC e Amazon Athena. As regras consolidadas ao final do documento prevalecem sobre os trechos preliminares que ainda marcavam consultas JDBC como indefinidas.

## 2. Etapas e situação

| Etapa | Atividades e entregáveis | Dependências | Situação |
|---|---|---|---|
| 1 — Contratos | Mapear endpoints, DTOs, nulos, status HTTP e divergências documentais | Especificação v3 | Implementada; decisões abaixo para homologação |
| 2 — Fundação | Maven, Kotlin, Spring Boot, Java 21, configuração externa, Docker e tratamento de erros | Etapa 1 | Implementada |
| 3 — JDBC | Total de processamentos, empates por vaga, tecnologia por projeto e listagem com recomendações | Queries consolidadas | Implementada e testada em H2; homologação real pendente |
| 4 — Agrupamento | Agrupar por processamento, manter ocorrências, ordenar ranking por vaga e tratar orçamento decimal | Etapa 3 | Implementada e testada |
| 5 — Athena | Cliente SDK, polling, paginação, timeout, cancelamento e mapeamento de aliases | Configuração AWS | Implementada e testada com mocks |
| 6 — Banco | Preparar schema com orçamento `DECIMAL(15,2) NULL` | DDL e acesso ao NOCTUA | DDL em `database/script-noctua.sql`; homologação e migrações de bases existentes pendentes |
| 7 — SQL Athena | Quatro consultas em `database/athena/`, baseadas em `gold`, com `linguagens` string separada em pares por `|` | Credenciais e validação da granularidade das partições | SQLs escritos conforme esquema informado; homologação AWS pendente; roteiro em `TESTE_ATHENA_INSOMNIA.md` |
| 8 — Qualidade | Testes de contrato, regras JDBC e falhas Athena; build executável | Etapas 2–5 | 24 testes aprovados e JAR gerado |
| 9 — Homologação | Banco real, Athena real, frontend, volumetria e aceite dos contratos | Etapas 6–7 e acessos | Pendente de ambiente |

## 3. Estrutura implementada

```text
src/main/kotlin/br/com/noctua/
  NoctuaApplication.kt
  config/AthenaConfig.kt
  controller/DashboardController.kt
  controller/ApiExceptionHandler.kt
  dto/DashboardDTOs.kt
  repository/DashboardJdbcRepository.kt
  repository/DashboardAthenaRepository.kt
  repository/AthenaQueryExecutor.kt
  repository/ProjetoRegistro.kt
  service/DashboardService.kt
src/main/resources/application.yml
src/test/kotlin/br/com/noctua/
src/test/resources/
database/script-noctua.sql
```

O Spring Boot configura `DataSource`, HikariCP e `JdbcTemplate` pelas propriedades externas, dispensando uma classe `DatabaseConfig` redundante. SQL JDBC fica no repositório; SQL Athena vem de arquivos definidos pelo operador e é executado exclusivamente pela integração Athena. O Service transforma linhas de projetos em DTOs hierárquicos.

## 4. Decisões e divergências resolvidas

1. **Projeto = Processamento:** total usa `COUNT(*) FROM Processamento`, como definido na consolidação.
2. **Ferramenta mais utilizada é uma lista:** retorna todos os empates, inclusive quando há apenas um vencedor. Base vazia retorna `[]` com 200.
3. **Unidades de contagem distintas:** ferramenta conta vagas distintas; tecnologia conta processamentos distintos. As recomendações nunca são deduplicadas.
4. **Todos os projetos:** os `INNER JOIN`s do exemplo excluem projetos sem documento/matching/candidato. A implementação usa `LEFT JOIN` para cumprir o critério de aceite de listar todos os processamentos, inclusive os incompletos. Dados documentais ausentes ficam nulos; ausência de candidatos resulta em `profissionaisRecomendados: []`.
5. **Cardinalidade documental:** o contrato representa um documento por processamento. Se o banco retornar documentos diferentes para o mesmo processamento, a API responde com erro e registra a inconsistência em vez de escolher arbitrariamente um documento ou multiplicar recomendações. Confirmar se existe restrição única ou definir política de versões antes da homologação.
6. **Tipos:** identificadores internos e contagens `Long`; orçamento e score `BigDecimal`; início previsto `LocalDate`. Campos opcionais preservam nulos.
7. **Ordenação:** projetos por processamento; recomendações por ocorrência de vaga, ranking e ID do candidato como desempate. Ferramentas por nome; tecnologias de projeto por quantidade decrescente e nome no desempate.
8. **HTTP:** consultas bem-sucedidas retornam 200; ausência de dados usa zero/listas vazias. Não se usa 204 nesta versão. Falhas internas/fontes retornam 500 conforme a especificação. Erros de protocolo preservam o status do Spring MVC.
9. **Athena baseado no esquema informado de gold:** arquivos em `database/athena/` usam `gold`, com `linguagens` do tipo string no formato do CSV (`sql=1|python=1|...`). Mantêm os aliases do contrato e pressupõem uma linha por funcionário, considerando todas as partições. Senioridade filtra Júnior, Pleno e Sênior, conforme a referência. Homologar os resultados e confirmar se as partições representam populações distintas ou versões dos mesmos registros.
10. **Infraestrutura:** MySQL 8+ é o driver/dialeto de execução. O DDL disponível em `database/script-noctua.sql` inclui orçamento; sua compatibilidade com cada ambiente real ainda deve ser homologada. H2 é apenas uma fonte de teste.

## 5. Próximos passos de integração

### Banco relacional — responsável sugerido: backend + DBA

1. Validar `database/script-noctua.sql` com a equipe e confirmar a versão do MySQL no ambiente.
2. Conferir tipos, nomes, capitalização e chaves das tabelas consultadas.
3. Confirmar cardinalidade de `Documento_Projeto.fk_processamento` e comportamento de documentos ausentes.
4. Em bases novas, aplicar o DDL revisado. Em bases existentes, preparar migração de orçamento onde necessário; verificar precisão, escala e nulabilidade. O DDL completo não é reaplicável automaticamente.
5. Conferir os indicadores com uma amostra de referência, usando os casos de teste do plano.
6. Medir os planos de execução e avaliar índices nas chaves de junção antes de alterar o esquema.

### Athena — responsável sugerido: engenharia de dados + backend

1. Mapear identificador único de funcionário, tecnologias, senioridade e área.
2. Definir contagem de funcionários, explosão de arrays e tratamento de múltiplas linhas do mesmo funcionário.
3. Definir nulos, normalização de nomes e ordenação das distribuições.
4. Construir os quatro SQLs com aliases documentados, validando os números contra amostras conhecidas.
5. Configurar database, catalog, workgroup, localização de resultados e credenciais externas.
6. Configurar arquivos SQL e ativar `ATHENA_ENABLED=true`; ausência de arquivo/configuração deve impedir startup válido.
7. Executar casos de sucesso, base vazia, erro de permissão, timeout e múltiplas páginas no ambiente de homologação.

### Frontend e entrega — responsável sugerido: backend + frontend

1. Homologar lista de empates, projetos sem documento, orçamento nulo e repetição de profissionais.
2. Integrar os oito endpoints e o tratamento de erro por `errorId`.
3. Medir tamanho da resposta de projetos e latências para definir paginação/cache em uma próxima versão.
4. Definir autenticação, autorização e política de origem do frontend, pendências já registradas na especificação.
5. Executar o build, aplicar configurações no ambiente e fazer smoke test antes do aceite.

## 6. Critérios para conclusão da homologação

- Os oito endpoints devolvem dados conferidos nas fontes reais.
- O total de projetos considera todos os processamentos e os projetos incompletos seguem a decisão acordada.
- Nenhuma ocorrência legítima de recomendação é removida.
- Orçamento nullable com precisão de duas casas decimais confirmado no banco.
- Empates e agregações distintas correspondem ao SQL consolidado.
- Configuração Athena válida, resultados completos e timeout/cancelamento observados.
- Contrato aceito pelo frontend e limites de volume conhecidos.

## 7. Sequência de publicação e reversão

1. Executar `mvn -B verify` e identificar o artefato/imagem da entrega.
2. Aplicar o DDL em base nova ou uma migração revisada em base existente, se necessário, antes de publicar a API.
3. Fornecer configurações JDBC e, quando homologadas, Athena; iniciar a aplicação.
4. Verificar health JDBC e executar as oito requisições de smoke test.
5. Em regressão, restaurar o artefato e as configurações anteriores. Manter a coluna de orçamento: removê-la pode eliminar dados já preenchidos. Como esta é a primeira implementação, sem versão anterior, a reversão consiste em interromper a nova instância.
