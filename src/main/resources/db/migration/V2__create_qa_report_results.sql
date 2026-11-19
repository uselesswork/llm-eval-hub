ALTER TABLE eval_runs
    ADD COLUMN source_report_sha256 CHAR(64) NULL,
    ADD UNIQUE KEY uq_eval_runs_source_report (source_project, source_report_sha256);

CREATE TABLE qa_report_imports (
    run_id CHAR(36) NOT NULL PRIMARY KEY,
    report_filename VARCHAR(255) NOT NULL,
    dataset_sha256 CHAR(64) NOT NULL,
    predictions_sha256 CHAR(64) NOT NULL,
    predictions_filename VARCHAR(255) NOT NULL,
    source_created_at DATETIME(3) NOT NULL,
    python_version VARCHAR(64) NOT NULL,
    k INT UNSIGNED NOT NULL,
    sample_count INT UNSIGNED NOT NULL,
    answerable_count INT UNSIGNED NOT NULL,
    refusal_count INT UNSIGNED NOT NULL,
    CONSTRAINT fk_qa_report_imports_run FOREIGN KEY (run_id) REFERENCES eval_runs (id)
);

CREATE TABLE qa_report_corpus_hashes (
    run_id CHAR(36) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    PRIMARY KEY (run_id, filename),
    CONSTRAINT fk_qa_corpus_hashes_import FOREIGN KEY (run_id) REFERENCES qa_report_imports (run_id)
);

CREATE TABLE eval_run_metrics (
    run_id CHAR(36) NOT NULL,
    metric_key VARCHAR(64) NOT NULL,
    definition_version VARCHAR(64) NOT NULL,
    aggregation VARCHAR(16) NOT NULL,
    sample_scope VARCHAR(24) NOT NULL,
    denominator INT UNSIGNED NOT NULL,
    unit VARCHAR(24) NOT NULL,
    metric_value DECIMAL(24, 12) NULL,
    PRIMARY KEY (run_id, metric_key),
    CONSTRAINT fk_eval_run_metrics_run FOREIGN KEY (run_id) REFERENCES eval_runs (id)
);

CREATE TABLE eval_case_results (
    run_id CHAR(36) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    category VARCHAR(128) NOT NULL,
    sample_scope VARCHAR(24) NOT NULL,
    latency_ms DECIMAL(12, 3) NOT NULL,
    model_calls INT UNSIGNED NOT NULL,
    PRIMARY KEY (run_id, case_id),
    CONSTRAINT fk_eval_case_results_run FOREIGN KEY (run_id) REFERENCES eval_runs (id)
);

CREATE TABLE eval_case_metrics (
    run_id CHAR(36) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    metric_key VARCHAR(64) NOT NULL,
    definition_version VARCHAR(64) NOT NULL,
    boolean_value BOOLEAN NULL,
    numeric_value DECIMAL(24, 12) NULL,
    PRIMARY KEY (run_id, case_id, metric_key),
    CONSTRAINT fk_eval_case_metrics_case FOREIGN KEY (run_id, case_id)
        REFERENCES eval_case_results (run_id, case_id),
    CONSTRAINT chk_eval_case_metric_one_value CHECK (
        (boolean_value IS NOT NULL AND numeric_value IS NULL)
        OR (boolean_value IS NULL AND numeric_value IS NOT NULL)
    )
);
