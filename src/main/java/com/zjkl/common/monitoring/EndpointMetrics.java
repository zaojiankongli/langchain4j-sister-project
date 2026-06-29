package com.zjkl.common.monitoring;

import com.zjkl.common.Result;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Service
public class EndpointMetrics {

    private final MeterRegistry meterRegistry;

    public EndpointMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> Result<T> recordResult(String client, String endpoint, Supplier<Result<T>> supplier) {
        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            Result<T> result = supplier.get();
            outcome = outcomeFromCode(result.getCode());
            return result;
        } catch (RuntimeException e) {
            outcome = "exception";
            throw e;
        } finally {
            record(client, endpoint, outcome, System.nanoTime() - startedAt);
        }
    }

    public <T> Result<T> recordCheckedResult(String client, String endpoint, CheckedSupplier<Result<T>> supplier) throws Exception {
        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            Result<T> result = supplier.get();
            outcome = outcomeFromCode(result.getCode());
            return result;
        } catch (Exception e) {
            outcome = "exception";
            throw e;
        } finally {
            record(client, endpoint, outcome, System.nanoTime() - startedAt);
        }
    }

    public <T> CompletableFuture<Result<T>> recordFutureResult(String client, String endpoint, Supplier<CompletableFuture<Result<T>>> supplier) {
        long startedAt = System.nanoTime();
        try {
            return supplier.get().whenComplete((result, throwable) -> {
                String outcome = throwable == null ? outcomeFromCode(result != null ? result.getCode() : null) : "exception";
                record(client, endpoint, outcome, System.nanoTime() - startedAt);
            });
        } catch (RuntimeException e) {
            record(client, endpoint, "exception", System.nanoTime() - startedAt);
            throw e;
        }
    }

    public <T> DeferredResult<Result<T>> recordDeferredResult(String client, String endpoint, Supplier<DeferredResult<Result<T>>> supplier) {
        long startedAt = System.nanoTime();
        try {
            DeferredResult<Result<T>> deferredResult = supplier.get();
            deferredResult.onCompletion(() -> {
                Object result = deferredResult.getResult();
                String outcome = result instanceof Result<?> typedResult ? outcomeFromCode(typedResult.getCode()) : "unknown";
                record(client, endpoint, outcome, System.nanoTime() - startedAt);
            });
            return deferredResult;
        } catch (RuntimeException e) {
            record(client, endpoint, "exception", System.nanoTime() - startedAt);
            throw e;
        }
    }

    private void record(String client, String endpoint, String outcome, long durationNanos) {
        Timer.builder("app.endpoint.duration")
                .tag("client", client)
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);

        meterRegistry.counter("app.endpoint.total",
                "client", client,
                "endpoint", endpoint,
                "outcome", outcome
        ).increment();
    }

    private String outcomeFromCode(Integer code) {
        if (code == null) {
            return "unknown";
        }
        if (code >= 200 && code < 300) {
            return "success";
        }
        if (code == 401) {
            return "unauthorized";
        }
        if (code == 429) {
            return "rate_limited";
        }
        if (code >= 400 && code < 500) {
            return "client_error";
        }
        if (code >= 500) {
            return "server_error";
        }
        return "other";
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
