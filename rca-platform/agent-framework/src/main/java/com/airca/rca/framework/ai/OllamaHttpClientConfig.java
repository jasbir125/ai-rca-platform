package com.airca.rca.framework.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring AI's Ollama autoconfiguration builds its HTTP client from whatever
 * {@code RestClient.Builder} bean is available (falling back to
 * {@code RestClient.builder()} otherwise), which in turn defaults to a read timeout far
 * too short for a local model doing multi-minute reasoning generations (observed: the
 * default times out mid-response on this platform's evidence-heavy RCA prompt). This
 * bean gives every RestClient in the app — including Spring AI's — a generous timeout
 * instead. Local-model latency, not network failure, is the normal case here.
 *
 * <p>Raised from 10 to 30 minutes after live testing on a resource-contended host showed
 * the final RCA reasoning call (evidence-heavy prompt, 8192-token context) can genuinely
 * take ~19 minutes to complete server-side when the host is under memory/CPU pressure —
 * the 10-minute timeout was killing the client connection before Ollama finished, causing
 * every retry to repeat the same slow generation from scratch instead of ever succeeding.
 */
@Configuration
public class OllamaHttpClientConfig {

    private static final Duration LLM_CALL_TIMEOUT = Duration.ofMinutes(30);

    @Bean
    public RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(LLM_CALL_TIMEOUT);
        return RestClient.builder().requestFactory(factory);
    }
}
