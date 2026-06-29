package com.zjkl.common.monitoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.common.Result;
import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.common.context.UserContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin/performance")
@RequiredArgsConstructor
@Slf4j
public class PerformanceMetricsController {

    private static final long WARNING_AVG_MS = 1_500;
    private static final long CRITICAL_AVG_MS = 5_000;
    private static final int MAX_HISTORY_SIZE = 60;
    private static final String HISTORY_REDIS_KEY = "monitoring:performance:endpoint:history";
    private static final Duration HISTORY_REDIS_TTL = Duration.ofHours(2);

    private final MeterRegistry meterRegistry;
    private final UserContext userContext;
    private final AuthProperties authProperties;
    private final EndpointMetrics endpointMetrics;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Deque<EndpointMetricsHistorySample> history = new ArrayDeque<>();

    @GetMapping("/endpoints")
    public Result<EndpointMetricsSnapshot> endpointMetrics() {
        return endpointMetrics.recordResult("admin", "performance.endpoints", () -> {
            String authError = userContext.checkAdminAccess(authProperties);
            if (authError != null) {
                return Result.unauthorized(authError);
            }
            EndpointMetricsSnapshot snapshot = buildSnapshot();
            rememberSnapshot(snapshot);
            return Result.success(snapshot);
        });
    }

    @GetMapping("/history")
    public Result<EndpointMetricsHistory> endpointMetricsHistory() {
        return endpointMetrics.recordResult("admin", "performance.history", () -> {
            String authError = userContext.checkAdminAccess(authProperties);
            if (authError != null) {
                return Result.unauthorized(authError);
            }
            return Result.success(buildHistory());
        });
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void sampleEndpointMetrics() {
        rememberSnapshot(buildSnapshot());
    }

    private EndpointMetricsSnapshot buildSnapshot() {
        Map<String, MutableEndpointMetric> metrics = new HashMap<>();

        for (Meter meter : meterRegistry.getMeters()) {
            Meter.Id id = meter.getId();
            if (!"app.endpoint.duration".equals(id.getName()) && !"app.endpoint.total".equals(id.getName())) {
                continue;
            }

            String client = tagValue(id, "client");
            String endpoint = tagValue(id, "endpoint");
            String outcome = tagValue(id, "outcome");
            if (client.isBlank() || endpoint.isBlank() || outcome.isBlank()) {
                continue;
            }

            String key = client + "|" + endpoint + "|" + outcome;
            MutableEndpointMetric metric = metrics.computeIfAbsent(key,
                    ignored -> new MutableEndpointMetric(client, endpoint, outcome));

            if (meter instanceof Timer timer) {
                metric.timerCount += timer.count();
                metric.totalDurationMs += timer.totalTime(TimeUnit.MILLISECONDS);
            } else if (meter instanceof Counter counter) {
                metric.totalCount += counter.count();
            }
        }

        List<EndpointMetricRow> rows = metrics.values().stream()
                .map(MutableEndpointMetric::toRow)
                .sorted(Comparator
                        .comparingInt((EndpointMetricRow row) -> severityRank(row.level())).reversed()
                        .thenComparing(EndpointMetricRow::total, Comparator.reverseOrder()))
                .toList();

        EndpointMetricsSummary summary = buildSummary(rows);
        return new EndpointMetricsSnapshot(Instant.now().toString(), rows.size(), summary, rows);
    }

    private EndpointMetricsSummary buildSummary(List<EndpointMetricRow> rows) {
        long totalRequests = rows.stream().mapToLong(EndpointMetricRow::total).sum();
        long slowRows = rows.stream().filter(row -> row.avgMs() >= WARNING_AVG_MS).count();
        long errorRows = rows.stream().filter(row -> isErrorOutcome(row.outcome())).count();
        long criticalRows = rows.stream().filter(row -> "critical".equals(row.level())).count();
        long warningRows = rows.stream().filter(row -> "warning".equals(row.level())).count();
        Set<String> endpoints = new HashSet<>();
        rows.forEach(row -> endpoints.add(row.client() + "|" + row.endpoint()));

        String status = criticalRows > 0 ? "critical" : warningRows > 0 ? "warning" : "healthy";
        List<String> recommendations = new ArrayList<>();
        if (criticalRows > 0) {
            recommendations.add("Prioritize critical endpoints: server errors, exceptions, or average latency above 5s.");
        }
        if (slowRows > 0) {
            recommendations.add("Review slow endpoints above 1.5s average latency for caching, async work, or database indexes.");
        }
        if (errorRows > 0) {
            recommendations.add("Inspect error outcomes such as server_error, exception, client_error, or rate_limited with logs.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("No obvious endpoint issue detected. Keep watching the trend.");
        }

        return new EndpointMetricsSummary(
                status,
                totalRequests,
                endpoints.size(),
                rows.size(),
                slowRows,
                errorRows,
                criticalRows,
                recommendations
        );
    }

    private void rememberSnapshot(EndpointMetricsSnapshot snapshot) {
        EndpointMetricsHistorySample sample = EndpointMetricsHistorySample.from(snapshot);
        rememberInMemory(sample);
        rememberInRedis(sample);
    }

    private synchronized void rememberInMemory(EndpointMetricsHistorySample sample) {
        EndpointMetricsHistorySample last = history.peekLast();
        if (last != null && last.updatedAt().equals(sample.updatedAt())) {
            return;
        }
        history.addLast(sample);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }
    }

    private void rememberInRedis(EndpointMetricsHistorySample sample) {
        try {
            stringRedisTemplate.opsForList().rightPush(HISTORY_REDIS_KEY, objectMapper.writeValueAsString(sample));
            stringRedisTemplate.opsForList().trim(HISTORY_REDIS_KEY, -MAX_HISTORY_SIZE, -1);
            stringRedisTemplate.expire(HISTORY_REDIS_KEY, HISTORY_REDIS_TTL);
        } catch (JsonProcessingException | RuntimeException e) {
            log.debug("Failed to persist endpoint metrics history to Redis; memory history remains available.", e);
        }
    }

    private EndpointMetricsHistory buildHistory() {
        List<EndpointMetricsHistorySample> samples = loadHistorySamples();
        EndpointMetricsHistorySample first = samples.isEmpty() ? null : samples.getFirst();
        EndpointMetricsHistorySample latest = samples.isEmpty() ? null : samples.getLast();
        EndpointMetricsTrend trend = EndpointMetricsTrend.from(first, latest);
        return new EndpointMetricsHistory(Instant.now().toString(), samples.size(), MAX_HISTORY_SIZE, trend, samples);
    }

    private List<EndpointMetricsHistorySample> loadHistorySamples() {
        List<EndpointMetricsHistorySample> redisSamples = loadRedisHistorySamples();
        return redisSamples.isEmpty() ? loadMemoryHistorySamples() : redisSamples;
    }

    private List<EndpointMetricsHistorySample> loadRedisHistorySamples() {
        try {
            List<String> values = stringRedisTemplate.opsForList().range(HISTORY_REDIS_KEY, 0, -1);
            if (values == null || values.isEmpty()) {
                return List.of();
            }

            List<EndpointMetricsHistorySample> samples = new ArrayList<>();
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                try {
                    samples.add(objectMapper.readValue(value, EndpointMetricsHistorySample.class));
                } catch (JsonProcessingException e) {
                    log.debug("Skipped invalid endpoint metrics history sample from Redis.", e);
                }
            }
            return samples;
        } catch (RuntimeException e) {
            log.debug("Failed to load endpoint metrics history from Redis; falling back to memory history.", e);
            return List.of();
        }
    }

    private synchronized List<EndpointMetricsHistorySample> loadMemoryHistorySamples() {
        return List.copyOf(history);
    }

    private static int severityRank(String level) {
        return switch (level) {
            case "critical" -> 3;
            case "warning" -> 2;
            default -> 1;
        };
    }

    private String tagValue(Meter.Id id, String key) {
        String value = id.getTag(key);
        return value == null ? "" : value;
    }

    private static boolean isErrorOutcome(String outcome) {
        return "exception".equals(outcome)
                || "server_error".equals(outcome)
                || "client_error".equals(outcome)
                || "rate_limited".equals(outcome);
    }

    private static String levelFor(String outcome, long avgMs) {
        if ("exception".equals(outcome) || "server_error".equals(outcome) || avgMs >= CRITICAL_AVG_MS) {
            return "critical";
        }
        if (isErrorOutcome(outcome) || "unauthorized".equals(outcome) || avgMs >= WARNING_AVG_MS) {
            return "warning";
        }
        return "healthy";
    }

    private static String insightFor(String outcome, long avgMs) {
        if ("exception".equals(outcome)) {
            return "Endpoint raised exceptions. Check server logs first.";
        }
        if ("server_error".equals(outcome)) {
            return "Endpoint returned server errors. Check dependencies and business exceptions.";
        }
        if (avgMs >= CRITICAL_AVG_MS) {
            return "Average latency is above 5s. Check slow queries, external APIs, or model calls.";
        }
        if (avgMs >= WARNING_AVG_MS) {
            return "Average latency is above 1.5s. Consider caching or async processing.";
        }
        if ("rate_limited".equals(outcome)) {
            return "Endpoint hit rate limiting. Confirm whether this usage pattern is expected.";
        }
        if ("client_error".equals(outcome) || "unauthorized".equals(outcome)) {
            return "Client or auth failures exist. Cross-check frontend telemetry.";
        }
        return "Endpoint looks healthy. Keep watching the trend.";
    }

    private static final class MutableEndpointMetric {
        private final String client;
        private final String endpoint;
        private final String outcome;
        private double totalCount;
        private long timerCount;
        private double totalDurationMs;

        private MutableEndpointMetric(String client, String endpoint, String outcome) {
            this.client = client;
            this.endpoint = endpoint;
            this.outcome = outcome;
        }

        private EndpointMetricRow toRow() {
            double total = totalCount > 0 ? totalCount : timerCount;
            long roundedTotal = Math.round(total);
            long avgMs = timerCount > 0 ? Math.round(totalDurationMs / timerCount) : 0;
            String level = levelFor(outcome, avgMs);
            return new EndpointMetricRow(
                    client + "|" + endpoint + "|" + outcome,
                    client,
                    endpoint,
                    outcome,
                    roundedTotal,
                    avgMs,
                    level,
                    insightFor(outcome, avgMs)
            );
        }
    }

    public record EndpointMetricsSnapshot(String updatedAt, int rowCount, EndpointMetricsSummary summary,
                                          List<EndpointMetricRow> rows) {
        public EndpointMetricsSnapshot {
            rows = List.copyOf(new ArrayList<>(rows));
        }
    }

    public record EndpointMetricsSummary(String status, long totalRequests, int endpointCount, int rowCount,
                                         long slowRows, long errorRows, long criticalRows,
                                         List<String> recommendations) {
        public EndpointMetricsSummary {
            recommendations = List.copyOf(new ArrayList<>(recommendations));
        }
    }

    public record EndpointMetricRow(String key, String client, String endpoint, String outcome, long total, long avgMs,
                                    String level, String insight) {
    }

    public record EndpointMetricsHistory(String updatedAt, int sampleCount, int capacity,
                                         EndpointMetricsTrend trend, List<EndpointMetricsHistorySample> samples) {
        public EndpointMetricsHistory {
            samples = List.copyOf(new ArrayList<>(samples));
        }
    }

    public record EndpointMetricsHistorySample(String updatedAt, String status, long totalRequests, long slowRows,
                                               long errorRows, long criticalRows) {
        static EndpointMetricsHistorySample from(EndpointMetricsSnapshot snapshot) {
            EndpointMetricsSummary summary = snapshot.summary();
            return new EndpointMetricsHistorySample(
                    snapshot.updatedAt(),
                    summary.status(),
                    summary.totalRequests(),
                    summary.slowRows(),
                    summary.errorRows(),
                    summary.criticalRows()
            );
        }
    }

    public record EndpointMetricsTrend(long totalRequestDelta, long slowRowDelta, long errorRowDelta,
                                       long criticalRowDelta, String direction) {
        static EndpointMetricsTrend from(EndpointMetricsHistorySample first, EndpointMetricsHistorySample latest) {
            if (first == null || latest == null) {
                return new EndpointMetricsTrend(0, 0, 0, 0, "flat");
            }

            long slowRowDelta = latest.slowRows() - first.slowRows();
            long errorRowDelta = latest.errorRows() - first.errorRows();
            long criticalRowDelta = latest.criticalRows() - first.criticalRows();
            String direction = criticalRowDelta > 0 || errorRowDelta > 0 || slowRowDelta > 0
                    ? "worse"
                    : criticalRowDelta < 0 || errorRowDelta < 0 || slowRowDelta < 0 ? "better" : "flat";

            return new EndpointMetricsTrend(
                    latest.totalRequests() - first.totalRequests(),
                    slowRowDelta,
                    errorRowDelta,
                    criticalRowDelta,
                    direction
            );
        }
    }
}
