-- cargo e STRING em gold: analytics_engineer=1|analista_de_dados/data_analyst=0|...
-- Pressupoe uma linha por funcionario e um par por cargo nessa linha.
-- Valores 0, null textual e ausentes nao contam como associacao ao cargo.
SELECT
    TRIM(atuacao) AS area,
    COUNT(DISTINCT id) AS quantidade
FROM silver
WHERE NULLIF(TRIM(atuacao), '') IS NOT NULL
GROUP BY TRIM(atuacao)
ORDER BY quantidade DESC, area;