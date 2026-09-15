package com.airca.rca.framework.tools.prometheus;

import java.util.List;
import java.util.Map;

/** Shape of Prometheus's {@code /api/v1/query} response (instant query). */
public record PrometheusQueryResponse(String status, Data data) {

    public record Data(String resultType, List<Result> result) {
    }

    public record Result(Map<String, String> metric, List<Object> value) {
    }
}
