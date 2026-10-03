-- Modelo de dados da Etapa 1. As restrições NOT NULL e CHECK garantem no próprio
-- banco que o estoque nunca fica negativo, mesmo diante de um bug na aplicação.
CREATE TABLE produto (
    id         BIGINT PRIMARY KEY,
    nome       VARCHAR(100) NOT NULL,
    quantidade INTEGER      NOT NULL CHECK (quantidade >= 0)
);

-- Dados iniciais da Etapa 1
INSERT INTO produto (id, nome, quantidade) VALUES (1, 'Notebook', 10);
INSERT INTO produto (id, nome, quantidade) VALUES (2, 'Mouse', 50);
INSERT INTO produto (id, nome, quantidade) VALUES (3, 'Teclado', 20);
