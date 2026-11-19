CREATE TABLE eval_case_reviews (
    id CHAR(36) NOT NULL PRIMARY KEY,
    run_id CHAR(36) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    reviewer VARCHAR(128) NOT NULL,
    verdict VARCHAR(24) NOT NULL,
    note VARCHAR(1000) NULL,
    recorded_at DATETIME(3) NOT NULL,
    INDEX idx_case_reviews_run_time (run_id, recorded_at, id),
    CONSTRAINT fk_case_reviews_result FOREIGN KEY (run_id, case_id)
        REFERENCES eval_case_results (run_id, case_id),
    CONSTRAINT chk_case_review_verdict CHECK (verdict IN ('PASS', 'FAIL', 'NEEDS_FOLLOWUP'))
);
