-- V4__add_required_ability_to_columns.sql
-- Ability obrigatória para execução na coluna.

ALTER TABLE columns
    ADD COLUMN required_ability VARCHAR(64);

UPDATE columns
SET required_ability = 'DEVELOPER'
WHERE required_ability IS NULL;

ALTER TABLE columns
    ALTER COLUMN required_ability SET NOT NULL;

ALTER TABLE columns
    ADD CONSTRAINT chk_columns_required_ability
    CHECK (required_ability IN ('PRODUCT_MANAGER', 'DEVELOPER', 'TESTER'));
