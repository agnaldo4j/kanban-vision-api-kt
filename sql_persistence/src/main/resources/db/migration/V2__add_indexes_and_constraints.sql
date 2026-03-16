-- V2__add_indexes_and_constraints.sql
-- 4 índices FK (evitam full scan em joins) + 2 CHECK constraints
-- (wip_limit e team_size devem ser > 0, espelhando invariantes do domínio).

CREATE INDEX idx_columns_board_id
    ON columns(board_id);

CREATE INDEX idx_cards_column_id
    ON cards(column_id);

CREATE INDEX idx_scenarios_tenant_id
    ON scenarios(tenant_id);

CREATE INDEX idx_daily_snapshots_scenario_id
    ON daily_snapshots(scenario_id);

ALTER TABLE scenarios
    ADD CONSTRAINT check_wip_limit_positive CHECK (wip_limit > 0);

ALTER TABLE scenarios
    ADD CONSTRAINT check_team_size_positive CHECK (team_size > 0);
