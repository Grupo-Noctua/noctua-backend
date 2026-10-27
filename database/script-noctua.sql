SELECT 1;
SHOW DATABASES;
SELECT CURRENT_USER();

CREATE DATABASE NOCTUA;
USE NOCTUA;

CREATE TABLE Processamento (
    id_processamento INT AUTO_INCREMENT PRIMARY KEY,
    quantidade_consultores INT NOT NULL,
    senioridades_selecionadas JSON NULL,
    nome_arquivo_pdf VARCHAR(255) NULL,
    dt_processamento DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Processamento_Log_JSON (
    id_log INT AUTO_INCREMENT PRIMARY KEY,
    fk_processamento INT NOT NULL,
    etapa VARCHAR(50) NOT NULL,
    conteudo JSON NOT NULL,
    dt_inclusao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (fk_processamento) REFERENCES Processamento(id_processamento)
);

CREATE TABLE Documento_Projeto (
    id_documento INT AUTO_INCREMENT PRIMARY KEY,
    fk_processamento INT NOT NULL,
    nome_projeto VARCHAR(255) NULL,
    cliente VARCHAR(255) NULL,
    gestor_responsavel VARCHAR(255) NULL,
    data_inicio_prevista DATE NULL,
    duracao_estimada VARCHAR(100) NULL,
    orcamento DECIMAL(15,2) NULL,
    descricao TEXT NULL,
    objetivo_geral TEXT NULL,
    texto_documento LONGTEXT NULL,
    FOREIGN KEY (fk_processamento) REFERENCES Processamento(id_processamento)
);

CREATE TABLE Vaga_Processada (
    id_vaga_processada INT AUTO_INCREMENT PRIMARY KEY,
    fk_processamento INT NOT NULL,
    id_vaga_externa INT NOT NULL,
    titulo_vaga VARCHAR(255) NULL,
    senioridade VARCHAR(50) NOT NULL,
    FOREIGN KEY (fk_processamento) REFERENCES Processamento(id_processamento),
    UNIQUE (fk_processamento, id_vaga_externa)
);

CREATE TABLE Vaga_Competencia (
    id_vaga_competencia INT AUTO_INCREMENT PRIMARY KEY,
    fk_vaga_processada INT NOT NULL,
    nome_competencia VARCHAR(255) NOT NULL,
    categoria VARCHAR(50) NOT NULL,
    FOREIGN KEY (fk_vaga_processada) REFERENCES Vaga_Processada(id_vaga_processada)
);

CREATE TABLE Matching_Execucao (
    id_matching_execucao INT AUTO_INCREMENT PRIMARY KEY,
    fk_processamento INT NOT NULL,
    quantidade_vagas_solicitada INT NOT NULL,
    vagas_processadas INT NOT NULL,
    fonte_dados VARCHAR(100) NULL,
    FOREIGN KEY (fk_processamento) REFERENCES Processamento(id_processamento)
);

CREATE TABLE Matching_Execucao_Vaga (
    id_matching_execucao_vaga INT AUTO_INCREMENT PRIMARY KEY,
    fk_matching_execucao INT NOT NULL,
    fk_vaga_processada INT NOT NULL,
    titulo_vaga VARCHAR(255) NULL,
    senioridade_vaga VARCHAR(50) NULL,
    status VARCHAR(50) NOT NULL,
    observacao_status TEXT NULL,
    FOREIGN KEY (fk_matching_execucao) REFERENCES Matching_Execucao(id_matching_execucao),
    FOREIGN KEY (fk_vaga_processada) REFERENCES Vaga_Processada(id_vaga_processada)
);

CREATE TABLE Candidato_Ranqueado (
    id_candidato_ranqueado INT AUTO_INCREMENT PRIMARY KEY,
    fk_matching_execucao_vaga INT NOT NULL,
    id_profissional_externo VARCHAR(100) NOT NULL,
    nome VARCHAR(255) NULL,
    senioridade VARCHAR(50) NULL,
    posicao_ranking INT NOT NULL,
    score_total DECIMAL(10,2) NOT NULL,
    justificativa_curta TEXT NULL,
    FOREIGN KEY (fk_matching_execucao_vaga) REFERENCES Matching_Execucao_Vaga(id_matching_execucao_vaga)
);

CREATE TABLE Candidato_Score_Detalhado (
    id_score_detalhado INT AUTO_INCREMENT PRIMARY KEY,
    fk_candidato_ranqueado INT NOT NULL,
    score_core INT NOT NULL DEFAULT 0,
    score_correlatas INT NOT NULL DEFAULT 0,
    score_suporte INT NOT NULL DEFAULT 0,
    score_complementares INT NOT NULL DEFAULT 0,
    FOREIGN KEY (fk_candidato_ranqueado) REFERENCES Candidato_Ranqueado(id_candidato_ranqueado),
    UNIQUE (fk_candidato_ranqueado)
);

CREATE TABLE Candidato_Competencia_Match (
    id_candidato_competencia_match INT AUTO_INCREMENT PRIMARY KEY,
    fk_candidato_ranqueado INT NOT NULL,
    nome_competencia VARCHAR(255) NOT NULL,
    categoria VARCHAR(50) NOT NULL,
    FOREIGN KEY (fk_candidato_ranqueado) REFERENCES Candidato_Ranqueado(id_candidato_ranqueado)
);

CREATE TABLE Candidato_Lacuna (
    id_candidato_lacuna INT AUTO_INCREMENT PRIMARY KEY,
    fk_candidato_ranqueado INT NOT NULL,
    nome_competencia VARCHAR(255) NOT NULL,
    FOREIGN KEY (fk_candidato_ranqueado) REFERENCES Candidato_Ranqueado(id_candidato_ranqueado)
);