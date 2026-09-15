package com.airca.rca.framework.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCallBudgetTest {

    @Test
    void allowsCallsUpToTheLimit() {
        ToolCallBudget budget = new ToolCallBudget(2);

        budget.consume("searchLogs");
        budget.consume("queryMetrics");

        assertThat(budget.used()).isEqualTo(2);
        assertThat(budget.remaining()).isZero();
    }

    @Test
    void throwsOnceLimitExceeded() {
        ToolCallBudget budget = new ToolCallBudget(1);
        budget.consume("searchLogs");

        assertThatThrownBy(() -> budget.consume("searchLogs"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("budget exceeded");
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> new ToolCallBudget(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
