-- Esquema mínimo de teste derivado dos campos documentados, não é o DDL de produção.
DROP ALL OBJECTS;
CREATE TABLE Processamento (id_processamento BIGINT PRIMARY KEY);
CREATE TABLE Documento_Projeto (
    id_documento BIGINT PRIMARY KEY,
    fk_processamento BIGINT REFERENCES Processamento(id_processamento),
    nome_projeto VARCHAR(255), cliente VARCHAR(255), gestor_responsavel VARCHAR(255),
    data_inicio_prevista DATE, duracao_estimada VARCHAR(100),
    orcamento DECIMAL(15,2), descricao VARCHAR(2000), objetivo_geral VARCHAR(2000)
);
CREATE TABLE Vaga_Processada (
    id_vaga_processada BIGINT PRIMARY KEY,
    fk_processamento BIGINT REFERENCES Processamento(id_processamento)
);
CREATE TABLE Vaga_Competencia (
    id_competencia BIGINT PRIMARY KEY,
    fk_vaga_processada BIGINT REFERENCES Vaga_Processada(id_vaga_processada),
    nome_competencia VARCHAR(100)
);
CREATE TABLE Matching_Execucao (
    id_matching_execucao BIGINT PRIMARY KEY,
    fk_processamento BIGINT REFERENCES Processamento(id_processamento)
);
CREATE TABLE Matching_Execucao_Vaga (
    id_matching_execucao_vaga BIGINT PRIMARY KEY,
    fk_matching_execucao BIGINT REFERENCES Matching_Execucao(id_matching_execucao),
    titulo_vaga VARCHAR(255), senioridade_vaga VARCHAR(100)
);
CREATE TABLE Candidato_Ranqueado (
    id_candidato_ranqueado BIGINT PRIMARY KEY,
    fk_matching_execucao_vaga BIGINT REFERENCES Matching_Execucao_Vaga(id_matching_execucao_vaga),
    id_profissional_externo VARCHAR(100), nome VARCHAR(255), senioridade VARCHAR(100),
    posicao_ranking INT, score_total DECIMAL(10,2), justificativa_curta VARCHAR(2000)
);
