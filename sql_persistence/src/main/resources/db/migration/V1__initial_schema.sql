-- V1__initial_schema.sql
-- Schema inicial: boards, columns, cards (Flow Design) +
-- tenants, scenarios, scenario_states, daily_snapshots (Simulation).

CREATE TABLE boards (
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at BIGINT       NOT NULL
);

CREATE TABLE columns (
    id         VARCHAR(36)  PRIMARY KEY,
    board_id   VARCHAR(36)  NOT NULL REFERENCES boards(id),
    name       VARCHAR(255) NOT NULL,
    position   INT          NOT NULL
);

CREATE TABLE cards (
    id          VARCHAR(36)  PRIMARY KEY,
    column_id   VARCHAR(36)  NOT NULL REFERENCES columns(id),
    title       VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    position    INT          NOT NULL,
    created_at  BIGINT       NOT NULL
);

CREATE TABLE tenants (
    id   VARCHAR(36)  PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE scenarios (
    id         VARCHAR(36) PRIMARY KEY,
    tenant_id  VARCHAR(36) NOT NULL REFERENCES tenants(id),
    wip_limit  INT         NOT NULL,
    team_size  INT         NOT NULL,
    seed_value BIGINT      NOT NULL
);

CREATE TABLE scenario_states (
    scenario_id VARCHAR(36) PRIMARY KEY REFERENCES scenarios(id),
    state_json  TEXT        NOT NULL
);

CREATE TABLE daily_snapshots (
    scenario_id   VARCHAR(36) NOT NULL REFERENCES scenarios(id),
    day           INT         NOT NULL,
    snapshot_json TEXT        NOT NULL,
    PRIMARY KEY (scenario_id, day)
);
