package com.airca.rca.framework.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    private final PromptLoader promptLoader = new PromptLoader();

    @Test
    void loadsTheRealSystemPrompt() {
        String prompt = promptLoader.load("system-prompt");

        assertThat(prompt).contains("Root Cause Analysis");
        assertThat(prompt).contains("Evidence unavailable");
        assertThat(prompt).contains("untrusted data");
    }

    @Test
    void substitutesVariables() {
        String prompt = promptLoader.load("test-fixture", Map.of("service", "order-service", "count", "3"));

        assertThat(prompt).isEqualTo("Investigating order-service with 3 errors.");
    }

    @Test
    void throwsAClearErrorWhenPromptIsMissing() {
        assertThatThrownBy(() -> promptLoader.load("does-not-exist"))
                .hasMessageContaining("does-not-exist");
    }
}
