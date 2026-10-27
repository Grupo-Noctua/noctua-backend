-- linguagens e STRING em gold, no formato do CSV: sql=1|r=0|python=1|...
-- Pressupoe uma linha por funcionario e um par por linguagem nessa linha.
-- Para indicadores binarios, contar valores 1 equivale a somar os valores 0/1.
-- Retorna linhas (tecnologia, quantidade), conforme o contrato da API.
SELECT
    tecnologia,
    COUNT_IF(utiliza = 1) AS quantidade
FROM noctua_analytics.silver
CROSS JOIN UNNEST(
    ARRAY[
        'sql', 'r', 'python', 'c/c++/c#', 'net',
        'java', 'julia', 'sas/stata', 'visual_basic/vba',
        'scala', 'matlab', 'php', 'javascript'
    ],
    ARRAY[
        sql, r, python, c_c_c, net,
        java, julia, sas_stata, visual_basic_vba,
        scala, matlab, php, javascript
    ]
) AS linguagens(tecnologia, utiliza)
GROUP BY tecnologia
ORDER BY quantidade DESC, tecnologia;
