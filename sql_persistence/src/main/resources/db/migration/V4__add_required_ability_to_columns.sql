-- V4__add_required_ability_to_columns.sql
-- Ability obrigatória para execução na coluna.

ALTER TABLE columns
    ADD COLUMN required_ability VARCHAR(64);

UPDATE columns
SET required_ability = CASE
                           WHEN lower(name) LIKE '%anal%' OR lower(name) LIKE '%backlog%' OR
                                lower(name) LIKE '%refin%' OR lower(name) LIKE '%product%' THEN 'PRODUCT_MANAGER'
                           WHEN lower(name) LIKE '%test%' OR lower(name) LIKE '%qa%' OR
                                lower(name) LIKE '%qualit%' OR lower(name) LIKE '%hml%' THEN 'TESTER'
                           WHEN lower(name) LIKE '%deploy%' OR lower(name) LIKE '%release%' OR
                                lower(name) LIKE '%prod%' THEN 'DEPLOYER'
                           ELSE 'DEVELOPER'
    END
WHERE required_ability IS NULL;

ALTER TABLE columns
    ALTER COLUMN required_ability SET NOT NULL;

ALTER TABLE columns
    ADD CONSTRAINT chk_columns_required_ability
    CHECK (required_ability IN ('PRODUCT_MANAGER', 'DEVELOPER', 'TESTER', 'DEPLOYER'));
