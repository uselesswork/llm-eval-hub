CREATE TABLE job_report_imports (
    run_id CHAR(36) NOT NULL PRIMARY KEY,
    source_run_id VARCHAR(128) NOT NULL,
    report_sha256 CHAR(64) NOT NULL,
    sample_count INT UNSIGNED NOT NULL,
    passed_count INT UNSIGNED NOT NULL,
    UNIQUE KEY uq_job_source_run (source_run_id),
    CONSTRAINT fk_job_report_run FOREIGN KEY (run_id) REFERENCES eval_runs (id)
);

CREATE TABLE job_case_results (
    run_id CHAR(36) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    task VARCHAR(16) NOT NULL,
    passed BOOLEAN NOT NULL,
    PRIMARY KEY (run_id, case_id),
    CONSTRAINT fk_job_case_run FOREIGN KEY (run_id) REFERENCES job_report_imports (run_id),
    CONSTRAINT chk_job_task CHECK (task IN ('resume', 'job', 'match'))
);

CREATE TABLE job_case_checks (
    run_id CHAR(36) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    check_name VARCHAR(128) NOT NULL,
    passed BOOLEAN NOT NULL,
    PRIMARY KEY (run_id, case_id, check_name),
    CONSTRAINT fk_job_check_case FOREIGN KEY (run_id, case_id)
        REFERENCES job_case_results (run_id, case_id)
);
