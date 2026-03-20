-- V1__initial_schema.sql
-- Schema inicial unificado:
-- boards, steps, cards, organizations, simulations, simulation_states, daily_snapshots.

CREATE TABLE boards (
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at BIGINT       NOT NULL
);

CREATE TABLE steps (
    id               VARCHAR(36)  PRIMARY KEY,
    board_id         VARCHAR(36)  NOT NULL REFERENCES boards(id),
    name             VARCHAR(255) NOT NULL,
    position         INT          NOT NULL,
    required_ability VARCHAR(64)  NOT NULL,
    CONSTRAINT uq_steps_board_name UNIQUE (board_id, name),
    CONSTRAINT chk_steps_required_ability
        CHECK (required_ability IN ('PRODUCT_MANAGER', 'DEVELOPER', 'TESTER', 'DEPLOYER'))
);

CREATE TABLE cards (
    id          VARCHAR(36)  PRIMARY KEY,
    step_id     VARCHAR(36)  NOT NULL REFERENCES steps(id),
    title       VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    position    INT          NOT NULL,
    created_at  BIGINT       NOT NULL
);

CREATE TABLE organizations (
    id   VARCHAR(36)  PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE simulations (
    id              VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id),
    wip_limit       INT         NOT NULL,
    team_size       INT         NOT NULL,
    seed_value      BIGINT      NOT NULL
);

CREATE TABLE simulation_states (
    simulation_id VARCHAR(36) PRIMARY KEY REFERENCES simulations(id),
    state_json    TEXT        NOT NULL
);

CREATE TABLE daily_snapshots (
    simulation_id VARCHAR(36) NOT NULL REFERENCES simulations(id),
    day           INT         NOT NULL,
    snapshot_json TEXT        NOT NULL,
    PRIMARY KEY (simulation_id, day)
);

CREATE INDEX idx_steps_board_id
    ON steps(board_id);

CREATE INDEX idx_cards_step_id
    ON cards(step_id);

CREATE INDEX idx_simulations_organization_id
    ON simulations(organization_id);

CREATE INDEX idx_daily_snapshots_simulation_id
    ON daily_snapshots(simulation_id);

ALTER TABLE simulations
    ADD CONSTRAINT check_wip_limit_positive CHECK (wip_limit > 0);

ALTER TABLE simulations
    ADD CONSTRAINT check_team_size_positive CHECK (team_size > 0);
