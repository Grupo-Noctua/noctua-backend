-- =====================================================================
--  Noctua - Esquema relacional
--  PostgreSQL 16 / JDBC puro
--  Convencao: colunas em snake_case; o mapeamento para o JSON do
--  frontend (camelCase) acontece nos DTOs Kotlin.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Autenticacao
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id          BIGSERIAL    PRIMARY KEY,
    nome        VARCHAR(160) NOT NULL,
    email       VARCHAR(180) NOT NULL UNIQUE,
    senha_hash  VARCHAR(100) NOT NULL,
    papel       VARCHAR(40)  NOT NULL DEFAULT 'DIRETOR_PROJETOS',
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Dimensionais
-- ---------------------------------------------------------------------
CREATE TABLE area (
    id    SMALLSERIAL  PRIMARY KEY,
    nome  VARCHAR(80)  NOT NULL UNIQUE
);

-- `ordem` fixa a hierarquia exibida no grafico de senioridade.
-- O grafico sempre devolve os 6 niveis, mesmo com contagem zero.
CREATE TABLE senioridade (
    id     SMALLSERIAL  PRIMARY KEY,
    nome   VARCHAR(80)  NOT NULL UNIQUE,
    ordem  SMALLINT     NOT NULL
);

CREATE TABLE tecnologia (
    id            SMALLSERIAL  PRIMARY KEY,
    nome          VARCHAR(80)  NOT NULL UNIQUE,
    categoria     VARCHAR(80)  NOT NULL,
    subcategoria  VARCHAR(80)  NOT NULL
);

-- ---------------------------------------------------------------------
-- Funcionarios
-- ---------------------------------------------------------------------
CREATE TABLE funcionario (
    id               VARCHAR(16)  PRIMARY KEY,
    nome             VARCHAR(160) NOT NULL,
    cargo            VARCHAR(120) NOT NULL,
    area_id          SMALLINT     NOT NULL REFERENCES area (id),
    senioridade_id   SMALLINT     REFERENCES senioridade (id),
    disponibilidade   SMALLINT     NOT NULL DEFAULT 100,
    data_ingresso    DATE         NOT NULL,
    localizacao      VARCHAR(60)  NOT NULL,
    CONSTRAINT funcionario_disponibilidade_ck CHECK (disponibilidade BETWEEN 0 AND 100)
);

-- relacionamento MANY-TO-MANY.
-- Necessario porque o grafico "Funcionarios x Tecnologia" soma 81 registros
-- para 80 funcionarios: um funcionario pode atuar em mais de uma tecnologia.
CREATE TABLE funcionario_tecnologia (
    funcionario_id  VARCHAR(16) NOT NULL REFERENCES funcionario (id) ON DELETE CASCADE,
    tecnologia_id   SMALLINT    NOT NULL REFERENCES tecnologia (id),
    nivel_uso       SMALLINT    NOT NULL DEFAULT 1,
    PRIMARY KEY (funcionario_id, tecnologia_id),
    CONSTRAINT funcionario_tecnologia_uso_ck CHECK (nivel_uso BETWEEN 0 AND 3)
);

-- ---------------------------------------------------------------------
-- Projetos (documentos provenientes do bucket S3)
-- ---------------------------------------------------------------------
CREATE TABLE projeto (
    id                VARCHAR(16)  PRIMARY KEY,
    nome              VARCHAR(160) NOT NULL,
    cliente           VARCHAR(160) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    progresso         SMALLINT     NOT NULL,
    orcamento         NUMERIC(14, 2) NOT NULL,
    gasto             NUMERIC(14, 2) NOT NULL,
    deadline          DATE         NOT NULL,
    fase              VARCHAR(80)  NOT NULL,
    -- Metadados do objeto no bucket S3 de origem
    s3_bucket         VARCHAR(160) NOT NULL,
    s3_object_key     VARCHAR(400) NOT NULL,
    s3_last_modified  TIMESTAMPTZ,
    CONSTRAINT projeto_status_ck CHECK (status IN ('Em dia', 'Em risco', 'Atrasado', 'Concluído')),
    CONSTRAINT projeto_progresso_ck CHECK (progresso BETWEEN 0 AND 100)
);

-- relacionamento MANY-TO-MANY: um projeto tem varios funcionarios e um
-- funcionario pode participar de varios projetos.
CREATE TABLE projeto_funcionario (
    projeto_id      VARCHAR(16) NOT NULL REFERENCES projeto (id)     ON DELETE CASCADE,
    funcionario_id  VARCHAR(16) NOT NULL REFERENCES funcionario (id) ON DELETE CASCADE,
    alocado_em      DATE,
    PRIMARY KEY (projeto_id, funcionario_id)
);

-- `nivel_uso` (0..3) e o peso de adocao da tecnologia no projeto.
-- O grafico "Projetos x Tecnologia" devolve SUM(nivel_uso) por tecnologia.
-- Apenas linhas com nivel_uso > 0 sao materializadas.
CREATE TABLE projeto_tecnologia (
    projeto_id     VARCHAR(16) NOT NULL REFERENCES projeto (id)    ON DELETE CASCADE,
    tecnologia_id  SMALLINT    NOT NULL REFERENCES tecnologia (id),
    nivel_uso      SMALLINT    NOT NULL DEFAULT 1,
    PRIMARY KEY (projeto_id, tecnologia_id),
    CONSTRAINT projeto_tecnologia_uso_ck CHECK (nivel_uso BETWEEN 1 AND 3)
);

-- ---------------------------------------------------------------------
-- Indices de apoio aos agregados do dashboard
-- ---------------------------------------------------------------------
CREATE INDEX idx_func_tecn_tec   ON funcionario_tecnologia (tecnologia_id);
CREATE INDEX idx_func_area        ON funcionario (area_id);
CREATE INDEX idx_func_senioridade ON funcionario (senioridade_id);
CREATE INDEX idx_proj_tecn_tec    ON projeto_tecnologia (tecnologia_id);
CREATE INDEX idx_proj_func_func   ON projeto_funcionario (funcionario_id);
