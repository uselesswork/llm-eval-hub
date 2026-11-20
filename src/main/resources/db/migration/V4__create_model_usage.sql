CREATE TABLE model_price_versions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    model_id VARCHAR(128) NOT NULL,
    version VARCHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL,
    input_per_million DECIMAL(20, 8) NOT NULL,
    output_per_million DECIMAL(20, 8) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_model_price_version (model_id, version),
    CONSTRAINT chk_model_price_nonnegative CHECK (input_per_million >= 0 AND output_per_million >= 0)
);

CREATE TABLE model_call_usage (
    id CHAR(36) NOT NULL PRIMARY KEY,
    run_id CHAR(36) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    model_id VARCHAR(128) NOT NULL,
    latency_ms BIGINT UNSIGNED NULL,
    input_tokens BIGINT UNSIGNED NULL,
    output_tokens BIGINT UNSIGNED NULL,
    price_id CHAR(36) NULL,
    cost DECIMAL(28, 12) NULL,
    recorded_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_model_call_request (run_id, request_id),
    INDEX idx_model_call_run (run_id, recorded_at, id),
    CONSTRAINT fk_model_call_run FOREIGN KEY (run_id) REFERENCES eval_runs (id),
    CONSTRAINT fk_model_call_price FOREIGN KEY (price_id) REFERENCES model_price_versions (id),
    CONSTRAINT chk_model_call_tokens_pair CHECK ((input_tokens IS NULL) = (output_tokens IS NULL))
);
