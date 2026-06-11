-- V2__jsonb_simulation_blobs.sql
-- Migrate simulation blob columns from TEXT to JSONB.
-- JSONB enables field-level queries (->>, @>, #>) and enforces JSON validity at write time.
-- Application code must send JSONB-typed parameters (e.g., via JsonbColumnType/PGobject); serializers remain unchanged.
-- See ADR-0013 for rationale and production migration guidance (lock implications).
--
-- DEPLOYMENT WARNING: this migration is NOT safe for RollingUpdate with old pods still running.
-- Old pods bind JSON as plain strings; PostgreSQL rejects plain varchar for JSONB columns in
-- prepared statements. Deploy with strategy: Recreate, OR run Flyway as a pre-deployment Job
-- before the rollout starts, so no old writers are active when the column type changes.

ALTER TABLE simulation_states
    ALTER COLUMN state_json TYPE JSONB USING state_json::jsonb;

ALTER TABLE daily_snapshots
    ALTER COLUMN snapshot_json TYPE JSONB USING snapshot_json::jsonb;
