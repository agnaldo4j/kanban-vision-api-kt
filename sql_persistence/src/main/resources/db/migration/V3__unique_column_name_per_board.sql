-- V3__unique_column_name_per_board.sql
-- Garante unicidade de nome de coluna por board ao nível do banco,
-- prevenindo race conditions que poderiam contornar a validação in-memory
-- de Board.addColumn() em requisições concorrentes.

ALTER TABLE columns
    ADD CONSTRAINT uq_columns_board_name UNIQUE (board_id, name);
