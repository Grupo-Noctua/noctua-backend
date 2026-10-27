INSERT INTO Processamento VALUES (1), (2), (3), (4);
INSERT INTO Documento_Projeto VALUES
    (101, 1, 'Data Lake', 'Empresa ABC', 'João Silva', '2026-10-15', '180 dias', 350000.00, 'Plataforma de dados', 'Modernizar'),
    (102, 2, 'Cloud', 'Empresa DEF', NULL, NULL, NULL, NULL, NULL, NULL),
    (103, 3, 'Sem candidatos', NULL, NULL, NULL, NULL, 9999999999999.99, NULL, NULL);
INSERT INTO Vaga_Processada VALUES (11, 1), (12, 1), (21, 2);
INSERT INTO Vaga_Competencia VALUES
    (1, 11, 'SQL'), (2, 11, 'SQL'), (3, 12, 'SQL'),
    (4, 11, 'Java'), (5, 21, 'Java'), (6, 21, 'AWS');
INSERT INTO Matching_Execucao VALUES (1, 1), (2, 1), (3, 3);
INSERT INTO Matching_Execucao_Vaga VALUES
    (10, 1, 'Engenheiro de Dados', 'Senior'),
    (20, 2, 'Arquiteto de Dados', 'Senior'),
    (30, 3, 'DevOps', 'Pleno');
-- Ordem de inserção diferente do ranking; PROF-001 ocorre em duas vagas/execuções.
INSERT INTO Candidato_Ranqueado VALUES
    (2, 10, 'PROF-002', 'João Santos', 'Senior', 2, 91.70, 'Boa aderência'),
    (1, 10, 'PROF-001', 'Carlos Oliveira', 'Senior', 1, 95.40, 'Alta compatibilidade'),
    (3, 20, 'PROF-001', 'Carlos Oliveira', 'Senior', 1, 93.20, 'Alta compatibilidade');
