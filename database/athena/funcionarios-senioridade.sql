-- Mesmo filtro dos SELECTs de referencia: Junior, Pleno e Senior.
SELECT
    TRIM(nivel) AS senioridade,
    COUNT(*) AS quantidade
FROM silver
WHERE TRIM(nivel) IN ('Sênior', 'Pleno', 'Júnior')
GROUP BY TRIM(nivel)
ORDER BY quantidade DESC, senioridade;
