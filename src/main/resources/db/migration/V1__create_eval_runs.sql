CREATE TABLE eval_runs (
    id CHAR(36) NOT NULL PRIMARY KEY,
    source_project VARCHAR(64) NOT NULL,
    run_label VARCHAR(128) NOT NULL,
    dataset_version VARCHAR(128) NOT NULL,
    model_id VARCHAR(128) NULL,
    prompt_version VARCHAR(128) NULL,
    result_source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    INDEX idx_eval_runs_project_created (source_project, created_at, id)
);
