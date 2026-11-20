package dev.llmevalhub.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsageService {
    private final JdbcTemplate jdbc;
    private final Clock clock = Clock.systemUTC();

    public UsageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ModelPrice createPrice(CreatePriceRequest request) {
        BigDecimal maxPrice = new BigDecimal("1000000000000");
        if (request.inputPerMillion().compareTo(maxPrice) >= 0
                || request.outputPerMillion().compareTo(maxPrice) >= 0
                || request.inputPerMillion().scale() > 8 || request.outputPerMillion().scale() > 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "价格超过数据库精度");
        }
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        try {
            jdbc.update("""
                    INSERT INTO model_price_versions
                    (id, model_id, version, currency, input_per_million, output_per_million, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id, request.modelId(), request.version(), request.currency(),
                    request.inputPerMillion(), request.outputPerMillion(), now);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型价格版本已存在", e);
        }
        return new ModelPrice(id, request.modelId(), request.version(), request.currency(),
                request.inputPerMillion(), request.outputPerMillion(), now.toInstant(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<ModelPrice> listPrices() {
        return jdbc.query("""
                SELECT id, model_id, version, currency, input_per_million, output_per_million, created_at
                FROM model_price_versions ORDER BY created_at DESC, id DESC
                """, (rs, row) -> new ModelPrice(rs.getString("id"), rs.getString("model_id"),
                rs.getString("version"), rs.getString("currency"),
                rs.getBigDecimal("input_per_million"), rs.getBigDecimal("output_per_million"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
    }

    @Transactional
    public ModelCall createCall(String runId, CreateCallRequest request) {
        requireRun(runId);
        if ((request.inputTokens() == null) != (request.outputTokens() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "输入和输出 token 必须同时提供");
        }
        String version = request.priceVersion();
        if (version != null && (version.isBlank() || version.length() > 64)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "价格版本无效");
        }
        List<ModelPrice> prices = version == null ? List.of() : jdbc.query("""
                SELECT id, model_id, version, currency, input_per_million, output_per_million, created_at
                FROM model_price_versions WHERE model_id = ? AND version = ?
                """, (rs, row) -> new ModelPrice(rs.getString("id"), rs.getString("model_id"),
                rs.getString("version"), rs.getString("currency"),
                rs.getBigDecimal("input_per_million"), rs.getBigDecimal("output_per_million"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                request.modelId(), version);
        if (version != null && prices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型价格版本不存在");
        }
        ModelPrice price = prices.isEmpty() ? null : prices.get(0);
        BigDecimal cost = request.inputTokens() == null || price == null ? null
                : calculateCost(request.inputTokens(), request.outputTokens(), price);
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        try {
            jdbc.update("""
                    INSERT INTO model_call_usage
                    (id, run_id, request_id, model_id, latency_ms, input_tokens, output_tokens,
                     price_id, cost, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, runId, request.requestId(), request.modelId(), request.latencyMs(),
                    request.inputTokens(), request.outputTokens(), price == null ? null : price.id(), cost, now);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "运行中的请求 ID 已存在", e);
        }
        return new ModelCall(id, runId, request.requestId(), request.modelId(), request.latencyMs(),
                request.inputTokens(), request.outputTokens(), version, price == null ? null : price.currency(),
                cost, status(request.inputTokens(), price), now.toInstant(ZoneOffset.UTC));
    }

    static BigDecimal calculateCost(long input, long output, ModelPrice price) {
        return price.inputPerMillion().multiply(BigDecimal.valueOf(input))
                .add(price.outputPerMillion().multiply(BigDecimal.valueOf(output)))
                .divide(BigDecimal.valueOf(1_000_000), 12, RoundingMode.HALF_UP);
    }

    private static String status(Long tokens, ModelPrice price) {
        if (tokens == null) return "MISSING_USAGE";
        if (price == null) return "MISSING_PRICE";
        return "CALCULATED";
    }

    @Transactional(readOnly = true)
    public RunUsage getRunUsage(String runId) {
        requireRun(runId);
        List<ModelCall> calls = jdbc.query("""
                SELECT c.*, p.version AS price_version, p.currency
                FROM model_call_usage c LEFT JOIN model_price_versions p ON p.id = c.price_id
                WHERE c.run_id = ? ORDER BY c.recorded_at, c.id
                """, (rs, row) -> {
                    Long latency = rs.getObject("latency_ms", Long.class);
                    Long input = rs.getObject("input_tokens", Long.class);
                    Long output = rs.getObject("output_tokens", Long.class);
                    String currency = rs.getString("currency");
                    return new ModelCall(rs.getString("id"), rs.getString("run_id"),
                            rs.getString("request_id"), rs.getString("model_id"), latency, input, output,
                            rs.getString("price_version"), currency, rs.getBigDecimal("cost"),
                            input == null ? "MISSING_USAGE" : currency == null ? "MISSING_PRICE" : "CALCULATED",
                            rs.getObject("recorded_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
                }, runId);
        int priced = 0;
        long inputTotal = 0;
        long outputTotal = 0;
        BigDecimal total = BigDecimal.ZERO;
        String currency = null;
        boolean mixed = false;
        for (ModelCall call : calls) {
            if (call.inputTokens() != null) {
                inputTotal = Math.addExact(inputTotal, call.inputTokens());
                outputTotal = Math.addExact(outputTotal, call.outputTokens());
            }
            if (call.cost() != null) {
                priced++;
                total = total.add(call.cost());
                if (currency != null && !currency.equals(call.currency())) mixed = true;
                currency = call.currency();
            }
        }
        String summaryStatus = calls.isEmpty() ? "NO_CALLS" : mixed ? "MIXED_CURRENCY"
                : priced == calls.size() ? "CALCULATED" : "INCOMPLETE";
        return new RunUsage(runId, calls.size(), priced,
                calls.stream().anyMatch(c -> c.inputTokens() != null) ? inputTotal : null,
                calls.stream().anyMatch(c -> c.outputTokens() != null) ? outputTotal : null,
                mixed ? null : currency, "CALCULATED".equals(summaryStatus) ? total : null,
                summaryStatus, calls);
    }

    private void requireRun(String runId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM eval_runs WHERE id = ?", Integer.class, runId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评测运行不存在");
        }
    }
}
