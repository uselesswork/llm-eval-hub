package dev.llmevalhub.importer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class QaReportParser {
    static final String DEFINITION_VERSION = "qa-system.eval.metrics.v1";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int MAX_REPORT_BYTES = 2 * 1024 * 1024;
    private final ObjectMapper mapper;

    public QaReportParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public QaReportDocument parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_REPORT_BYTES) {
            throw invalid("报告必须是 1 字节到 2 MiB 的 JSON 文件");
        }
        final JsonNode root;
        try {
            root = mapper.readTree(bytes);
        } catch (JacksonException exception) {
            throw new InvalidQaReportException("报告不是合法 JSON", exception);
        }
        requireObject(root, "报告");
        JsonNode run = requireObject(field(root, "run"), "run");
        String label = text(run, "label", 128);
        Instant createdAt;
        try {
            createdAt = Instant.parse(text(run, "created_at_utc", 64));
        } catch (DateTimeParseException exception) {
            throw new InvalidQaReportException("run.created_at_utc 不是 UTC 时间", exception);
        }
        String datasetSha = sha256(run, "dataset_sha256");
        String predictionsSha = sha256(run, "predictions_sha256");
        String predictionsFilename = filename(text(run, "predictions_path", 1024));
        String pythonVersion = text(run, "python", 64);
        Map<String, String> corpusHashes = corpusHashes(run.get("corpus_sha256"));

        int sampleCount = positiveInt(root, "sample_count", 10_000);
        int answerableCount = nonnegativeInt(root, "answerable_count", sampleCount);
        int refusalCount = nonnegativeInt(root, "refusal_count", sampleCount);
        int k = positiveInt(root, "k", 1_000);
        JsonNode casesNode = field(root, "cases");
        if (!casesNode.isArray() || casesNode.size() != sampleCount ||
                answerableCount + refusalCount != sampleCount) {
            throw invalid("cases 数量或样本分母与报告不一致");
        }

        List<QaReportResultsResponse.CaseResult> cases = new ArrayList<>(sampleCount);
        Set<String> ids = new HashSet<>();
        int actualAnswerable = 0;
        int totalCalls = 0;
        for (JsonNode node : casesNode) {
            requireObject(node, "case");
            String id = text(node, "id", 128);
            if (!ids.add(id)) {
                throw invalid("cases 含重复 ID：" + id);
            }
            String category = text(node, "category", 128);
            BigDecimal latency = nonnegativeDecimal(node, "latency_ms");
            if (latency.scale() > 3 || latency.compareTo(new BigDecimal("999999999.999")) > 0) {
                throw invalid("case.latency_ms 超出数据库精度");
            }
            int modelCalls = nonnegativeInt(node, "model_calls", Integer.MAX_VALUE);
            try {
                totalCalls = Math.addExact(totalCalls, modelCalls);
            } catch (ArithmeticException exception) {
                throw invalid("model_calls_total 超出整数范围");
            }

            Boolean hit = optionalBoolean(node, "hit_at_k");
            Boolean refusal = optionalBoolean(node, "refusal_correct");
            BigDecimal rank = optionalRatio(node, "reciprocal_rank");
            BigDecimal citation = optionalRatio(node, "citation_precision");
            Boolean answerContains = optionalBoolean(node, "answer_contains_all");
            List<QaReportResultsResponse.CaseMetric> metrics = new ArrayList<>(4);
            String scope;
            if (hit != null && refusal == null && rank != null && citation != null && answerContains != null) {
                scope = "ANSWERABLE";
                actualAnswerable++;
                metrics.add(booleanMetric("qa.hit_at_k", hit));
                metrics.add(numericMetric("qa.reciprocal_rank", rank));
                metrics.add(numericMetric("qa.citation_precision", citation));
                metrics.add(booleanMetric("qa.answer_contains_all", answerContains));
            } else if (refusal != null && hit == null && rank == null && citation == null && answerContains == null) {
                scope = "REFUSAL";
                metrics.add(booleanMetric("qa.refusal_correct", refusal));
            } else {
                throw invalid("case 的可回答题与拒答题指标不一致：" + id);
            }
            cases.add(new QaReportResultsResponse.CaseResult(id, category, scope,
                    latency, modelCalls, List.copyOf(metrics)));
        }
        if (actualAnswerable != answerableCount || sampleCount - actualAnswerable != refusalCount ||
                nonnegativeInt(root, "model_calls_total", Integer.MAX_VALUE) != totalCalls) {
            throw invalid("逐例结果与汇总分母或模型调用总数不一致");
        }

        List<QaReportResultsResponse.AggregateMetric> aggregates = List.of(
                ratioMetric(root, "hit_at_k", "qa.hit_at_k", "ANSWERABLE", answerableCount),
                ratioMetric(root, "mrr_at_k", "qa.mrr_at_k", "ANSWERABLE", answerableCount),
                ratioMetric(root, "citation_precision", "qa.citation_precision", "ANSWERABLE", answerableCount),
                ratioMetric(root, "refusal_accuracy", "qa.refusal_accuracy", "REFUSAL", refusalCount),
                ratioMetric(root, "answer_contains_accuracy", "qa.answer_contains_accuracy", "ANSWERABLE", answerableCount),
                aggregate(root, "latency_p50_ms", "qa.latency_p50_ms", "P50", "ALL", sampleCount, "MILLISECONDS"),
                aggregate(root, "latency_p95_ms", "qa.latency_p95_ms", "P95", "ALL", sampleCount, "MILLISECONDS"),
                aggregate(root, "model_calls_total", "qa.model_calls_total", "SUM", "ALL", sampleCount, "CALLS"),
                aggregate(root, "model_calls_mean", "qa.model_calls_mean", "MEAN", "ALL", sampleCount, "CALLS_PER_CASE")
        );
        return new QaReportDocument(label, createdAt, pythonVersion, datasetSha,
                predictionsSha, predictionsFilename, Map.copyOf(corpusHashes), k,
                sampleCount, answerableCount, refusalCount, aggregates, List.copyOf(cases));
    }

    static String filename(String path) {
        String normalized = path.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl)) {
            throw invalid("文件名为空、过长或含控制字符");
        }
        return name;
    }

    private static Map<String, String> corpusHashes(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        requireObject(node, "run.corpus_sha256");
        if (node.size() > 1_000) {
            throw invalid("run.corpus_sha256 文件数量过多");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String name = filename(entry.getKey());
            if (!name.equals(entry.getKey()) || result.put(name, sha256Value(entry.getValue())) != null) {
                throw invalid("run.corpus_sha256 文件名无效或重复");
            }
        }
        return result;
    }

    private static QaReportResultsResponse.AggregateMetric ratioMetric(
            JsonNode root, String sourceKey, String key, String scope, int denominator) {
        BigDecimal value = optionalRatio(root, sourceKey);
        if ((denominator == 0) != (value == null)) {
            throw invalid(sourceKey + " 与样本分母不一致");
        }
        return new QaReportResultsResponse.AggregateMetric(key, DEFINITION_VERSION,
                "MEAN", scope, denominator, "RATIO", value);
    }

    private static QaReportResultsResponse.AggregateMetric aggregate(
            JsonNode root, String sourceKey, String key, String aggregation,
            String scope, int denominator, String unit) {
        BigDecimal value = nonnegativeDecimal(root, sourceKey);
        return new QaReportResultsResponse.AggregateMetric(key, DEFINITION_VERSION,
                aggregation, scope, denominator, unit, normalized(value));
    }

    private static QaReportResultsResponse.CaseMetric booleanMetric(String key, boolean value) {
        return new QaReportResultsResponse.CaseMetric(key, DEFINITION_VERSION, value, null);
    }

    private static QaReportResultsResponse.CaseMetric numericMetric(String key, BigDecimal value) {
        return new QaReportResultsResponse.CaseMetric(key, DEFINITION_VERSION, null, normalized(value));
    }

    private static JsonNode field(JsonNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null || node.isNull()) {
            throw invalid("缺少必填字段：" + name);
        }
        return node;
    }

    private static JsonNode requireObject(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            throw invalid(name + " 必须是 JSON 对象");
        }
        return node;
    }

    private static String text(JsonNode object, String name, int maxLength) {
        JsonNode node = field(object, name);
        if (!node.isString() || node.stringValue().isBlank() || node.stringValue().length() > maxLength) {
            throw invalid(name + " 必须是非空且长度不超过 " + maxLength + " 的字符串");
        }
        return node.stringValue();
    }

    private static String sha256(JsonNode object, String name) {
        return sha256Value(field(object, name));
    }

    private static String sha256Value(JsonNode node) {
        if (!node.isString() || !SHA256.matcher(node.stringValue()).matches()) {
            throw invalid("SHA-256 必须是 64 位十六进制字符串");
        }
        return node.stringValue().toLowerCase(Locale.ROOT);
    }

    private static int positiveInt(JsonNode object, String name, int max) {
        int value = nonnegativeInt(object, name, max);
        if (value == 0) {
            throw invalid(name + " 必须大于 0");
        }
        return value;
    }

    private static int nonnegativeInt(JsonNode object, String name, int max) {
        JsonNode node = field(object, name);
        if (!node.isIntegralNumber()) {
            throw invalid(name + " 必须是非负整数");
        }
        try {
            int value = node.decimalValue().intValueExact();
            if (value < 0 || value > max) {
                throw invalid(name + " 超出允许范围");
            }
            return value;
        } catch (ArithmeticException exception) {
            throw invalid(name + " 超出整数范围");
        }
    }

    private static BigDecimal nonnegativeDecimal(JsonNode object, String name) {
        JsonNode node = field(object, name);
        if (!node.isNumber()) {
            throw invalid(name + " 必须是非负数");
        }
        BigDecimal value = node.decimalValue();
        if (value.signum() < 0 || value.compareTo(new BigDecimal("999999999999.999999999999")) > 0) {
            throw invalid(name + " 超出允许范围");
        }
        return value;
    }

    private static BigDecimal optionalRatio(JsonNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null) {
            throw invalid("缺少字段：" + name);
        }
        if (node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw invalid(name + " 必须是 0 到 1 的数值或 null");
        }
        BigDecimal value = node.decimalValue();
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw invalid(name + " 必须在 0 到 1 之间");
        }
        return normalized(value);
    }

    private static Boolean optionalBoolean(JsonNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null) {
            throw invalid("缺少字段：" + name);
        }
        if (node.isNull()) {
            return null;
        }
        if (!node.isBoolean()) {
            throw invalid(name + " 必须是布尔值或 null");
        }
        return node.booleanValue();
    }

    private static BigDecimal normalized(BigDecimal value) {
        return value.setScale(12, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static InvalidQaReportException invalid(String message) {
        return new InvalidQaReportException(message);
    }
}
