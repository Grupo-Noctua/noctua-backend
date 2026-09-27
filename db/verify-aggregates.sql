-- =====================================================================
--  Verificacao dos agregados do dashboard.
--  Uso:  docker compose exec -T postgres psql -U noctua -d noctua < db/verify-aggregates.sql
--
--  Cada bloco imprime o valor obtido e o valor esperado. Qualquer
--  divergencia invalida os graficos do frontend.
-- =====================================================================

\pset border 2

\echo '=== 1. KPIs ========================================================='
SELECT * FROM (
    SELECT 'total_funcionarios' AS metrica,
           (SELECT COUNT(*) FROM funcionario)::text AS obtido,
           '80' AS esperado
    UNION ALL
    SELECT 'total_projetos_analisados',
           (SELECT COUNT(*) FROM projeto)::text,
           '6'
    UNION ALL
    SELECT 'ferramenta_mais_utilizada',
           (SELECT t.nome FROM tecnologia t
              JOIN projeto_tecnologia pt ON pt.tecnologia_id = t.id
             GROUP BY t.id, t.nome
             ORDER BY SUM(pt.nivel_uso) DESC, t.nome ASC
             LIMIT 1),
           'Node.js'
    UNION ALL
    SELECT 'total_vinculos_funcionario_tecnologia',
           (SELECT COUNT(*) FROM funcionario_tecnologia)::text,
           '81'
    UNION ALL
    SELECT 'total_usos_projeto_tecnologia',
           (SELECT SUM(nivel_uso) FROM projeto_tecnologia)::text,
           '79'
) kpi
WHERE obtido IS DISTINCT FROM esperado;   -- zero linhas = tudo correto

\echo ''
\echo '=== 2. Funcionarios x Tecnologia (soma 81) ========================='
SELECT t.nome, COUNT(*) AS quantidade
FROM funcionario_tecnologia ft
JOIN tecnologia t ON t.id = ft.tecnologia_id
GROUP BY t.id, t.nome
ORDER BY t.id;
-- esperado: Node.js 11 | Spark 8 | React 10 | Postgres 7 | Docker 13
--           Figma 6   | AWS 9   | PowerBI 5 | Python 12      = 81

\echo ''
\echo '=== 3. Funcionarios por Area (16 x 5 = 80) ========================='
SELECT a.nome AS area, COUNT(f.id) AS quantidade
FROM funcionario f
JOIN area a ON a.id = f.area_id
GROUP BY a.id, a.nome
ORDER BY quantidade DESC, a.nome ASC;

\echo ''
\echo '=== 4. Funcionarios por Senioridade (14,14,13,13,13,13 = 80) ======='
-- Hierarquia fixa e com preenchimento zero, independente da contagem.
SELECT s.nome AS senioridade, s.ordem,
       COALESCE(contagem.total, 0) AS quantidade
FROM senioridade s
LEFT JOIN (
    SELECT senioridade_id, COUNT(*) AS total
    FROM funcionario
    GROUP BY senioridade_id
) contagem ON contagem.senioridade_id = s.id
ORDER BY s.ordem;

\echo ''
\echo '=== 5. Projetos x Tecnologia (soma 79) ============================='
SELECT t.nome,
       SUM(pt.nivel_uso)                  AS total_usos,
       ROUND(100.0 * SUM(pt.nivel_uso)
             / SUM(SUM(pt.nivel_uso)) OVER (), 0) AS percentual
FROM projeto_tecnologia pt
JOIN tecnologia t ON t.id = pt.tecnologia_id
GROUP BY t.id, t.nome
ORDER BY total_usos DESC, t.nome ASC;
-- esperado: Node.js 11/14% | Spark 11/14% | React 9/11% | Postgres 9/11%
--           Docker 9/11% | Figma 9/11% | AWS 7/9% | PowerBI 7/9% | Python 7/9% = 79

\echo ''
\echo '=== 6. Equipes por projeto ========================================='
SELECT p.id, p.nome, COUNT(pf.funcionario_id) AS quantidade_equipe,
       string_agg(pf.funcionario_id, ', ' ORDER BY pf.funcionario_id) AS equipe
FROM projeto p
LEFT JOIN projeto_funcionario pf ON pf.projeto_id = p.id
GROUP BY p.id, p.nome
ORDER BY p.id;
