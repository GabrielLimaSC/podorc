-- Idempotency ledger (spec §3.1). The idempotency guard (S1-04) writes one row per
-- message_id in the SAME transaction that persists the message's result; a repeated
-- message_id is discarded without reprocessing.
--
-- message_id is TEXT (not uuid) to match the SPI port
-- com.podorc.runtime.spi.IdempotencyGuard, which is storage-agnostic. The PRIMARY KEY is
-- what makes a concurrent double-insert of the same id fail for the losing writer.

CREATE TABLE processed_message (
    message_id     TEXT        NOT NULL,
    correlation_id TEXT,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_message PRIMARY KEY (message_id)
);

-- Supports the periodic TTL sweep (decision 11: retain a few days, then delete).
CREATE INDEX idx_processed_message_processed_at ON processed_message (processed_at);
