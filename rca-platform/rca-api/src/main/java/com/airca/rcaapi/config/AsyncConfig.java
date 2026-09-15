package com.airca.rcaapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Investigations run async so POST .../investigate returns immediately (spec section
 * 44). A bounded pool (rather than Spring's default unbounded-thread-per-task executor)
 * caps how many investigations can run concurrently — a form of the "maximum
 * investigation time"/cost-control guardrail from spec section 46.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "investigationExecutor")
    public Executor investigationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("investigation-");
        executor.initialize();
        return executor;
    }
}
