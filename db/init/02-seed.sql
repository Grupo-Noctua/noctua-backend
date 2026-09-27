-- =====================================================================
--  Noctua - Massa de dados mock
--  Alinhada a `graficos.md` e a `src/data/mock.tsx` do frontend.
--
--  Metas de agregacao verificadas por `db/verify-aggregates.sql`:
--    total_funcionarios ............. 80
--    total_projetos_analisados ......  6
--    funcionario x tecnologia ....... 81  (11,8,10,7,13,6,9,5,12)
--    funcionario x area ............. 80  (16 x 5)
--    funcionario x senioridade ...... 80  (14,14,13,13,13,13)
--    projeto x tecnologia (uso) ..... 79  (11,11,9,9,9,9,7,7,7)
--    ferramenta mais utilizada ..... Node.js  (desempate por nome ASC)
-- =====================================================================

TRUNCATE TABLE projeto_tecnologia, projeto_funcionario, funcionario_tecnologia,
               projeto, funcionario, tecnologia, senioridade, area, usuario
    RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------
-- Usuario de demonstracao
--   e-mail .... rafael.drummond@noctua.com
--   senha ..... noctua123
--   O hash abaixo e BCrypt strength 10 (prefixo $2a$) de "noctua123".
-- ---------------------------------------------------------------------
INSERT INTO usuario (nome, email, senha_hash, papel) VALUES
    ('Rafael Drummond', 'rafael.drummond@noctua.com',
     '$2a$10$EQEULGBxybx9i/rPa1GDRObVHF5y0aMvooUx0eq1f.w.qDZ.dR5Vu', 'DIRETOR_PROJETOS');

-- ---------------------------------------------------------------------
-- Areas funcionais -- 16 funcionarios cada = 80
-- ---------------------------------------------------------------------
INSERT INTO area (id, nome) VALUES
    (1, 'TI'), (2, 'Dados'), (3, 'Design'), (4, 'Infra'), (5, 'Consultoria');

-- ---------------------------------------------------------------------
-- Senioridades -- hierarquia fixa; 14,14,13,13,13,13 = 80
-- ---------------------------------------------------------------------
INSERT INTO senioridade (id, nome, ordem) VALUES
    (1, 'Estagiário',   1),
    (2, 'Júnior',       2),
    (3, 'Pleno',        3),
    (4, 'Sênior',       4),
    (5, 'Especialista', 5),
    (6, 'Tech Lead',    6);

-- ---------------------------------------------------------------------
-- Tecnologias (ids 1..9 = ordem exibida nos graficos)
-- ---------------------------------------------------------------------
INSERT INTO tecnologia (id, nome, categoria, subcategoria) VALUES
    (1, 'Node.js',  'Desenvolvimento',            'Backend'),
    (2, 'Spark',    'Dados e Análise',            'Pipeline'),
    (3, 'React',    'Desenvolvimento',            'Frontend'),
    (4, 'Postgres', 'Dados e Análise',            'DB'),
    (5, 'Docker',   'Infraestrutura e DevOps',    'Cloud'),
    (6, 'Figma',    'Design',                     'Frontend'),
    (7, 'AWS',      'Infraestrutura e DevOps',    'Cloud'),
    (8, 'PowerBI',  'Dados e Análise',            'BI'),
    (9, 'Python',   'Dados e Análise',            'Backend');

-- ---------------------------------------------------------------------
-- 80 funcionarios
--   * area por i % 5            -> 16 por area
--   * senioridade por faixas    -> 14,14,13,13,13,13
--   * os 15 primeiros mantem os nomes reais de `mock.tsx`
-- ---------------------------------------------------------------------
INSERT INTO funcionario (id, nome, cargo, area_id, senioridade_id, disponibilidade, data_ingresso, localizacao)
SELECT
    'u' || i AS id,
    COALESCE(
        (ARRAY['Ana Silva','Bruno Costa','Carla Maia','Diego Rocha','Eduardo Lima',
               'Fernanda Dias','Gustavo Nunes','Helena Pinto','Igor Santos','Juliana Moraes',
               'Karim Souza','Lucas Alves','Mariana Reis','Nicolas Prado','Olivia Castro'])[i],
        'Usuário ' || i
    ) AS nome,
    (ARRAY['Analista','Desenvolvedor','Designer','Engenheiro de Dados','Gerente de Projeto'])[(i - 1) % 5 + 1] AS cargo,
    ((i - 1) % 5) + 1 AS area_id,
    CASE
        WHEN i <= 14 THEN 1
        WHEN i <= 28 THEN 2
        WHEN i <= 41 THEN 3
        WHEN i <= 54 THEN 4
        WHEN i <= 67 THEN 5
        ELSE 6
    END AS senioridade_id,
    (ARRAY[90,70,50,100,30,80,60,40,20,75,55,85,65,45,95,88,72,66,59,81,
           47,93,37,52,68,74,82,49,58,61,79,33,41,71,64,86,56,39,70,77])[(i - 1) % 40 + 1] AS disponibilidade,
    make_date(2021 + (i % 3), 1 + (i % 9), 1) AS data_ingresso,
    (ARRAY['SP','RJ','BH','POA','Recife'])[(i - 1) % 5 + 1] AS localizacao
FROM generate_series(1, 80) AS i;

-- ---------------------------------------------------------------------
-- Funcionario x Tecnologia  -> 81 vinculos para 80 funcionarios
--
-- Distribuicao-alvo (contagem de vinculos por tecnologia):
--   Node.js 11 | Spark 8 | React 10 | Postgres 7 | Docker 13
--   Figma 6   | AWS 9   | PowerBI 5 | Python 12                 = 81
--
-- Estrategia: cada funcionario recebe exatamente 1 tecnologia primaria
-- (80 vinculos) e `u2` recebe uma segunda (Spark + Node.js).
-- As 80 primarias sao ordenadas por `g` (posicao dentro de cada grupo) e
-- depois por tecnologia, o que produz um rodizio entre as 9 tecnologias.
-- ---------------------------------------------------------------------
WITH alvos (tecnologia_id, total) AS (
    VALUES (1::smallint, 10), (2, 8), (3, 10), (4, 7), (5, 13),
           (6, 6),  (7, 9),  (8, 5),  (9, 12)
),
vinculos AS (
    SELECT
        a.tecnologia_id,
        row_number() OVER (ORDER BY g, a.tecnologia_id) AS posicao
    FROM alvos a
    CROSS JOIN LATERAL generate_series(1, a.total) AS g
)
INSERT INTO funcionario_tecnologia (funcionario_id, tecnologia_id, nivel_uso)
SELECT
    'u' || v.posicao,
    v.tecnologia_id,
    1 + ((v.posicao + v.tecnologia_id) % 3) AS nivel_uso
FROM vinculos v;

--Segundo vinculo: eleva Node.js de 10 para 11 sem alterar Docker (13).
--`u2` recebe Spark como primaria, portanto Node.js esta livre para ele.
INSERT INTO funcionario_tecnologia (funcionario_id, tecnologia_id, nivel_uso)
VALUES ('u2', 1, 2);

-- ---------------------------------------------------------------------
-- 6 projetos
-- ---------------------------------------------------------------------
INSERT INTO projeto (id, nome, cliente, status, progresso, orcamento, gasto,
                     deadline, fase, s3_bucket, s3_object_key, s3_last_modified) VALUES
    ('p1', 'Portal Cliente',    'Empresa A', 'Em dia',     78,  800000.00, 400000.00, DATE '2026-11-30', 'Desenvolvimento',
         'noctua-projetos-documentos', '2026/portal-cliente/documentacao.pdf',      TIMESTAMPTZ '2026-01-12 09:15:00+00'),
    ('p2', 'ERP Modernização',  'Empresa B', 'Em risco',   45, 1200000.00, 900000.00, DATE '2026-09-10', 'Implementação',
         'noctua-projetos-documentos', '2026/erp-modernizacao/documentacao.pdf',    TIMESTAMPTZ '2026-01-18 14:40:00+00'),
    ('p3', 'Data Lake',         'Empresa C', 'Atrasado',   32,  600000.00, 500000.00, DATE '2026-06-01', 'Infraestrutura',
         'noctua-projetos-documentos', '2026/data-lake/documentacao.pdf',           TIMESTAMPTZ '2025-11-30 11:05:00+00'),
    ('p4', 'App Mobile',        'Empresa D', 'Em dia',     90,  400000.00, 300000.00, DATE '2026-12-15', 'Entrega',
         'noctua-projetos-documentos', '2026/app-mobile/documentacao.pdf',           TIMESTAMPTZ '2026-02-02 08:20:00+00'),
    ('p5', 'BI Dashboard',      'Empresa E', 'Concluído', 100,  300000.00, 300000.00, DATE '2026-03-01', 'Operação',
         'noctua-projetos-documentos', '2026/bi-dashboard/documentacao.pdf',        TIMESTAMPTZ '2025-10-05 16:30:00+00'),
    ('p6', 'Segurança Cloud',   'Empresa F', 'Em dia',     55,  700000.00, 350000.00, DATE '2026-10-20', 'Planejamento',
         'noctua-projetos-documentos', '2026/seguranca-cloud/documentacao.pdf',     TIMESTAMPTZ '2026-03-11 10:00:00+00');

-- ---------------------------------------------------------------------
-- Equipes -- reproduz `mock.tsx` (u1..u15) para preservar os avatares
-- ---------------------------------------------------------------------
INSERT INTO projeto_funcionario (projeto_id, funcionario_id, alocado_em) VALUES
    ('p1', 'u1',  DATE '2026-01-12'), ('p1', 'u2',  DATE '2026-01-12'), ('p1', 'u3',  DATE '2026-01-15'),
    ('p2', 'u4',  DATE '2026-01-18'), ('p2', 'u5',  DATE '2026-01-18'),
    ('p3', 'u6',  DATE '2025-11-30'), ('p3', 'u7',  DATE '2025-12-01'), ('p3', 'u8',  DATE '2025-12-08'),
    ('p4', 'u9',  DATE '2026-02-02'), ('p4', 'u10', DATE '2026-02-02'),
    ('p5', 'u11', DATE '2025-10-05'), ('p5', 'u12', DATE '2025-10-05'),
    ('p6', 'u13', DATE '2026-03-11'), ('p6', 'u14', DATE '2026-03-11'), ('p6', 'u15', DATE '2026-03-16');

-- ---------------------------------------------------------------------
-- Projeto x Tecnologia -- nivel_uso 1..3
--
-- Somatorio por tecnologia (destacado na ultima coluna):
--   Node.js 11 | Spark 11 | React 9 | Postgres 9 | Docker 9
--   Figma 9    | AWS 7    | PowerBI 7 | Python 7                    = 79
--
-- Cada linha e um par (projeto, tecnologia) com o peso de adocao.
-- ---------------------------------------------------------------------
INSERT INTO projeto_tecnologia (projeto_id, tecnologia_id, nivel_uso) VALUES
    -- Node.js  -> 3+2+2+2+1+1 = 11
    ('p1', 1, 3), ('p2', 1, 2), ('p3', 1, 2), ('p4', 1, 2), ('p5', 1, 1), ('p6', 1, 1),
    -- Spark    -> 2+2+2+2+2+1 = 11
    ('p1', 2, 2), ('p2', 2, 2), ('p3', 2, 2), ('p4', 2, 2), ('p5', 2, 2), ('p6', 2, 1),
    -- React    -> 2+2+2+1+1+1 = 9
    ('p1', 3, 2), ('p2', 3, 2), ('p3', 3, 2), ('p4', 3, 1), ('p5', 3, 1), ('p6', 3, 1),
    -- Postgres -> 2+2+1+1+2+1 = 9
    ('p1', 4, 2), ('p2', 4, 2), ('p3', 4, 1), ('p4', 4, 1), ('p5', 4, 2), ('p6', 4, 1),
    -- Docker   -> 2+2+1+2+1+1 = 9
    ('p1', 5, 2), ('p2', 5, 2), ('p3', 5, 1), ('p4', 5, 2), ('p5', 5, 1), ('p6', 5, 1),
    -- Figma    -> 1+1+2+2+2+1 = 9
    ('p1', 6, 1), ('p2', 6, 1), ('p3', 6, 2), ('p4', 6, 2), ('p5', 6, 2), ('p6', 6, 1),
    -- AWS      -> 1+1+1+2+1+1 = 7
    ('p1', 7, 1), ('p2', 7, 1), ('p3', 7, 1), ('p4', 7, 2), ('p5', 7, 1), ('p6', 7, 1),
    -- PowerBI  -> 1+1+1+1+2+1 = 7
    ('p1', 8, 1), ('p2', 8, 1), ('p3', 8, 1), ('p4', 8, 1), ('p5', 8, 2), ('p6', 8, 1),
    -- Python   -> 1+1+1+2+1+1 = 7
    ('p1', 9, 1), ('p2', 9, 1), ('p3', 9, 1), ('p4', 9, 2), ('p5', 9, 1), ('p6', 9, 1);
