-- Per-call LLM telemetry (spec §3.5, decision 2). Exactly one row per real LLM call,
-- successful or failed. Columns mirror com.podorc.runtime.spi.LlmCallRecord field by
-- field; S1-08 inserts a row inside the idempotency guard's transaction.
--
-- Token and cost columns are nullable on purpose: NULL means "the CLI did not report
-- this value" (best-effort telemetry), never a guessed number. cached_input_tokens is
-- the cache-read (hit) count only; cache_creation_input_tokens is kept separate.

CREATE TABLE llm_call (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY,
    correlation_id              TEXT        NOT NULL,
    message_id                  TEXT        NOT NULL,
    task_id                     TEXT,
    agent_id                    TEXT,
    provider                    TEXT        NOT NULL,
    model                       TEXT,
    effort                      TEXT,
    input_tokens                BIGINT,
    output_tokens               BIGINT,
    cached_input_tokens         BIGINT,
    cache_creation_input_tokens BIGINT,
    reasoning_tokens            BIGINT,
    cost_usd                    NUMERIC(14, 6),
    context_blocks              JSONB       NOT NULL DEFAULT '[]'::jsonb,
    raw_envelope                TEXT,
    status                      TEXT        NOT NULL,
    error_kind                  TEXT,
    started_at                  TIMESTAMPTZ NOT NULL,
    finished_at                 TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_llm_call PRIMARY KEY (id),
    CONSTRAINT ck_llm_call_status CHECK (status IN ('OK', 'ERROR'))
);

-- Traceability: reconstruct every LLM call of a run by correlation_id (spec §3.3).
CREATE INDEX idx_llm_call_correlation_id ON llm_call (correlation_id);
CREATE INDEX idx_llm_call_message_id ON llm_call (message_id);
