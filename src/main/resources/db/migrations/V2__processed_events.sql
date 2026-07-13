-- Idempotency ledger (IRD-004 amended, ADR-004). The consumer inserts an event's `eventId`
-- here before creating its notification; a duplicate key means the event was already processed
-- and is skipped. Keyed on the schema-v2 `eventId` stamped by task-service.
CREATE TABLE processed_events (
    event_id     CHAR(36)  PRIMARY KEY,
    processed_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
