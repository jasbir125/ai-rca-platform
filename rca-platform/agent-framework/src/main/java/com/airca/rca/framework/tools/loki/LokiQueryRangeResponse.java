package com.airca.rca.framework.tools.loki;

import java.util.List;
import java.util.Map;

/** Shape of Loki's {@code /loki/api/v1/query_range} response. */
public record LokiQueryRangeResponse(String status, Data data) {

    public record Data(String resultType, List<Stream> result) {
    }

    public record Stream(Map<String, String> stream, List<List<String>> values) {
    }
}
