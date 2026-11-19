package dev.llmevalhub.review;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaseReviewService {
    private final JdbcTemplate jdbc;
    private final Clock clock = Clock.systemUTC();

    public CaseReviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public CaseReview create(String runId, String caseId, CreateReviewRequest request) {
        Integer found = jdbc.queryForObject("""
                SELECT COUNT(*) FROM eval_case_results WHERE run_id = ? AND case_id = ?
                """, Integer.class, runId, caseId);
        if (found == null || found == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "逐例结果不存在");
        }
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO eval_case_reviews (id, run_id, case_id, reviewer, verdict, note, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, runId, caseId, request.reviewer().trim(), request.verdict().name(),
                request.note(), now);
        return new CaseReview(id, runId, caseId, request.reviewer().trim(),
                request.verdict(), request.note(), now.toInstant(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<CaseReview> list(String runId, String caseId) {
        String existsSql = caseId == null
                ? "SELECT COUNT(*) FROM qa_report_imports WHERE run_id = ?"
                : "SELECT COUNT(*) FROM eval_case_results WHERE run_id = ? AND case_id = ?";
        Integer found = jdbc.queryForObject(existsSql, Integer.class,
                caseId == null ? new Object[] { runId } : new Object[] { runId, caseId });
        if (found == null || found == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导入结果或案例不存在");
        }
        return jdbc.query("""
                SELECT id, run_id, case_id, reviewer, verdict, note, recorded_at
                FROM eval_case_reviews WHERE run_id = ?
                """ + (caseId == null ? "" : " AND case_id = ?") +
                " ORDER BY recorded_at DESC, id DESC",
                (rs, rowNum) -> new CaseReview(rs.getString("id"), rs.getString("run_id"),
                        rs.getString("case_id"), rs.getString("reviewer"),
                        ReviewVerdict.valueOf(rs.getString("verdict")), rs.getString("note"),
                        rs.getObject("recorded_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                caseId == null ? new Object[] { runId } : new Object[] { runId, caseId });
    }

    @Transactional(readOnly = true)
    public CaseReview get(String id) {
        List<CaseReview> rows = jdbc.query("""
                SELECT id, run_id, case_id, reviewer, verdict, note, recorded_at
                FROM eval_case_reviews WHERE id = ?
                """, (rs, rowNum) -> new CaseReview(rs.getString("id"), rs.getString("run_id"),
                rs.getString("case_id"), rs.getString("reviewer"),
                ReviewVerdict.valueOf(rs.getString("verdict")), rs.getString("note"),
                rs.getObject("recorded_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "审核记录不存在");
        }
        return rows.get(0);
    }
}
